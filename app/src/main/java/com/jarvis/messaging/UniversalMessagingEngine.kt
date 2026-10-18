package com.jarvis.messaging

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.accessibility.UiAutomationManager
import com.jarvis.notification.NotificationItem
import com.jarvis.notification.NotificationStore
import com.jarvis.telecom.ContactResolver
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class AutoReplyMode {
    OFF,
    DRIVING,
    MEETING,
    BUSY,
    CUSTOM
, AI_SMART_REPLY
}

data class DirectReplyAction(
    val id: String,
    val sender: String,
    val action: Notification.Action?,
    val timestamp: Long = System.currentTimeMillis()
)

data class AutoReplyRecord(
    val sender: String,
    val messageSent: String,
    val mode: AutoReplyMode,
    val timestamp: Long = System.currentTimeMillis()
)

class UniversalMessagingEngine(
    context: Context? = null,
    var llmClient: com.jarvis.ai.LlmClient? = null,
    private val contactResolver: ContactResolver = ContactResolver(context?.applicationContext ?: context)
) {
    private val context: Context? = context?.applicationContext ?: context

    // Auto-reply configuration
    var autoReplyMode: AutoReplyMode
        get() = AutoReplyPolicyManager.mode
        set(value) = AutoReplyPolicyManager.setMode(value, customAutoReplyTemplate)
    var customAutoReplyTemplate: String?
        get() = AutoReplyPolicyManager.customTemplate
        set(value) = AutoReplyPolicyManager.setMode(autoReplyMode, value)

    /**
     * Set the auto-reply mode and optional custom template.
     */
    fun setAutoReplyMode(mode: AutoReplyMode, customTemplate: String? = null) {
        AutoReplyPolicyManager.setMode(mode, customTemplate)
        Log.i(TAG, "Auto-reply mode set to $mode with custom template: ${customTemplate != null}")
    }

    /**
     * Returns true if an auto-reply mode other than OFF is active.
     */
    fun isAutoReplyActive(): Boolean = AutoReplyPolicyManager.isEnabled()

    /**
     * Reads recent WhatsApp notifications and returns a ToolResult.
     */
    fun readRecentWhatsApp(limit: Int = 10): ToolResult {
        val notifs = com.jarvis.notification.NotificationStore.getRecentNotifications(limit, appFilter = PKG_WHATSAPP)
        return if (notifs.isEmpty()) {
            ToolResult(true, "You have no unread WhatsApp messages.")
        } else {
            val summary = buildString {
                append("You have ${notifs.size} recent WhatsApp message${if (notifs.size > 1) "s" else ""}. ")
                val grouped = notifs.groupBy { it.title }
                grouped.entries.take(3).forEach { (sender, msgs) ->
                    val latest = msgs.first().text
                    append("$sender: \"$latest\"; ")
                }
            }.trim()
            ToolResult(true, summary)
        }
    }

    /**
     * Reads recent WhatsApp notifications with an optional sender filter.
     */
    fun readRecentWhatsApp(limit: Int = 10, senderFilter: String? = null): ToolResult {
        // Reuse existing readRecentMessages which already supports sender filtering.
        val result = readRecentMessages(limit = limit, senderFilter = senderFilter, platformFilter = null)
        return result
    }

    companion object {
        private const val TAG = "UniversalMessagingEngine"
        private const val AUTO_REPLY_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes
        private const val PKG_WHATSAPP = "com.whatsapp"
        private const val PKG_WHATSAPP_BIZ = "com.whatsapp.w4b"

        fun resolveTargetPackage(platform: String?): String {
            return when (platform?.lowercase()?.trim()) {
                "business", "whatsapp business", "wa business", "w4b" -> PKG_WHATSAPP_BIZ
                "telegram", "tg" -> "org.telegram.messenger"
                "signal" -> "org.thoughtcrime.securesms"
                "instagram", "ig" -> "com.instagram.android"
                else -> PKG_WHATSAPP
            }
        }

        fun buildPlatformUri(pkg: String, contact: String?, message: String?): Uri {
            return when (pkg) {
                "org.telegram.messenger" -> Uri.parse("tg://resolve?domain=${contact ?: ""}")
                "com.instagram.android" -> Uri.parse("instagram://user?username=${contact ?: ""}")
                else -> {
                    if (message.isNullOrEmpty()) {
                        Uri.parse("https://wa.me/${contact ?: ""}")
                    } else {
                        Uri.parse("https://wa.me/${contact ?: ""}?text=${Uri.encode(message)}")
                    }
                }
            }
        }

        @Volatile
        var instance: UniversalMessagingEngine? = null
            internal set

        fun destroy() {
            instance = null
        }
    }

    init {
        instance = this
    }

    // Direct reply actions cached from active notifications: key is normalized sender name or notif id
    private val replyActions = ConcurrentHashMap<String, DirectReplyAction>()

    // Auto-reply configuration

    // Cooldown tracker: normalized sender -> last auto-replied timestamp
    private val lastAutoRepliedMap = ConcurrentHashMap<String, Long>()
    private val autoReplyHistory = mutableListOf<AutoReplyRecord>()

    // Overload for registering a reply action using only an ID and reply text.
    // The reply text is not used for direct notification replies, so we delegate to the existing method
    // with a blank sender and null action, which effectively registers a placeholder.
    fun registerReplyAction(id: String, replyText: String) {
        registerReplyAction(id, sender = "", action = null)
    }
    fun registerReplyAction(id: String, sender: String, action: Notification.Action?) {
        val normalized = sender.trim().lowercase(Locale.ROOT)
        val entry = DirectReplyAction(id, sender.trim(), action)
        replyActions[normalized] = entry
        replyActions[id] = entry
        Log.d(TAG, "Registered direct reply action for '$sender' (id: $id)")
    }

    fun removeReplyAction(id: String) {
        val entry = replyActions.remove(id)
        if (entry != null) {
            val normalized = entry.sender.lowercase(Locale.ROOT)
            replyActions.remove(normalized)
        }
    }



    fun getAutoReplyHistory(): List<AutoReplyRecord> = synchronized(autoReplyHistory) {
        autoReplyHistory.toList()
    }

    /**
     * Tier 1: Instant background reply via Android RemoteInput API.
     * Completes without launching WhatsApp or disturbing the active UI.
     */
/**
 * Sends a direct notification reply using RemoteInput.
 * Returns true if the reply was successfully dispatched.
 */
fun sendDirectNotificationReply(recipient: String, replyText: String): Boolean {
    if (replyText.isBlank() || context == null) return false
    val normalized = recipient.trim().lowercase(Locale.ROOT)

    // Find candidate action by exact match or substring
    val candidate = replyActions[normalized]
        ?: replyActions.values.firstOrNull {
            it.sender.lowercase(Locale.ROOT).contains(normalized) ||
            normalized.contains(it.sender.lowercase(Locale.ROOT))
        } ?: return false

    val action = candidate.action ?: return false

    return runCatching {
        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            Log.w(TAG, "No RemoteInputs found on action for ${candidate.sender}")
            return false
        }

        val fillInIntent = Intent()
        val bundle = Bundle()
        for (input in remoteInputs) {
            bundle.putCharSequence(input.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, bundle)

        action.actionIntent.send(context, 0, fillInIntent)
        Log.i(TAG, "Successfully dispatched background RemoteInput reply to ${candidate.sender}: $replyText")
        true
    }.getOrElse { e ->
        Log.w(TAG, "Failed sending RemoteInput reply to ${candidate.sender}: ${e.message}")
        false
    }
}

/**
 * Synchronous wrapper for sendDirectNotificationReply.
 * Useful for callers that require a blocking call.
 */
fun sendDirectNotificationReplySync(recipient: String, replyText: String): Boolean {
    return runBlocking { sendDirectNotificationReply(recipient, replyText) }
}

    /**
     * Direct WhatsApp Messaging Engine via api.whatsapp.com / whatsapp:// URI scheme.
     * Direct dispatch without requiring prior chat thread history or manual contact selection.
     */
    suspend fun sendDirectMessage(recipient: String, message: String, platform: String? = null): ToolResult {
        val trimmedRecipient = recipient.trim()
        val trimmedMessage = message.trim()

        if (trimmedRecipient.isBlank() || trimmedMessage.isBlank()) {
            return ToolResult(false, "Recipient and message are required for direct WhatsApp messaging.")
        }

        // Resolve recipient to phone number if not already digits/phone format
        val isPhoneNumber = trimmedRecipient.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        var resolvedNumber: String? = if (isPhoneNumber) {
            trimmedRecipient.filter { it.isDigit() || it == '+' }
        } else null

        if (resolvedNumber == null) {
            val contact = contactResolver.resolveContact(trimmedRecipient)
            if (contact != null && contact.phoneNumber.isNotBlank()) {
                resolvedNumber = contact.phoneNumber.filter { it.isDigit() || it == '+' }
                Log.i(TAG, "Direct Message: Resolved '$trimmedRecipient' to phone number: $resolvedNumber (${contact.name})")
            }
        }

        if (resolvedNumber.isNullOrBlank()) {
            return ToolResult(false, "Could not resolve a valid phone number for contact: '$trimmedRecipient'.")
        }

        val cleanDigits = formatWhatsAppNumber(resolvedNumber)
        if (cleanDigits.length < 7) {
            return ToolResult(false, "Invalid phone number digits for direct messaging: '$resolvedNumber'.")
        }

        val targetPkg = resolveTargetPackage(platform)
        val platformLabel = if (targetPkg == PKG_WHATSAPP_BIZ) "WhatsApp Business" else "WhatsApp"

        val ctx = context ?: return ToolResult(
            true,
            "Direct $platformLabel message queued for $trimmedRecipient ($cleanDigits): \"$trimmedMessage\" (Simulation mode)."
        )

        ensureScreenAwake()
        val directUri = buildPlatformUri(targetPkg, cleanDigits, trimmedMessage)
        val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
            setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            ctx.startActivity(intent)

            if (UiAutomationManager.isEnabled()) {
                val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                if (arrived) {
                    if (clickContinueToChatIfShown(targetPkg)) delay(700)
                    if (clickSendButton(targetPkg)) {
                        Log.i(TAG, "Auto-clicked Send button for direct message to $cleanDigits via $platformLabel")
                        autoReturnToHome()
                        return ToolResult(
                            true,
                            "Direct $platformLabel message sent to $trimmedRecipient ($cleanDigits) automatically."
                        )
                    }
                }
            }

            ToolResult(false, "Direct $platformLabel chat opened with the message pre-filled, but Send could not be verified.")
        }.getOrElse { _ ->
            // Fall back to standard Intent if package filter fails
            val fallbackUri = buildPlatformUri(targetPkg, cleanDigits, trimmedMessage)
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                ctx.startActivity(fallbackIntent)
                ToolResult(false, "Fallback chat opened for $trimmedRecipient, but Send could not be verified.")
            }.getOrElse { fallbackErr ->
                ToolResult(false, "Failed to launch direct chat: ${fallbackErr.message}")
            }
        }
    }

    /**
     * Primary 3-tier send mechanism:
     * - Tier 1: Background RemoteInput reply (if active notification exists)
     * - Tier 2: ContactResolver lookup -> wa.me deep link -> Accessibility auto-send
     * - Tier 3: In-app accessibility contact search macro
     */
    suspend fun sendMessage(recipient: String, message: String, platform: String? = null): ToolResult {
        val trimmedRecipient = recipient.trim()
        val trimmedMessage = message.trim()

        if (trimmedRecipient.isBlank() || trimmedMessage.isBlank()) {
            return ToolResult(false, "Recipient and message are required for WhatsApp messaging.")
        }

        val targetPkg = resolveTargetPackage(platform)
        val platformLabel = if (targetPkg == PKG_WHATSAPP_BIZ) "WhatsApp Business" else "WhatsApp"

        // Tier 1: Attempt instant background reply
        val directReplied = sendDirectNotificationReply(trimmedRecipient, trimmedMessage)
        if (directReplied) {
            return ToolResult(
                true,
                "Replied to $trimmedRecipient on $platformLabel instantly via background notification: \"$trimmedMessage\"."
            )
        }

        val ctx = context ?: return ToolResult(
            true,
            "$platformLabel message queued for $trimmedRecipient: \"$trimmedMessage\" (Simulation mode)."
        )

        ensureScreenAwake()

        // Check if recipient is a raw phone number
        val isPhoneNumber = trimmedRecipient.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        var resolvedNumber: String? = if (isPhoneNumber) {
            trimmedRecipient.filter { it.isDigit() || it == '+' }
        } else null

        // Tier 2: Resolve via ContactResolver if not already a phone number
        if (resolvedNumber == null) {
            val contact = contactResolver.resolveContact(trimmedRecipient)
            if (contact != null && contact.phoneNumber.isNotBlank()) {
                resolvedNumber = contact.phoneNumber.filter { it.isDigit() || it == '+' }
                Log.i(TAG, "Resolved '$trimmedRecipient' to phone number: $resolvedNumber (${contact.name})")
            }
        }

        if (resolvedNumber != null) {
            val cleanDigits = formatWhatsAppNumber(resolvedNumber)
            val waUri = buildPlatformUri(targetPkg, cleanDigits, trimmedMessage)
            val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                setPackage(targetPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return runCatching {
                ctx.startActivity(intent)

                // If accessibility automation is enabled, auto-click the Send button!
                if (UiAutomationManager.isEnabled()) {
                val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                if (arrived) {
                    if (clickContinueToChatIfShown(targetPkg)) delay(700)
                    if (clickSendButton(targetPkg)) {
                        Log.i(TAG, "Auto-clicked Send button for $cleanDigits via $platformLabel")
                        autoReturnToHome()
                        return ToolResult(
                            true,
                            "Sent $platformLabel message to $trimmedRecipient ($cleanDigits) automatically."
                        )
                        }
                    }
                }

                ToolResult(false, "$platformLabel opened with the message pre-filled, but Send could not be verified. Accessibility automation must be enabled to send it.")
            }.getOrElse { e ->
                ToolResult(false, "Failed to launch $platformLabel chat: ${e.message}")
            }
        }

        // Tier 3: In-app search macro
        return runCatching {
            val shareUri = buildPlatformUri(targetPkg, null, trimmedMessage)
            val intent = Intent(Intent.ACTION_VIEW, shareUri).apply {
                setPackage(targetPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)

            if (UiAutomationManager.isEnabled()) {
                val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                if (arrived) {
                    delay(700)
                    val service = JarvisAccessibilityService.instance
                    val contactNode = service?.findNodeByText(trimmedRecipient, exact = false)
                    if (contactNode != null) {
                        service.clickNode(contactNode)
                        delay(800)
                        val sendNode = UiAutomationManager.waitForNode(timeoutMs = 2500) { s ->
                            findSendNodeForPackage(s, targetPkg)
                        }
                        if (sendNode != null && service.clickNode(sendNode)) {
                            Log.i(TAG, "Selected '$trimmedRecipient' and sent $platformLabel message automatically")
                            autoReturnToHome()
                            return ToolResult(
                                true,
                                "Selected '$trimmedRecipient' and sent $platformLabel message automatically."
                            )
                        }
                    }
                }
            }

            ToolResult(
                false,
                "$platformLabel opened with message pre-filled, but it was not sent. Please enable Accessibility automation or confirm the contact manually."
            )
        }.getOrElse { e ->
            ToolResult(false, "Failed to open $platformLabel: ${e.message}")
        }
    }

    suspend fun readChatVisually(contactName: String, platform: String? = null): ToolResult {
        val trimmedRecipient = contactName.trim()
        val targetPkg = resolveTargetPackage(platform)
        val platformLabel = if (targetPkg == PKG_WHATSAPP_BIZ) "WhatsApp Business" else "WhatsApp"

        val ctx = context ?: return ToolResult(false, "Context required for UI automation.")
        ensureScreenAwake()

        val isPhoneNumber = trimmedRecipient.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        var resolvedNumber: String? = if (isPhoneNumber) {
            trimmedRecipient.filter { it.isDigit() || it == '+' }
        } else null

        if (resolvedNumber == null) {
            val contact = contactResolver.resolveContact(trimmedRecipient)
            if (contact != null && contact.phoneNumber.isNotBlank()) {
                resolvedNumber = contact.phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }

        return runCatching {
            if (resolvedNumber != null) {
                val cleanDigits = formatWhatsAppNumber(resolvedNumber)
                val waUri = buildPlatformUri(targetPkg, cleanDigits, null)
                val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } else {
                // If it's WhatsApp we can try whatsapp://send, but let's just use buildPlatformUri
                val shareUri = if (targetPkg == PKG_WHATSAPP || targetPkg == PKG_WHATSAPP_BIZ) {
                    Uri.parse("whatsapp://send")
                } else {
                    buildPlatformUri(targetPkg, null, null)
                }
                val intent = Intent(Intent.ACTION_VIEW, shareUri).apply {
                    setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                
                if (UiAutomationManager.isEnabled()) {
                    val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                    if (arrived) {
                        kotlinx.coroutines.delay(700)
                        val service = JarvisAccessibilityService.instance
                        val contactNode = service?.findNodeByText(trimmedRecipient, exact = false)
                        if (contactNode != null) {
                            service.clickNode(contactNode)
                        } else {
                            return ToolResult(false, "Could not find chat with '$trimmedRecipient' on screen.")
                        }
                    }
                }
            }
            
            if (UiAutomationManager.isEnabled()) {
                val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                if (arrived) {
                    kotlinx.coroutines.delay(1200) // Wait for chat to load
                    val nodes = UiAutomationManager.inspectScreen()
                    val noiseWords = setOf("type a message", "message", "send", "online", "typing...", "last seen", "call", "video call")
                    val chatNodes = nodes.filter {
                        it.className == "android.widget.TextView" && 
                        !it.text.isNullOrBlank() && 
                        !noiseWords.contains(it.text!!.lowercase(Locale.ROOT).trim())
                    }
                    var chatText = chatNodes.joinToString("\n") { it.text!! }
                    
                    // Check for images
                    val imageNodes = nodes.filter { it.className == "android.widget.ImageView" || it.className == "android.widget.Image" }
                    val currentLlm = llmClient
                    if (imageNodes.isNotEmpty() && currentLlm != null) {
                        try {
                            val screenshot = com.jarvis.accessibility.ScreenVisionManager.captureScreen().base64Image
                            if (screenshot != null) {
                                val visionResult = currentLlm.chatVision("Describe the images in this chat briefly.", screenshot)
                                if (visionResult.isSuccess) {
                                    chatText += "\n[Image Content: ${visionResult.getOrNull()}]"
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Vision extraction failed", e)
                        }
                    }
                    
                    autoReturnToHome()
                    if (chatText.isBlank()) return ToolResult(false, "Opened chat, but could not extract any text.")
                    return ToolResult(true, "Visually read chat with '$trimmedRecipient':\n$chatText")
                }
            }
            ToolResult(false, "Accessibility automation must be enabled to read chat visually.")
        }.getOrElse { e ->
            ToolResult(false, "Failed to launch $platformLabel chat for reading: ${e.message}")
        }
    }


    /**
     * Reads recent intercepted WhatsApp notifications, grouping them by sender.
     * Supports platform filtering (standard/business) and media type classification.
     */
    fun readRecentMessages(
        limit: Int = 5,
        senderFilter: String? = null,
        platformFilter: String? = null
    ): ToolResult {
        val targetPkg = if (platformFilter.isNullOrBlank()) null else resolveTargetPackage(platformFilter)
        val platformFiltered = NotificationStore.getRecentNotifications(limit = 25, exactPackageFilter = targetPkg)
        if (platformFiltered.isEmpty()) {
            return ToolResult(true, "You have no unread messages right now.")
        }

        val filtered = if (!senderFilter.isNullOrBlank()) {
            val filterLower = senderFilter.lowercase(Locale.ROOT).trim()
            platformFiltered.filter { it.title.lowercase(Locale.ROOT).contains(filterLower) }
        } else {
            platformFiltered
        }

        if (filtered.isEmpty()) {
            val context = when {
                !senderFilter.isNullOrBlank() && !platformFilter.isNullOrBlank() ->
                    "from \"$senderFilter\" on $platformFilter"
                !senderFilter.isNullOrBlank() -> "from \"$senderFilter\""
                !platformFilter.isNullOrBlank() -> "on $platformFilter"
                else -> ""
            }
            return ToolResult(true, "No recent WhatsApp messages found $context.".trim())
        }

        // Group by sender (notification title)
        val groupedBySender = filtered.groupBy { it.title }
        val senderSummaries = mutableListOf<String>()

        for ((sender, messages) in groupedBySender.entries.take(limit)) {
            val latestMsg = messages.first().text
            val msgType = classifyMessageType(latestMsg)
            val displayMsg = if (msgType != "text") "[$msgType]" else "\"$latestMsg\""
            val countText = if (messages.size > 1) " (${messages.size} messages)" else ""
            val srcLabel = if (messages.first().platform == com.jarvis.notification.WhatsAppPlatform.BUSINESS) " [Business]" else ""
            senderSummaries.add("$sender$srcLabel$countText: $displayMsg")
        }

        val totalCount = filtered.size
        val summaryText = buildString {
            append("You have $totalCount recent WhatsApp message${if (totalCount > 1) "s" else ""}. ")
            append(senderSummaries.joinToString("; "))
            append(". Would you like me to reply to anyone?")
        }

        return ToolResult(
            success = true,
            message = summaryText,
            data = mapOf(
                "totalCount" to totalCount,
                "sendersCount" to groupedBySender.size,
                "summary" to summaryText
            )
        )
    }

    private fun ensureScreenAwake() {
        try {
            val pm = context?.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm?.isInteractive == false) {
                @Suppress("DEPRECATION")
                val screenLock = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "Jarvis::WhatsAppScreenWake"
                )
                screenLock.acquire(5000L)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Screen wake for WhatsApp: ${e.message}")
        }
    }

    private fun formatWhatsAppNumber(rawNumber: String): String {
        val clean = rawNumber.filter { it.isDigit() }
        return when {
            clean.length == 10 -> "91$clean"
            clean.length == 11 && clean.startsWith("0") -> "91" + clean.substring(1)
            clean.length >= 11 -> clean
            else -> clean
        }
    }

    fun classifyMessageType(text: String): String {
        val lower = text.lowercase(Locale.ROOT).trim()
        return when {
            lower.isEmpty() || lower == "📷 photo" || lower.startsWith("📷") -> "photo"
            lower == "🎥 video" || lower.startsWith("🎥") -> "video"
            lower.startsWith("🎵") || lower == "🎵 audio" -> "audio"
            lower == "📎 document" || lower.startsWith("📎") -> "document"
            lower.startsWith("📍") || lower.contains("location") -> "location"
            lower == "sticker" || lower.startsWith("🏷") -> "sticker"
            lower.contains("gif") && lower.length < 10 -> "gif"
            lower.startsWith("☎") || lower.contains("missed voice call") -> "call"
            else -> "text"
        }
    }

    private fun findSendNodeForPackage(service: JarvisAccessibilityService, pkg: String): android.view.accessibility.AccessibilityNodeInfo? {
        return service.findNodeByViewId("$pkg:id/send")
            ?: service.findNodeByViewId("$pkg:id/btn_send")
            ?: service.findNodeByViewId("$pkg:id/send_button")
            ?: service.findNodeByViewId("$pkg:id/send_container")
            ?: service.findNodeByContentDescription("Send")
            ?: service.findNodeByContentDescription("send")
            ?: service.findNodeByContentDescription("Send message")
            ?: service.findNodeByContentDescription("भेजें")
            ?: service.findNodeByContentDescription("संदेश भेजें")
            ?: service.findNodeByText("Send")
            ?: service.findNodeByText("भेजें")
            ?: service.findNodeByContentDescription("send", exact = false)
            ?: service.findNodeByText("send", exact = false)
    }

    private suspend fun clickContinueToChatIfShown(pkg: String): Boolean {
        val node = UiAutomationManager.waitForNode(timeoutMs = 900) { service ->
            service.findNodeByViewId("$pkg:id/primary_button")
                ?: service.findNodeByText("continue to chat", exact = false)
        } ?: return false
        return JarvisAccessibilityService.instance?.clickNode(node) == true
    }

    private suspend fun clickSendButton(pkg: String): Boolean {
        val node = UiAutomationManager.waitForNode(timeoutMs = 5000) { service ->
            findSendNodeForPackage(service, pkg)
        } ?: return false
        return JarvisAccessibilityService.instance?.clickNode(node) == true
    }

    private fun findWhatsAppSendNode(service: JarvisAccessibilityService): android.view.accessibility.AccessibilityNodeInfo? {
        return findSendNodeForPackage(service, PKG_WHATSAPP)
    }

    private suspend fun autoReturnToHome() {
        val enabled = context?.let {
            runCatching { com.jarvis.ui.data.UiPreferencesStore(it).loadSettings().whatsAppAutoReturnEnabled }.getOrDefault(true)
        } ?: true
        if (enabled) {
            delay(400)
            JarvisAccessibilityService.instance?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            )
        }
    }

    /**
     * Handles incoming WhatsApp notification for potential auto-reply.
     */
    fun processIncomingNotification(sbn: StatusBarNotification?, item: NotificationItem) {
        if (!AutoReplyPolicyManager.isEnabled()) return
        if (item.packageName != "com.whatsapp" && item.packageName != "com.whatsapp.w4b") return

        if (sbn != null) {
            val replyAction = sbn.notification?.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
            if (replyAction != null && item.title.isNotBlank()) {
                registerReplyAction(item.id, item.title, replyAction)
            }
        }

        val sender = item.title.trim()
        if (sender.isBlank() || sender.equals("WhatsApp", ignoreCase = true)) return

        // Check cooldown from AutoReplyPolicyManager
        if (!AutoReplyPolicyManager.canReplyTo(sender)) {
            Log.d(TAG, "Auto-reply suppressed for '$sender' (cooldown active)")
            return
        }

        val effectiveMode = AutoReplyPolicyManager.mode

        GlobalScope.launch(Dispatchers.IO) {
            val currentLlm = llmClient
            val replyText = if (effectiveMode == AutoReplyMode.AI_SMART_REPLY && currentLlm != null) {
                val prompt = "You are Jarvis, an AI assistant. The user is currently unavailable. Generate a very short, polite auto-reply to this message from $sender: \"${item.text}\". Let them know Jarvis is answering and will remind the user. Do not use placeholders, and keep it under 30 words."
                val result = currentLlm.chat(listOf(com.jarvis.ai.Message("user", prompt)))
                if (result.isSuccess) {
                    result.getOrNull()?.trim() ?: AutoReplyPolicyManager.getTemplateForCurrentMode()
                } else {
                    AutoReplyPolicyManager.getTemplateForCurrentMode()
                }
            } else {
                AutoReplyPolicyManager.getTemplateForCurrentMode()
            }

            if (replyText.isBlank()) return@launch

            val sent = sendDirectNotificationReply(sender, replyText)
            if (sent) {
                AutoReplyPolicyManager.recordReplySent(sender, "WHATSAPP", replyText)
                val now = System.currentTimeMillis()
                lastAutoRepliedMap[sender.lowercase(Locale.ROOT)] = now
                synchronized(autoReplyHistory) {
                    autoReplyHistory.add(0, AutoReplyRecord(sender, replyText, effectiveMode, now))
                    while (autoReplyHistory.size > 20) autoReplyHistory.removeAt(autoReplyHistory.size - 1)
                }
                Log.i(TAG, "Auto-replied to '$sender' on WhatsApp in mode $effectiveMode: \"$replyText\"")
            }
        }
    }
}
