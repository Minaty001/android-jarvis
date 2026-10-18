package com.jarvis.tools

import android.content.Context
import com.jarvis.accessibility.VoiceAccessOverlayController
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

class VoiceAccessTool(private val context: Context? = null) : Tool {
    override val name: String = "VOICE_ACCESS"
    override val description: String = "Displays numbered badges over interactive on-screen elements or clicks an element by number. Actions: 'show', 'hide', 'click' (param: number)."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("action", "string", "Action to perform: show, hide, click, tap", required = true),
            ParameterSchema("number", "string", "Badge number to click (e.g. '1', '2', '3')", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.lowercase()?.trim() ?: "show"

        return when (action) {
            "show", "display", "grid", "open" -> {
                val ctx = context ?: return ToolResult(false, "Context required to display Voice Access badges")
                val count = VoiceAccessOverlayController.showBadges(ctx)
                if (count > 0) {
                    ToolResult.Success("Displayed $count numbered badges. Say 'Tap <number>' to click any element.")
                } else {
                    ToolResult.Failed("Could not display badges. Ensure Accessibility Service and Display Over Other Apps permissions are enabled.")
                }
            }
            "hide", "close", "dismiss", "clear" -> {
                VoiceAccessOverlayController.hideBadges()
                ToolResult.Success("Numbered badges hidden.")
            }
            "click", "tap", "select", "press" -> {
                val rawNum = params["number"] ?: params["target"] ?: params["item"]
                val number = rawNum?.toIntOrNull()
                    ?: return ToolResult.Failed("Specify a valid badge number to tap (e.g. number='3').")

                val clicked = VoiceAccessOverlayController.clickBadge(number)
                if (clicked) {
                    ToolResult.Success("Tapped item #$number.")
                } else {
                    ToolResult.Failed("Badge #$number not found on screen. Say 'show numbers' to refresh badges.")
                }
            }
            else -> ToolResult.Failed("Unknown voice access action: '$action'. Supported: show, hide, click.")
        }
    }
}
