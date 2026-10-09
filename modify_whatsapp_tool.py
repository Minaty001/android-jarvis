import sys

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "r") as f:
    content = f.read()

import re

# Add LlmClient to constructor
content = content.replace(
    "private val messagingEngine: WhatsAppMessagingEngine? = null\n) : Tool {",
    "private val messagingEngine: WhatsAppMessagingEngine? = null,\n    private val llmClient: com.jarvis.ai.LlmClient? = null\n) : Tool {"
)

# Update read_messages case
old_read = """                "read_messages", "read_unread", "check_unread", "read" -> {
                    val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 10) ?: 5
                    val senderFilter = params["sender"]?.trim()
                    val platformFilter = params["platform"]?.trim()
                    senderFilter?.let { WhatsAppSessionManager.recordChat(it, message = "Reading recent messages", isOutbound = false) }
                    engine.readRecentWhatsApp(limit = limit, senderFilter = senderFilter, platformFilter = platformFilter)
                }"""

new_read = """                "read_messages", "read_unread", "check_unread", "read" -> {
                    val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 10) ?: 5
                    val senderFilter = params["sender"]?.trim()
                    val platformFilter = params["platform"]?.trim()
                    senderFilter?.let { WhatsAppSessionManager.recordChat(it, message = "Reading recent messages", isOutbound = false) }
                    
                    // Priority 1: Check UI fallback if requested explicitly or if senderFilter is provided
                    if (!senderFilter.isNullOrBlank() && params["visual"]?.toBoolean() == true) {
                        return engine.readChatVisually(senderFilter, platformFilter)
                    }

                    val result = engine.readRecentWhatsApp(limit = limit, senderFilter = senderFilter, platformFilter = platformFilter)
                    
                    if (result.success && llmClient != null && result.message.contains("recent WhatsApp message")) {
                        // Pass raw notification dump to LLM to summarize and detect urgency
                        val prompt = "You are JARVIS, an intelligent assistant. Read the following WhatsApp notification dump. Summarize the messages naturally in 1-2 sentences. If any message seems URGENT, explicitly mention it. Finally, suggest 2-3 short context-aware auto-reply options. Dump:\\n${result.message}"
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
                }"""

content = content.replace(old_read, new_read)

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "w") as f:
    f.write(content)

