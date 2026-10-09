import re

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "r") as f:
    content = f.read()

# Fix line 452 (chatVision)
content = content.replace("val visionResult = llmClient.chatVision", "val visionResult = llmClient?.chatVision")
# Since chatVision returns Result<String>, we need to handle ?.let or provide a default.
# The original was: if (imageNodes.isNotEmpty() && llmClient != null) ... val visionResult = llmClient.chatVision(...)
content = content.replace("if (imageNodes.isNotEmpty() && llmClient != null)", "val currentLlm = llmClient\n                    if (imageNodes.isNotEmpty() && currentLlm != null)")
content = content.replace("val visionResult = llmClient?.chatVision", "val visionResult = currentLlm.chatVision")


# Fix line 657
# Original: val replyText = if (effectiveMode == AutoReplyMode.AI_SMART_REPLY && llmClient != null) {
#               val prompt = ...
#               val result = llmClient.chat(prompt)
# Replace llmClient -> currentLlm
content = content.replace("val replyText = if (effectiveMode == AutoReplyMode.AI_SMART_REPLY && llmClient != null) {", "val currentLlm = llmClient\n            val replyText = if (effectiveMode == AutoReplyMode.AI_SMART_REPLY && currentLlm != null) {")
content = content.replace("val result = llmClient.chat(prompt)", "val result = currentLlm.chat(listOf(com.jarvis.ai.Message(\"user\", prompt)))")


with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "w") as f:
    f.write(content)

