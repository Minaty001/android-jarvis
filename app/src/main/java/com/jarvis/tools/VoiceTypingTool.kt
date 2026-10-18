package com.jarvis.tools

import android.content.Context
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

/**
 * Universal voice typing tool that injects dictated text, performs backspace,
 * clears text, or submits the focused input element across any app.
 */
class VoiceTypingTool(
    private val context: Context? = null
) : Tool {

    override val name: String = "VOICE_TYPING"
    override val description: String =
        "Universal voice typing and focused input manipulation. Actions: 'type', 'backspace', 'clear', 'send'."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "VOICE_TYPING",
        description = "Injects text, deletes, clears, or submits input in the currently active on-screen input field.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action: 'type', 'backspace', 'clear', 'send'", required = true),
            ParameterSchema("text", "string", "Text string to type/inject into the focused field", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "type"
        val service = JarvisAccessibilityService.instance

        if (service == null) {
            return ToolResult.Failed("Jarvis Accessibility Service is not active. Please enable it in Settings to use voice typing.")
        }

        return when (action) {
            "type", "write", "dictate" -> {
                val text = params["text"]?.trim().orEmpty()
                if (text.isEmpty()) {
                    return ToolResult.Failed("No text provided to type.")
                }
                val success = service.injectTextIntoFocusedInput(text, append = true)
                if (success) {
                    ToolResult.Success("Typed: \"$text\"", mapOf("action" to "type", "text" to text))
                } else {
                    ToolResult.Failed("No active or editable input field detected on screen.")
                }
            }
            "backspace", "delete" -> {
                val success = service.backspaceFocusedInput()
                if (success) {
                    ToolResult.Success("Backspaced last input in field.", mapOf("action" to "backspace"))
                } else {
                    ToolResult.Failed("No active input field to backspace.")
                }
            }
            "clear", "clear_text" -> {
                val success = service.clearFocusedInput()
                if (success) {
                    ToolResult.Success("Cleared active input field.", mapOf("action" to "clear"))
                } else {
                    ToolResult.Failed("No active input field to clear.")
                }
            }
            "send", "enter", "submit" -> {
                val success = service.sendOrEnterFocusedInput()
                if (success) {
                    ToolResult.Success("Submitted focused input.", mapOf("action" to "send"))
                } else {
                    ToolResult.Failed("Could not find a submit action or send button.")
                }
            }
            else -> ToolResult.Failed("Unknown voice typing action: '$action'. Supported: type, backspace, clear, send.")
        }
    }
}
