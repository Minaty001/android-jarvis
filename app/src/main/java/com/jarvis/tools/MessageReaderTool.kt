package com.jarvis.tools

import android.content.Context
import android.content.pm.PackageManager
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.messaging.UniversalMessagingEngine
import com.jarvis.notification.JarvisNotificationListenerService
import com.jarvis.telecom.CallAndSmsAgent

/**
 * Reads recent SMS and WhatsApp messages into a clean digest. Reuses the live
 * engine singletons; if they aren't running the answer is an honest "not
 * available yet" rather than a fabricated digest.
 */
class MessageReaderTool(
    private val context: Context? = null,
    private val permissionGate: com.jarvis.control.PermissionGate? = null
) : Tool {
    override val name: String = "MESSAGE_READER"
    override val description: String =
        "Reads recent SMS and WhatsApp messages. Actions: read_sms, read_whatsapp, read_all. Optional filters: sender, limit."

    val metadata = ToolMetadata(
        name = "MESSAGE_READER",
        description = "Reads recent incoming SMS and WhatsApp messages and summarises them.",
        parameters = emptyList(),
        riskLevel = RiskLevel.MEDIUM
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (permissionGate != null) {
            val intent = com.jarvis.control.ActionIntent(
                domain = "messaging",
                device = "messages",
                target = "inbox",
                action = "read",
                requiresConfirmation = true
            )
            val auth = permissionGate.verify(intent)
            if (!auth.isAuthorized) {
                return ToolResult.Failed(auth.reason ?: "Biometric Voice Security blocked access. Device must be unlocked or voice unrecognized.")
            }
        }

        val action = params["action"]?.trim()?.lowercase() ?: "read_all"
        if (action !in setOf("read_sms", "read_whatsapp", "read_telegram", "read_signal", "read_instagram", "read_all")) {
            return ToolResult.Failed("Unknown action '$action'. Supported: read_sms, read_whatsapp, read_all.")
        }
        val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 5
        val sender = params["sender"]
        val unavailable = mutableListOf<String>()

        val smsLines = mutableListOf<String>()
        val waLines = mutableListOf<String>()
        val platform = params["platform"]?.trim()?.lowercase()

        if (action == "read_sms" || action == "read_all") {
            if (context == null || context.checkSelfPermission(android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                if (action == "read_sms") return ToolResult.NeedsPermission(
                    android.Manifest.permission.READ_SMS,
                    "Grant SMS permission to read the device inbox."
                )
                unavailable.add("SMS inbox access needs SMS permission")
            } else {
                readSms(limit, sender, smsLines)
            }
        }
        if (action.startsWith("read_") && action != "read_sms" || action == "read_all") {
            if (context == null || !JarvisNotificationListenerService.isNotificationAccessGranted(context)) {
                if (action == "read_whatsapp") return ToolResult.Failed(
                    "Enable Jarvis under Android Notification Access to read WhatsApp notifications."
                )
                unavailable.add("WhatsApp reads need Jarvis Notification Access")
            } else {
                readWhatsApp(limit, sender, waLines)
            }
        }

        return when {
            action == "read_whatsapp" && waLines.isEmpty() && smsLines.isEmpty() -> {
                ToolResult.Success("No recent WhatsApp messages were available. New message reads require WhatsApp notifications and Jarvis Notification Access.")
            }
            waLines.isEmpty() && smsLines.isEmpty() && action == "read_all" -> ToolResult.Success(
                "No recent messages were found. SMS inbox access requires SMS permission; WhatsApp reads require Jarvis Notification Access."
            )
            waLines.isEmpty() && smsLines.isEmpty() -> ToolResult.Success("No recent messages were found from that source.")
            else -> {
                val digest = MessageDigestFormat.buildDigest(smsLines, waLines) +
                    if (unavailable.isNotEmpty()) " ${unavailable.joinToString("; ")}." else ""
                ToolResult.Success(digest, mapOf("sms" to smsLines.size.toString(), "whatsapp" to waLines.size.toString()))
            }
        }
    }

    private fun readSms(limit: Int, sender: String?, out: MutableList<String>) {
        val agent = CallAndSmsAgent.instance?.takeIf { it.hasReadSmsPermission() }
            ?: context?.let { CallAndSmsAgent(it) }
            ?: CallAndSmsAgent.instance
            ?: return
        agent.readRecentSms(limit = limit, senderFilter = sender).forEach {
            out.add(MessageDigestFormat.smsLine(it.contactName.ifBlank { it.sender }, it.body))
        }
    }

    private fun readWhatsApp(limit: Int, sender: String?, out: MutableList<String>) {
        val engine = com.jarvis.messaging.UniversalMessagingEngine.instance ?: context?.let { com.jarvis.messaging.UniversalMessagingEngine(it) } ?: return
        val result = engine.readRecentMessages(limit = limit, senderFilter = sender)
        if (result.success && !result.message.startsWith("You have no unread", ignoreCase = true) &&
            !result.message.startsWith("No recent WhatsApp messages", ignoreCase = true)) {
            result.message.split("\n").filter(String::isNotBlank).forEach { out.add(it) }
        }
    }
}
