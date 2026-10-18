package com.jarvis.tools

import android.content.Context
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.sensors.AirGestureEngine

/**
 * Tool for enabling, disabling, and checking touchless air gesture detection.
 */
class AirGestureTool(
    private val context: Context? = null,
    private val airGestureEngine: AirGestureEngine? = null
) : Tool {

    override val name: String = "AIR_GESTURES"
    override val description: String =
        "Controls touchless air gesture mode. Parameters: action ('enable', 'disable', 'status')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "AIR_GESTURES",
        description = "Enables or disables touchless proximity hand wave air gestures.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action: 'enable', 'disable', or 'status'", required = true)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "status"
        val engine = airGestureEngine

        return when (action) {
            "enable", "on", "start" -> {
                engine?.setEnabled(true)
                ToolResult.Success(
                    "Air gestures enabled. Wave your hand over the top sensor to control playback or scroll.",
                    mapOf("action" to "enable", "enabled" to "true")
                )
            }
            "disable", "off", "stop" -> {
                engine?.setEnabled(false)
                ToolResult.Success("Air gestures disabled.", mapOf("action" to "disable", "enabled" to "false"))
            }
            "status", "check" -> {
                val isEnabled = engine?.isEnabled == true
                val msg = if (isEnabled) "Air gestures are currently enabled and active." else "Air gestures are currently disabled."
                ToolResult.Success(msg, mapOf("action" to "status", "enabled" to isEnabled.toString()))
            }
            else -> ToolResult.Failed("Unknown air gesture action: '$action'. Supported: 'enable', 'disable', 'status'.")
        }
    }
}
