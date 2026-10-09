import re

# 1. Update UniversalMessagingEngine
with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "r") as f:
    ume = f.read()
ume = ume.replace("private val llmClient:", "var llmClient:")
with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "w") as f:
    f.write(ume)

# 2. Update CallAndSmsAgent
with open("app/src/main/java/com/jarvis/telecom/CallAndSmsAgent.kt", "r") as f:
    csa = f.read()

# Make sure imports are there
if "import kotlinx.coroutines.GlobalScope" not in csa:
    csa = csa.replace("import com.jarvis.accessibility.UiAutomationManager", "import com.jarvis.accessibility.UiAutomationManager\nimport kotlinx.coroutines.GlobalScope\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.Dispatchers")

# Add var llmClient
csa = re.sub(r"class CallAndSmsAgent\(\n    context: Context\? = null,\n    private val contactResolver", "class CallAndSmsAgent(\n    context: Context? = null,\n    var llmClient: com.jarvis.ai.LlmClient? = null,\n    private val contactResolver", csa)

# Replace handleIncomingSmsAutoReply call
old_sms_reply = """        // Evaluate SMS Auto-Reply via AutoReplyPolicyManager
        if (com.jarvis.messaging.AutoReplyPolicyManager.isEnabled()) {
            com.jarvis.messaging.AutoReplyPolicyManager.handleIncomingSmsAutoReply(context, cleanSender)
        }"""
new_sms_reply = """        // Evaluate SMS Auto-Reply via AutoReplyPolicyManager
        if (com.jarvis.messaging.AutoReplyPolicyManager.isEnabled()) {
            GlobalScope.launch(Dispatchers.IO) {
                com.jarvis.messaging.AutoReplyPolicyManager.handleIncomingSmsAutoReply(context, cleanSender, cleanBody, llmClient)
            }
        }"""
csa = csa.replace(old_sms_reply, new_sms_reply)

with open("app/src/main/java/com/jarvis/telecom/CallAndSmsAgent.kt", "w") as f:
    f.write(csa)

