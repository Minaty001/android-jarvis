package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.media.WhatsAppSessionManager
import com.jarvis.messaging.AutoReplyMode
import com.jarvis.messaging.UniversalMessagingEngine

class WhatsAppTool(
    private val context: Context? = null,
    private val messagingEngine: UniversalMessagingEngine? = null,
    private val llmClient: com.jarvis.ai.LlmClient? = null
) : Tool {
    override val name: String = "WHATSAPP"
    override val description: String = "Autonomous WhatsApp & WhatsApp Business messaging tool. Actions: 'send_message', 'send_direct_message', 'read_messages', 'reply', 'auto_reply', 'open', 'current_chat', 'chat_history', 'previous_chat'."

    private val TAG = "WhatsAppTool"

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val recipient = params["recipient"]?.trim()
        val message = params["message"]?.trim()
        val platform = params["platform"]?.trim()
        val rawAction = params["action"]?.trim()?.lowercase()

        val action = rawAction ?: when {
            params.containsKey("reply") || params.containsKey("respond") -> "reply"
            !recipient.isNullOrBlank() || !message.isNullOrBlank() -> "send_message"
            params.containsKey("mode") -> "auto_reply"
            else -> "open"
        }

        return try {
            val engine = messagingEngine ?: UniversalMessagingEngine.instance ?: UniversalMessagingEngine(context)

            when (action) {
                "open" -> {
                    val targetPkg = UniversalMessagingEngine.resolveTargetPackage(platform)
                    val pkgLabel = if (targetPkg == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp"
                    val ctx = context ?: run {
                        WhatsAppSessionManager.recordChat("WhatsApp Main Screen")
                        return ToolResult.Success("$pkgLabel opened (Simulation mode).")
                    }
                    val intent = ctx.packageManager.getLaunchIntentForPackage(targetPkg)
                        ?: return ToolResult.Failed("$pkgLabel is not installed on this device.")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    WhatsAppSessionManager.recordChat("WhatsApp Main Screen")
                    ToolResult.Success("$pkgLabel opened.")
                }

                "send_message", "send" -> {
                    if (recipient.isNullOrBlank() || message.isNullOrBlank()) {
                        return ToolResult.Failed("Recipient and message are required to send a WhatsApp message.")
                    }
                    WhatsAppSessionManager.recordChat(recipient, message = message, isOutbound = true)
                    engine.sendMessage(recipient, message, platform)
                }

                "send_direct_message", "direct_send", "direct_message", "send_direct" -> {
                    if (recipient.isNullOrBlank() || message.isNullOrBlank()) {
                        return ToolResult.Failed("Recipient and message are required to send a direct WhatsApp message.")
                    }
                    WhatsAppSessionManager.recordChat(recipient, message = message, isOutbound = true)
                    engine.sendDirectMessage(recipient, message, platform)
                }

                "reply", "reply_message", "respond" -> {
                    var targetRecipient = recipient
                    if (targetRecipient.isNullOrBlank() && !message.isNullOrBlank()) {
                        val recentItems = com.jarvis.notification.NotificationStore.getRecentNotifications(limit = 10)
                            .filter { it.packageName == "com.whatsapp" || it.packageName == "com.whatsapp.w4b" }
                        val lastSender = recentItems.firstOrNull { it.title.isNotBlank() && !it.title.equals("WhatsApp", ignoreCase = true) }?.title
                        if (!lastSender.isNullOrBlank()) {
                            targetRecipient = lastSender.trim()
                            Log.i(TAG, "Inferred reply recipient '$targetRecipient' from recent notification")
                        }
                    }

                    if (targetRecipient.isNullOrBlank()) {
                        val recent = engine.readRecentMessages(limit = 5, platformFilter = platform)
                        ToolResult.Success("Who would you like me to reply to? ${recent.message}")
                    } else if (message.isNullOrBlank()) {
                        ToolResult.Success("What would you like me to say to $targetRecipient?")
                    } else {
                        WhatsAppSessionManager.recordChat(targetRecipient, message = message, isOutbound = true)
                        engine.sendMessage(targetRecipient, message, platform)
                    }
                }

                "read_messages", "read_unread", "check_unread", "read" -> {
                    val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 10) ?: 5
                    val senderFilter = params["sender"]?.trim()
                    val platformFilter = params["platform"]?.trim()
                    senderFilter?.let { WhatsAppSessionManager.recordChat(it, message = "Reading recent messages", isOutbound = false) }
                    
                    // Priority 1: Check UI fallback if requested explicitly or if senderFilter is provided
                    if (!senderFilter.isNullOrBlank() && params["visual"]?.toBoolean() == true) {
                        return engine.readChatVisually(senderFilter, platformFilter)
                    }

                    val result = engine.readRecentMessages(limit = limit, senderFilter = senderFilter, platformFilter = platformFilter)
                    
                    if (result.success && llmClient != null && result.message.contains("recent WhatsApp message")) {
                        // Pass raw notification dump to LLM to summarize and detect urgency
                        val prompt = "You are JARVIS. Summarize this message dump naturally. Extract concrete tasks as <ACTION: task>. Suggest 2 short auto-replies. YOU MUST end your response by asking: \"Would you like to reply?\". Dump:\n${result.message}"
                        val llmResult = llmClient.chat(listOf(com.jarvis.ai.Message("user", prompt)))
                        if (llmResult.isSuccess) {
                            return ToolResult.Success(llmResult.getOrNull() ?: result.message)
                        }
                    }
                    
                    // Fallback to visual read if nothing found but a specific sender was asked
                    if (!senderFilter.isNullOrBlank() && result.message.contains("No recent WhatsApp messages")) {
                        return engine.readChatVisually(senderFilter, platformFilter)
                    }
                    
                    result
                }

                "current_chat", "current", "status" -> {
                    val current = WhatsAppSessionManager.getCurrentChat()
                    return if (current != null) {
                        ToolResult.Success("Active WhatsApp session chat with ${current.contactName}${if (current.lastMessage != null) " (Last msg: '${current.lastMessage}')" else ""}")
                    } else {
                        ToolResult.Success("No active WhatsApp chat session recorded.")
                    }
                }

                "chat_history", "history" -> {
                    val summary = WhatsAppSessionManager.getHistorySummary()
                    ToolResult.Success(summary)
                }

                "previous_chat", "previous" -> {
                    val prev = WhatsAppSessionManager.getPreviousChat()
                    return if (prev != null) {
                        ToolResult.Success("Switched to previous WhatsApp chat session with ${prev.contactName}")
                    } else {
                        ToolResult.Failed("No previous WhatsApp chat session found in history stack.")
                    }
                }

                "auto_reply" -> {
                    val modeStr = params["mode"]?.trim()?.lowercase() ?: "driving"
                    val template = params["template"]?.trim()

                    val targetMode = mapOf(
                        "off" to AutoReplyMode.OFF, "disable" to AutoReplyMode.OFF, "stop" to AutoReplyMode.OFF,
                        "driving" to AutoReplyMode.DRIVING, "drive" to AutoReplyMode.DRIVING,
                        "meeting" to AutoReplyMode.MEETING, "meet" to AutoReplyMode.MEETING,
                        "busy" to AutoReplyMode.BUSY,
                        "custom" to AutoReplyMode.CUSTOM,
                        "ai" to AutoReplyMode.AI_SMART_REPLY, "smart" to AutoReplyMode.AI_SMART_REPLY,
                        "on" to AutoReplyMode.DRIVING, "enable" to AutoReplyMode.DRIVING
                    )[modeStr] ?: AutoReplyMode.OFF

                    com.jarvis.messaging.AutoReplyPolicyManager.setMode(targetMode, template)

                    if (targetMode == AutoReplyMode.OFF)
                        ToolResult.Success("WhatsApp auto-reply has been turned off.")
                    else
                        ToolResult.Success("WhatsApp auto-reply active in ${targetMode.name} mode (10-min per-contact cooldown).")
                }

                else -> ToolResult.Failed("Unknown WhatsApp action: $action")
            }
        } catch (e: Exception) {
            ToolResult.Failed("Failed to execute WhatsApp operation: ${e.message}")
        }
    }
}
