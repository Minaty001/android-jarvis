import re

with open("app/src/main/java/com/jarvis/messaging/AutoReplyPolicyManager.kt", "r") as f:
    content = f.read()

content = content.replace("val result = llmClient.chat(prompt)", "val result = llmClient.chat(listOf(com.jarvis.ai.Message(\"user\", prompt)))")

with open("app/src/main/java/com/jarvis/messaging/AutoReplyPolicyManager.kt", "w") as f:
    f.write(content)

