import re

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "r") as f:
    content = f.read()

# 1. Add AI_SMART_REPLY to enum
content = re.sub(r"enum class AutoReplyMode \{(.*?)\}", r"enum class AutoReplyMode {\1, AI_SMART_REPLY\n}", content, flags=re.DOTALL)

# 2. Remove local properties setAutoReplyMode, isAutoReplyActive
content = re.sub(r"    var autoReplyMode: AutoReplyMode.*?private set\n", "", content, flags=re.DOTALL)
content = re.sub(r"    var customAutoReplyTemplate: String = .*?private set\n", "", content, flags=re.DOTALL)
content = re.sub(r"    fun setAutoReplyMode\(.*?\).*?\}\n", "", content, flags=re.DOTALL)
content = re.sub(r"    fun isAutoReplyActive\(\): Boolean.*?OFF\n", "", content, flags=re.DOTALL)

# 3. Add Coroutine imports if not present
if "import kotlinx.coroutines.launch" not in content:
    content = content.replace("import kotlinx.coroutines.delay", "import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.GlobalScope\nimport kotlinx.coroutines.Dispatchers")

# 4. Replace processIncomingNotification
new_method = """    fun processIncomingNotification(sbn: StatusBarNotification?, item: NotificationItem) {
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
            val replyText = if (effectiveMode == AutoReplyMode.AI_SMART_REPLY && llmClient != null) {
                val prompt = "You are Jarvis, an AI assistant. The user is currently unavailable. Generate a very short, polite auto-reply to this message from $sender: \\"${item.text}\\". Let them know Jarvis is answering and will remind the user. Do not use placeholders, and keep it under 30 words."
                val result = llmClient.chat(prompt)
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
                Log.i(TAG, "Auto-replied to '$sender' on WhatsApp in mode $effectiveMode: \\"$replyText\\"")
            }
        }
    }
}"""

content = re.sub(r"    fun processIncomingNotification\(sbn: StatusBarNotification\?, item: NotificationItem\).*?\}\n\}", new_method, content, flags=re.DOTALL)

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "w") as f:
    f.write(content)

