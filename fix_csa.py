import re

with open("app/src/main/java/com/jarvis/telecom/CallAndSmsAgent.kt", "r") as f:
    content = f.read()

# Change constructor
content = content.replace("class CallAndSmsAgent(\n    context: Context? = null,\n    var llmClient: com.jarvis.ai.LlmClient? = null,\n    private val contactResolver: ContactResolver = ContactResolver(context?.applicationContext ?: context)\n) {", "class CallAndSmsAgent(\n    context: Context? = null,\n    private val contactResolver: ContactResolver = ContactResolver(context?.applicationContext ?: context)\n) {\n    var llmClient: com.jarvis.ai.LlmClient? = null")

with open("app/src/main/java/com/jarvis/telecom/CallAndSmsAgent.kt", "w") as f:
    f.write(content)

