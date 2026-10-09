import sys

with open("app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt", "r") as f:
    content = f.read()

content = content.replace(
    "WhatsAppTool(context, whatsappMessagingEngine)",
    "WhatsAppTool(context, whatsappMessagingEngine, currentLlmClient)"
)

with open("app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt", "w") as f:
    f.write(content)

