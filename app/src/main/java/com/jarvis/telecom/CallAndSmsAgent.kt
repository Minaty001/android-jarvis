package com.jarvis.telecom

import android.content.Context
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.accessibility.UiAutomationManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class CallSession(
    val number: String,
    val contactName: String,
    val isRinging: Boolean,
    val isAnswered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class SmsItem(
    val id: String,
    val sender: String,
    val contactName: String,
    val body: String,
    val otpCode: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class CallAndSmsAgent(
    context: Context? = null,
    private val contactResolver: ContactResolver = ContactResolver(context?.applicationContext ?: context)
) {
    var llmClient: com.jarvis.ai.LlmClient? = null
    private val context: Context? = context?.applicationContext ?: context

    companion object {
        private const val TAG = "CallAndSmsAgent"

        @Volatile
        var instance: CallAndSmsAgent? = null
            internal set

        fun destroy() {
            instance = null
        }
    }

    init {
        instance = this
    }

    @Volatile
    var activeCallSession: CallSession? = null
        private set

    @Volatile
    var isSpeakerphoneOnSimulation: Boolean = false
        internal set

    @Volatile
    var isMicMutedSimulation: Boolean = false
        internal set

    private val recentSmsList = CopyOnWriteArrayList<SmsItem>()

    fun onIncomingCallRinging(number: String, onAnnounce: ((String) -> Unit)? = null) {
        val cleanNumber = number.trim()
        val contact = if (cleanNumber.isNotBlank()) contactResolver.resolveContact(cleanNumber) else null
        val displayName = contact?.name ?: cleanNumber.ifBlank { "Unknown Caller" }

        val session = CallSession(
            number = cleanNumber,
            contactName = displayName,
            isRinging = true
        )
        activeCallSession = session
        Log.i(TAG, "Incoming call ringing from: $displayName ($cleanNumber)")

        val announcement = "Incoming call from $displayName. Say 'answer', 'reject', or 'send busy SMS'."
        onAnnounce?.invoke(announcement)
    }

    fun onCallAnswered() {
        activeCallSession = activeCallSession?.copy(isAnswered = true, isRinging = false)
        Log.i(TAG, "Call explicitly answered (offhook).")
    }

    fun onMissedCall() {
        val session = activeCallSession ?: return
        Log.i(TAG, "Call missed from ${session.contactName} (${session.number})")

        val now = System.currentTimeMillis()
        val calendarManager = com.jarvis.calendar.CalendarManager(context)
        val activeEvents = calendarManager.getEventsForRange(now, now)

        if (activeEvents.isNotEmpty()) {
            val event = activeEvents.first()
            if (com.jarvis.messaging.AutoReplyPolicyManager.canReplyTo(session.number)) {
                val message = "I am currently in an event: '${event.title}'. Jarvis AI has recorded your missed call and I will get back to you soon."

                context?.let { ctx ->
                    runCatching {
                        val smsManager = ctx.getSystemService(android.telephony.SmsManager::class.java) ?: android.telephony.SmsManager.getDefault()
                        smsManager.sendTextMessage(session.number, null, message, null, null)
                        com.jarvis.messaging.AutoReplyPolicyManager.recordReplySent(session.number, "SMS", message)
                    }
                }
            }
        }
    }

    fun onCallEnded() {
        Log.i(TAG, "Call ended / idle. Active session cleared.")
        activeCallSession = null
    }

    fun onSmsReceived(sender: String, body: String): SmsItem {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()
        val contact = if (cleanSender.isNotBlank()) contactResolver.resolveContact(cleanSender) else null
        val displayName = contact?.name ?: cleanSender.ifBlank { "Unknown Sender" }

        val otp = extractOtp(cleanBody)
        val item = SmsItem(
            id = "sms_${System.currentTimeMillis()}",
            sender = cleanSender,
            contactName = displayName,
            body = cleanBody,
            otpCode = otp
        )

        recentSmsList.add(0, item)
        while (recentSmsList.size > 30) {
            recentSmsList.removeAt(recentSmsList.size - 1)
        }

        // Auto-copy OTP to clipboard if detected
        val currentCtx = context
        if (otp != null && currentCtx != null) {
            runCatching {
                val clipboard = currentCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("JARVIS OTP", otp)
                clipboard?.setPrimaryClip(clip)
                Log.i(TAG, "Auto-copied OTP code ($otp) to clipboard")
            }
        }

        // Evaluate SMS Auto-Reply via AutoReplyPolicyManager
        if (com.jarvis.messaging.AutoReplyPolicyManager.isEnabled()) {
            GlobalScope.launch(Dispatchers.IO) {
                com.jarvis.messaging.AutoReplyPolicyManager.handleIncomingSmsAutoReply(context, cleanSender, cleanBody, llmClient)
            }
        }

        Log.i(TAG, "SMS received from $displayName: \"${cleanBody.take(40)}\" (OTP: $otp)")
        return item
    }

    /**
     * Extracts 4 to 8 digit verification codes and OTPs from text.
     */
    fun extractOtp(body: String): String? {
        val lower = body.lowercase(Locale.ROOT)
        val otpKeywords = listOf("otp", "code", "verification", "passcode", "password", "pin", "verify", "auth", "secret", "cvv", "one time password", "login code")
        if (otpKeywords.none { lower.contains(it) }) {
            return null
        }

        // Pattern 1: Keywords preceding digits e.g. "code is 492810" or "OTP: 1234" or "OTP is 591029"
        val pattern1 = Regex("(?:code|otp|verification|passcode|pin|secret|password)[^0-9]{1,15}([0-9]{4,8})\\b", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(body)
        if (match1 != null) {
            return match1.groupValues[1]
        }

        // Pattern 2: Digits preceding keyword e.g. "492810 is your OTP"
        val pattern2 = Regex("\\b([0-9]{4,8})[^0-9]{1,15}(?:is your|is the|as your|for verification|for login|to verify)", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(body)
        if (match2 != null) {
            return match2.groupValues[1]
        }

        // Pattern 3: Standalone 4 to 8 digit code surrounded by whitespace/punctuation
        val pattern3 = Regex("\\b([0-9]{4,8})\\b")
        val match3 = pattern3.find(body)
        return match3?.groupValues?.get(1)
    }

    @SuppressLint("MissingPermission")
    private fun attemptCallControl(
        ctx: Context,
        telecomAction: (TelecomManager) -> Boolean,
        candidateTexts: List<String>,
        contentDescription: String
    ): Boolean {
        if (ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            if (tm != null && runCatching { telecomAction(tm) }.getOrDefault(false)) {
                return true
            }
        }

        if (UiAutomationManager.isEnabled()) {
            val service = JarvisAccessibilityService.instance
            val node = candidateTexts.firstNotNullOfOrNull { text ->
                service?.findNodeByText(text, exact = false)
            } ?: service?.findNodeByContentDescription(contentDescription)

            if (node != null && service?.clickNode(node) == true) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a wired or Bluetooth headset is currently connected.
     */
    fun isHeadsetConnected(): Boolean {
        val ctx = context ?: return false
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
        @Suppress("DEPRECATION")
        return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
    }

    /**
     * Turns speakerphone on or off during active calls.
     */
    fun setSpeakerphone(enabled: Boolean): Boolean {
        val ctx = context ?: run {
            isSpeakerphoneOnSimulation = enabled
            Log.i(TAG, "Simulated speakerphone set to: $enabled")
            return true
        }
        return runCatching {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                if (enabled) {
                    audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = android.media.AudioManager.MODE_NORMAL
                }
                isSpeakerphoneOnSimulation = enabled
                Log.i(TAG, "Speakerphone set to: $enabled")
                true
            } else false
        }.getOrElse { e ->
            Log.w(TAG, "Failed to toggle speakerphone: ${e.message}")
            false
        }
    }

    /**
     * Toggles microphone mute during active calls.
     */
    fun setMicrophoneMute(muted: Boolean): Boolean {
        val ctx = context ?: run {
            isMicMutedSimulation = muted
            Log.i(TAG, "Simulated microphone mute set to: $muted")
            return true
        }
        return runCatching {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                audioManager.isMicrophoneMute = muted
                isMicMutedSimulation = muted
                Log.i(TAG, "Microphone mute set to: $muted")
                true
            } else false
        }.getOrElse { e ->
            Log.w(TAG, "Failed to toggle mic mute: ${e.message}")
            false
        }
    }

    /**
     * Ends currently ringing or active phone call hands-free.
     */
    fun endActiveCall(): Boolean = rejectCall(null)

    /**
     * Answers incoming ringing call hands-free.
     */
    fun answerCall(): Boolean {
        val ctx = context ?: run {
            Log.i(TAG, "answerCall: Context null, simulating call answer.")
            activeCallSession = activeCallSession?.copy(isRinging = false, isAnswered = true)
            if (!isHeadsetConnected()) {
                setSpeakerphone(true)
            }
            return true
        }

        return runCatching {
            val answered = attemptCallControl(
                ctx = ctx,
                telecomAction = { tm ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                    ) {
                        @Suppress("DEPRECATION")
                        tm.acceptRingingCall()
                        true
                    } else false
                },
                candidateTexts = listOf("Answer", "Accept"),
                contentDescription = "Answer"
            )
            if (answered) {
                activeCallSession = activeCallSession?.copy(isRinging = false, isAnswered = true)
                if (!isHeadsetConnected()) {
                    setSpeakerphone(true)
                }
                Log.i(TAG, "Answered call successfully")
            }
            answered
        }.getOrElse { e ->
            Log.w(TAG, "Failed to answer call: ${e.message}")
            false
        }
    }

    /**
     * Rejects ringing or active call hands-free and optionally sends auto-reply SMS.
     */
    fun rejectCall(autoReplySms: String? = null): Boolean {
        val targetNumber = activeCallSession?.number
        val targetName = activeCallSession?.contactName ?: "Caller"
        val ctx = context ?: run {
            Log.i(TAG, "rejectCall: Context null, simulating call reject.")
            activeCallSession = null
            return true
        }

        val rejected = runCatching {
            attemptCallControl(
                ctx = ctx,
                telecomAction = { tm ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                    ) {
                        @Suppress("DEPRECATION")
                        tm.endCall()
                    } else false
                },
                candidateTexts = listOf("Decline", "Reject", "Dismiss"),
                contentDescription = "Decline"
            )
            true
        }.getOrDefault(false)

        activeCallSession = null

        // Send auto-reply SMS if requested and target number is available
        if (!autoReplySms.isNullOrBlank() && !targetNumber.isNullOrBlank()) {
            runCatching {
                val smsManager = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                smsManager.sendTextMessage(targetNumber, null, autoReplySms, null, null)
                Log.i(TAG, "Auto-reply SMS sent to $targetName ($targetNumber): \"$autoReplySms\"")
            }.onFailure { e ->
                Log.w(TAG, "Failed sending auto-reply SMS: ${e.message}")
            }
        }

        return rejected
    }

    fun readRecentSms(limit: Int = 5, senderFilter: String? = null, otpOnly: Boolean = false): List<SmsItem> {
        var items = readInboxSms() ?: recentSmsList.toList()
        if (!senderFilter.isNullOrBlank()) {
            val lowerFilter = senderFilter.lowercase(Locale.ROOT).trim()
            items = items.filter {
                it.sender.lowercase(Locale.ROOT).contains(lowerFilter) ||
                        it.contactName.lowercase(Locale.ROOT).contains(lowerFilter)
            }
        }
        if (otpOnly) {
            items = items.filter { !it.otpCode.isNullOrBlank() }
        }
        return items.take(limit)
    }

    fun hasReadSmsPermission(): Boolean = context?.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun readInboxSms(): List<SmsItem>? {
        val ctx = context ?: return null
        if (!hasReadSmsPermission()) return null
        return runCatching {
            val projection = arrayOf("_id", "address", "body", "date")
            ctx.contentResolver.query(
                android.net.Uri.parse("content://sms/inbox"), projection, null, null, "date DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex("_id")
                val addressCol = cursor.getColumnIndex("address")
                val bodyCol = cursor.getColumnIndex("body")
                val dateCol = cursor.getColumnIndex("date")
                buildList {
                    while (cursor.moveToNext() && size < 100) {
                        val sender = if (addressCol >= 0) cursor.getString(addressCol).orEmpty() else ""
                        val body = if (bodyCol >= 0) cursor.getString(bodyCol).orEmpty() else ""
                        val contact = sender.takeIf { it.isNotBlank() }?.let(contactResolver::resolveContact)?.name ?: sender
                        add(SmsItem(
                            id = if (idCol >= 0) "sms_${cursor.getString(idCol)}" else "sms_${size}",
                            sender = sender,
                            contactName = contact,
                            body = body,
                            otpCode = extractOtp(body),
                            timestamp = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        ))
                    }
                }
            } ?: emptyList()
        }.getOrNull()
    }

    fun formatSmsSummary(limit: Int = 5, senderFilter: String? = null, otpOnly: Boolean = false): String {
        val messages = readRecentSms(limit, senderFilter, otpOnly)
        if (messages.isEmpty()) {
            return if (otpOnly) "No recent OTP or verification codes found." else "You have no new SMS messages."
        }

        return buildString {
            if (otpOnly) {
                append("Found ${messages.size} verification code${if (messages.size > 1) "s" else ""}: ")
                messages.forEachIndexed { idx, item ->
                    append("${idx + 1}. Code is ${item.otpCode} from ${item.contactName}. ")
                }
            } else {
                append("You have ${messages.size} recent SMS message${if (messages.size > 1) "s" else ""}: ")
                messages.forEachIndexed { idx, item ->
                    val otpPart = if (!item.otpCode.isNullOrBlank()) " (OTP Code: ${item.otpCode})" else ""
                    append("${idx + 1}. From ${item.contactName}: \"${item.body}\"$otpPart. ")
                }
            }
        }
    }
}
