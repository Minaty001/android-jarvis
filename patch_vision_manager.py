import sys

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "r") as f:
    content = f.read()

content = content.replace("val screenshot = UiAutomationManager.takeScreenshotBase64(context)", "val screenshot = com.jarvis.accessibility.ScreenVisionManager.captureScreen().base64Image")

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "w") as f:
    f.write(content)
