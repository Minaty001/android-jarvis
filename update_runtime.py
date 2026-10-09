import re

with open("app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt", "r") as f:
    content = f.read()

injection = """        currentLlmClient = client
        currentStrongClient = strongClient
        
        // Inject LlmClient into messaging subsystems for AI smart reply
        subsystems.whatsappMessagingEngine.llmClient = client
        subsystems.callAndSmsAgent.llmClient = client"""

content = content.replace("        currentLlmClient = client\n        currentStrongClient = strongClient", injection)

with open("app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt", "w") as f:
    f.write(content)

