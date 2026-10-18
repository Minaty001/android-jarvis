package com.jarvis.tools

import android.content.Context
import com.jarvis.assistant.AssistantSettingsHelper
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

class DefaultAssistantTool(private val context: Context? = null) : Tool {

    override val name: String = "DEFAULT_ASSISTANT"
    override val description: String =
        "Inspects or opens Android settings to configure Jarvis as default digital assistant. Parameter: action ('open', 'check')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "DEFAULT_ASSISTANT",
        description = "Opens Default Assistant settings or checks assistant status.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action to perform: 'open' or 'check'", required = true)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "check"
        val ctx = context ?: return ToolResult.Failed("Context unavailable")

        return when (action) {
            "open" -> {
                val opened = AssistantSettingsHelper.openDefaultAssistantSettings(ctx)
                if (opened) {
                    ToolResult.Success("Opening default digital assistant settings.", mapOf("action" to "open"))
                } else {
                    ToolResult.Failed("Unable to open default digital assistant settings.")
                }
            }
            "check", "status" -> {
                val isDefault = AssistantSettingsHelper.isDefaultAssistant(ctx)
                val message = AssistantSettingsHelper.formatStatusMessage(isDefault)
                ToolResult.Success(message, mapOf("isDefault" to isDefault.toString()))
            }
            else -> ToolResult.Failed("Unknown action: '$action'. Supported: 'open', 'check'.")
        }
    }
}
