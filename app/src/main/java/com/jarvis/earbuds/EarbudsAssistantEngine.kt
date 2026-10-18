package com.jarvis.earbuds

import android.content.Context
import android.util.Log
import com.jarvis.bluetooth.BluetoothHeadsetManager
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import com.jarvis.messaging.UniversalMessagingEngine
import com.jarvis.telecom.CallAndSmsAgent
import com.jarvis.telecom.ContactResolver
import com.jarvis.ui.data.UiPreferencesStore
import com.jarvis.voice.AndroidSpeechRecognizerEngine
import com.jarvis.voice.TtsEngine
import com.jarvis.voice.VoiceEngine
import com.jarvis.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Smart Bluetooth Earbuds Assistant & Hands-Free Screen Call Control:
 * Intercepts incoming messaging events (WhatsApp / SMS) and phone calls,
 * announcing them privately in the user's earbuds (or speaker) with immediate
 * hands-free voice dictation and call control.
 */
class EarbudsAssistantEngine(
    private val context: Context,
    private val bluetoothHeadsetManager: BluetoothHeadsetManager,
    private val voiceEngine: VoiceEngine? = null,
    private val ttsEngine: TtsEngine? = voiceEngine?.ttsEngine,
    private val asrEngine: AndroidSpeechRecognizerEngine? = voiceEngine?.asrEngine
) {
    private val TAG = "EarbudsAssistantEngine"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var eventCollectorJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val isProcessingNotification = AtomicBoolean(false)
    private val isProcessingCall = AtomicBoolean(false)

    var isEnabled: Boolean = true

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Log.i(TAG, "Starting EarbudsAssistantEngine...")

        eventCollectorJob = scope.launch {
            JarvisEventBus.shared.events.collect { event ->
                if (!isEnabled) return@collect

                when (event) {
                    is JarvisEvent.WhatsAppNotification -> {
                        handleIncomingWhatsApp(event)
                    }
                    is JarvisEvent.SmsReceived -> {
                        handleIncomingSms(event)
                    }
                    is JarvisEvent.IncomingCall -> {
                        handleIncomingCall(event)
                    }
                    else -> {}
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        eventCollectorJob?.cancel()
        eventCollectorJob = null
        scope.cancel()
        Log.i(TAG, "EarbudsAssistantEngine stopped")
    }

    private suspend fun handleIncomingWhatsApp(event: JarvisEvent.WhatsAppNotification) {
        val settings = runCatching { UiPreferencesStore(context).loadSettings() }.getOrNull()
        if (settings?.notificationsEnabled == false) {
            Log.d(TAG, "Notification readout is disabled in settings")
            return
        }

        if (!isProcessingNotification.compareAndSet(false, true)) return
        try {
            val sender = event.sender.ifBlank { "Someone" }
            val announcement = "New message on ${event.platform} from $sender: ${event.message.take(120)}. Say reply or skip."
            Log.i(TAG, "Announcing incoming message: $announcement")
            ttsEngine?.speak(announcement)
            delay(250)

            if (asrEngine != null) {
                promptForMessageReply(sender, event.platform)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error announcing WhatsApp message: ${e.message}")
        } finally {
            isProcessingNotification.set(false)
        }
    }

    private suspend fun handleIncomingSms(event: JarvisEvent.SmsReceived) {
        val settings = runCatching { UiPreferencesStore(context).loadSettings() }.getOrNull()
        if (settings?.notificationsEnabled == false) {
            Log.d(TAG, "Notification readout is disabled in settings")
            return
        }

        if (!isProcessingNotification.compareAndSet(false, true)) return
        try {
            val sender = event.sender.ifBlank { "Unknown" }
            val contactName = runCatching { ContactResolver(context).resolveContact(sender)?.name }.getOrNull() ?: sender
            val announcement = if (event.detectedOtp != null) {
                "New SMS from $contactName. One time passcode detected and copied to clipboard."
            } else {
                "New SMS from $contactName: ${event.body.take(120)}. Say reply or skip."
            }
            Log.i(TAG, "Announcing incoming SMS: $announcement")
            ttsEngine?.speak(announcement)
            delay(250)

            if (event.detectedOtp == null && asrEngine != null) {
                promptForMessageReply(sender, "SMS")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error announcing SMS: ${e.message}")
        } finally {
            isProcessingNotification.set(false)
        }
    }

    private suspend fun promptForMessageReply(recipient: String, platform: String) {
        var replyTranscript: String? = null

        asrEngine?.startListening(
            onSpeechStarted = { Log.d(TAG, "Speech detected during reply prompt") },
            onPartial = {},
            onFinal = { text ->
                if (text.isNotBlank()) replyTranscript = text.trim()
            },
            onError = { _, _ -> },
            onEmpty = {}
        )

        var waited = 0L
        while (waited < 5000L && replyTranscript == null) {
            delay(200)
            waited += 200
        }

        runCatching { asrEngine?.stopListening() }

        val response = replyTranscript?.trim()
        if (!response.isNullOrBlank()) {
            val lower = response.lowercase()
            if (lower in listOf("skip", "cancel", "dismiss", "no", "nah", "nahi", "ignore")) {
                Log.i(TAG, "Message reply prompt dismissed by user")
                return
            }

            var replyText: String = response
            val replyPrefixes = listOf("reply with", "reply that", "reply", "send", "say that", "say")
            for (prefix in replyPrefixes) {
                if (lower.startsWith(prefix)) {
                    replyText = response.substring(prefix.length).trim()
                    break
                }
            }

            if (replyText.isBlank()) {
                ttsEngine?.speak("What would you like to reply?")
                delay(200)
                var followUpTranscript: String? = null
                asrEngine?.startListening(
                    onSpeechStarted = {},
                    onPartial = {},
                    onFinal = { text -> if (text.isNotBlank()) followUpTranscript = text.trim() },
                    onError = { _, _ -> },
                    onEmpty = {}
                )
                waited = 0L
                while (waited < 6000L && followUpTranscript == null) {
                    delay(200)
                    waited += 200
                }
                runCatching { asrEngine?.stopListening() }
                replyText = followUpTranscript?.trim().orEmpty()
            }

            if (replyText.isNotBlank()) {
                Log.i(TAG, "Dispatching hands-free reply to $recipient on $platform: '$replyText'")
                if (platform.contains("WhatsApp", ignoreCase = true)) {
                    val directSuccess = UniversalMessagingEngine.instance?.sendDirectNotificationReply(recipient, replyText) == true
                    if (!directSuccess) {
                        UniversalMessagingEngine.instance?.sendDirectMessage(recipient, replyText, platform)
                    }
                } else {
                    runCatching {
                        val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                            ?: @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
                        smsManager.sendTextMessage(recipient, null, replyText, null, null)
                    }
                }
                ttsEngine?.speak("Reply sent.")
            }
        }
    }

    private suspend fun handleIncomingCall(event: JarvisEvent.IncomingCall) {
        val settings = runCatching { UiPreferencesStore(context).loadSettings() }.getOrNull()
        if (settings?.callScreeningEnabled == false) {
            Log.d(TAG, "Call screening is disabled in settings")
            return
        }

        val isHeadset = bluetoothHeadsetManager.isHeadsetConnected
        // If neither headset nor speaker screening is active, return
        if (!isHeadset && settings == null) {
            Log.d(TAG, "No Bluetooth headset connected for incoming call announcement")
            return
        }

        if (!isProcessingCall.compareAndSet(false, true)) return

        try {
            val number = event.phoneNumber.trim()
            val resolvedName = event.callerName?.ifBlank { null }
                ?: runCatching { ContactResolver(context).resolveContact(number)?.name }.getOrNull()
                ?: number.ifBlank { "Unknown Caller" }

            val announcement = "Incoming call from $resolvedName. Say answer or reject."
            Log.i(TAG, "Screening call: $announcement (headset=$isHeadset)")
            ttsEngine?.speak(announcement)
            delay(200)

            if (asrEngine != null) {
                promptForCallAction(number, resolvedName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error screening incoming call: ${e.message}")
        } finally {
            isProcessingCall.set(false)
        }
    }

    private suspend fun promptForCallAction(number: String, callerName: String) {
        var responseTranscript: String? = null

        asrEngine?.startListening(
            onSpeechStarted = {
                Log.d(TAG, "Speech detected during call screening prompt")
            },
            onPartial = {},
            onFinal = { text ->
                if (text.isNotBlank()) {
                    responseTranscript = text.trim()
                }
            },
            onError = { _, _ -> },
            onEmpty = {}
        )

        // Listen for up to 6 seconds or until call stops ringing
        var waited = 0L
        while (waited < 6000L && responseTranscript == null && CallAndSmsAgent.instance?.activeCallSession?.isRinging != false) {
            delay(200)
            waited += 200
        }

        runCatching { asrEngine?.stopListening() }

        val response = responseTranscript
        if (!response.isNullOrBlank()) {
            val lower = response.lowercase().trim()
            val agent = CallAndSmsAgent.instance ?: CallAndSmsAgent(context)

            when {
                isRejectWithSms(lower) -> {
                    val smsMsg = extractCustomSmsMessage(lower)
                    Log.i(TAG, "User commanded reject call with SMS: '$smsMsg'")
                    agent.rejectCall(autoReplySms = smsMsg)
                    ttsEngine?.speak("Call rejected and SMS sent.")
                }
                isAnswerResponse(lower) -> {
                    Log.i(TAG, "User commanded answer call")
                    val answered = agent.answerCall()
                    if (answered) {
                        ttsEngine?.speak("Call connected.")
                    } else {
                        ttsEngine?.speak("Could not answer call.")
                    }
                }
                isRejectResponse(lower) -> {
                    Log.i(TAG, "User commanded reject call")
                    agent.rejectCall(null)
                    ttsEngine?.speak("Call rejected.")
                }
                lower.contains("who") || lower.contains("kaun") || lower.contains("caller") -> {
                    ttsEngine?.speak("Call is from $callerName.")
                    // Listen again for second chance
                    promptForCallAction(number, callerName)
                }
                else -> {
                    Log.d(TAG, "Unrecognized call response: '$response'")
                }
            }
        }
    }

    fun isAnswerResponse(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = listOf("answer", "pick up", "pickup", "accept", "receive", "uthao", "haan uthao", "haan", "connect")
        return words.any { lower.contains(it) }
    }

    fun isRejectResponse(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = listOf("reject", "decline", "cut", "kat do", "hang up", "dismiss", "no", "nahi")
        return words.any { lower.contains(it) }
    }

    fun isRejectWithSms(text: String): Boolean {
        val lower = text.lowercase().trim()
        return (lower.contains("busy") || lower.contains("message") || lower.contains("sms") ||
                lower.contains("text") || lower.contains("meeting") || lower.contains("driving") || lower.contains("later")) &&
                (lower.contains("send") || lower.contains("say") || lower.contains("reject") || lower.contains("tell") || lower.contains("bol"))
    }

    fun extractCustomSmsMessage(text: String): String {
        val lower = text.lowercase().trim()
        return when {
            lower.contains("driving") -> "I am driving right now, will call you later."
            lower.contains("meeting") -> "I am in a meeting, will get back to you soon."
            lower.contains("call later") || lower.contains("call you later") -> "Can't talk right now. Will call you later."
            else -> "I am currently busy, will call you back later."
        }
    }

}
