import re

with open("app/src/main/java/com/jarvis/messaging/AutoReplyPolicyManager.kt", "r") as f:
    content = f.read()

# 1. Update getTemplateForCurrentMode
template_replacement = """    fun getTemplateForCurrentMode(): String {
        return when (mode) {
            AutoReplyMode.DRIVING -> "I am currently driving. Jarvis AI will notify me of your message safely."
            AutoReplyMode.MEETING -> "I am in a meeting right now. Jarvis AI will remind me to respond once I am free."
            AutoReplyMode.BUSY -> "I am currently occupied. Jarvis AI will remind me to get back to you shortly."
            AutoReplyMode.CUSTOM -> customTemplate
            AutoReplyMode.AI_SMART_REPLY -> "I am currently unavailable. Jarvis AI has recorded your message."
            AutoReplyMode.OFF -> ""
        }
    }"""
content = re.sub(r"    fun getTemplateForCurrentMode\(\): String \{.*?    \}", template_replacement, content, flags=re.DOTALL)

# 2. Update handleIncomingSmsAutoReply to suspend
new_method = """    suspend fun handleIncomingSmsAutoReply(context: Context?, senderNumber: String, messageBody: String, llmClient: com.jarvis.ai.LlmClient? = null): Boolean {
        if (!isSmsEnabled || !canReplyTo(senderNumber)) return false
        
        val replyText = if (mode == AutoReplyMode.AI_SMART_REPLY && llmClient != null && messageBody.isNotBlank()) {
            val prompt = "You are Jarvis, an AI assistant. The user is currently unavailable. Generate a very short, polite SMS auto-reply to this message from $senderNumber: \\"$messageBody\\". Let them know Jarvis is answering and will remind the user. Keep it under 160 characters."
            val result = llmClient.chat(prompt)
            if (result.isSuccess) {
                result.getOrNull()?.trim() ?: getTemplateForCurrentMode()
            } else {
                getTemplateForCurrentMode()
            }
        } else {
            getTemplateForCurrentMode()
        }

        if (replyText.isBlank()) return false

        if (context == null) {
            recordReplySent(senderNumber, "SMS", replyText)
            return true
        }

        return runCatching {
            val smsManager = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            smsManager.sendTextMessage(senderNumber, null, replyText, null, null)
            recordReplySent(senderNumber, "SMS", replyText)
            true
        }.getOrElse { e ->
            Log.w(TAG, "Failed sending SMS auto-reply to $senderNumber: ${e.message}")
            false
        }
    }"""
content = re.sub(r"    fun handleIncomingSmsAutoReply\(context: Context\?, senderNumber: String\): Boolean \{.*?\n    \}", new_method, content, flags=re.DOTALL)

with open("app/src/main/java/com/jarvis/messaging/AutoReplyPolicyManager.kt", "w") as f:
    f.write(content)

