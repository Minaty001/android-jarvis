package com.jarvis.tools

import android.content.Context
import com.jarvis.emergency.EmergencySosBeaconEngine
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

/**
 * EmergencySosTool
 *
 * Emergency SOS dispatcher for life-critical situations.
 * Manages Morse flashlight strobe (... --- ...), siren tone,
 * location extraction, automated emergency SMS, and contact dialing.
 */
class EmergencySosTool(private val context: Context? = null) : Tool {

    override val name: String = "EMERGENCY_SOS"
    override val description: String =
        "Orchestrates Emergency SOS: Morse flashlight strobe (... --- ...), siren, GPS location dispatch, and hands-free emergency dialing. Actions: 'start', 'stop', 'status'."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("action", "string", "Action: start, stop, status", required = false),
            ParameterSchema("contact", "string", "Optional emergency contact number (defaults to 112)", required = false),
            ParameterSchema("dial", "boolean", "Whether to dial emergency services (default true)", required = false)
        ),
        riskLevel = RiskLevel.HIGH
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "start"
        val contact = params["contact"] ?: params["recipient"] ?: params["number"]
        val dial = params["dial"]?.toBooleanStrictOrNull() ?: true

        return when (action) {
            "stop", "cancel", "deactivate", "off", "band" -> {
                EmergencySosBeaconEngine.stopBeacon()
                ToolResult.Success("Emergency SOS Beacon stopped. Torch and siren deactivated.")
            }
            "status", "check" -> {
                val active = EmergencySosBeaconEngine.isBeaconActive()
                ToolResult.Success(
                    "Emergency SOS Beacon is currently ${if (active) "ACTIVE" else "inactive"}.",
                    mapOf("active" to active.toString())
                )
            }
            "start", "trigger", "sos", "alert" -> {
                EmergencySosBeaconEngine.startBeacon(context, contact, dial)
            }
            else -> ToolResult.Failed("Unknown SOS action '$action'. Use 'start', 'stop', or 'status'.")
        }
    }
}
