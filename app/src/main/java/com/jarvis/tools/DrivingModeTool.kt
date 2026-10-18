package com.jarvis.tools

import android.content.Context
import com.jarvis.automotive.DrivingModeEngine
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

/**
 * Tool for activating, deactivating, and checking In-Car Driving Mode.
 */
class DrivingModeTool(
    private val context: Context? = null,
    private val drivingModeEngine: DrivingModeEngine? = null
) : Tool {

    override val name: String = "DRIVING_MODE"
    override val description: String =
        "Controls dedicated in-car driving mode. Parameters: action ('start', 'stop', 'status')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "DRIVING_MODE",
        description = "Activates persistent open-mic, mandatory speakerphone, and automotive HUD dashboard.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action: 'start', 'stop', or 'status'", required = true)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "status"
        val engine = drivingModeEngine ?: DrivingModeEngine.instance ?: DrivingModeEngine(context)

        return when (action) {
            "start", "enable", "on" -> {
                engine.setDrivingMode(true)
                ToolResult.Success(
                    "Driving Mode activated. Open-mic enabled, calls routed to speaker, incoming messages will be read aloud.",
                    mapOf("action" to "start", "driving_mode" to "true")
                )
            }
            "stop", "disable", "off", "exit" -> {
                engine.setDrivingMode(false)
                ToolResult.Success(
                    "Driving Mode deactivated. Normal assistant standby restored.",
                    mapOf("action" to "stop", "driving_mode" to "false")
                )
            }
            "status", "check" -> {
                val active = engine.isEnabled()
                val msg = if (active) "Driving Mode is currently active." else "Driving Mode is currently off."
                ToolResult.Success(msg, mapOf("action" to "status", "driving_mode" to active.toString()))
            }
            else -> ToolResult.Failed("Unknown driving mode action: '$action'. Supported: start, stop, status.")
        }
    }
}
