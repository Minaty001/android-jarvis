import sys

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "r") as f:
    content = f.read()

# Add LlmClient parameter
content = content.replace("class UniversalMessagingEngine(\n    context: Context? = null,", "class UniversalMessagingEngine(\n    context: Context? = null,\n    private val llmClient: com.jarvis.ai.LlmClient? = null,")

old_read_visually = """                    val chatNodes = nodes.filter { it.className == "android.widget.TextView" && !it.text.isNullOrBlank() }
                    val chatText = chatNodes.joinToString("\\n") { it.text!! }
                    autoReturnToHome()
                    if (chatText.isBlank()) return ToolResult(false, "Opened chat, but could not extract any text.")
                    return ToolResult(true, "Visually read chat with '$trimmedRecipient':\\n$chatText")"""

new_read_visually = """                    val chatNodes = nodes.filter { it.className == "android.widget.TextView" && !it.text.isNullOrBlank() }
                    var chatText = chatNodes.joinToString("\\n") { it.text!! }
                    
                    // Check for images
                    val imageNodes = nodes.filter { it.className == "android.widget.ImageView" || it.className == "android.widget.Image" }
                    if (imageNodes.isNotEmpty() && llmClient != null) {
                        try {
                            val screenshot = UiAutomationManager.takeScreenshotBase64(context)
                            if (screenshot != null) {
                                val visionResult = llmClient.chatVision("Describe the images in this chat briefly.", screenshot)
                                if (visionResult.isSuccess) {
                                    chatText += "\\n[Image Content: ${visionResult.getOrNull()}]"
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Vision extraction failed", e)
                        }
                    }
                    
                    autoReturnToHome()
                    if (chatText.isBlank()) return ToolResult(false, "Opened chat, but could not extract any text.")
                    return ToolResult(true, "Visually read chat with '$trimmedRecipient':\\n$chatText")"""

content = content.replace(old_read_visually, new_read_visually)

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "w") as f:
    f.write(content)
