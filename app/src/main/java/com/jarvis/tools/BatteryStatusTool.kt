package com.jarvis.tools

import android.content.Context
import com.jarvis.device.BatteryMonitor
import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

class BatteryStatusTool(private val context: Context? = null) : Tool {
    override val name: String = "BATTERY_CHECK"
    override val description: String = "Checks current battery level, percentage, and charging state."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "BATTERY_CHECK",
        description = "Provides battery status and charging connection info.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.lowercase()?.trim() ?: "check"
        val state = BatteryMonitor.getBatteryState(context)
        if (action == "health") {
            val summary = "Battery health is ${state.health}. Temperature is ${state.temperatureC.toInt()}°C. Level: ${state.percentage}%."
            return ToolResult.Success(
                message = summary,
                data = mapOf(
                    "action" to "health",
                    "health" to state.health,
                    "percentage" to state.percentage,
                    "temperature" to state.temperatureC
                )
            )
        }
        val summary = buildString {
            append(BatteryMonitor.formatSummary(state))
            append(" Temperature is ${state.temperatureC.toInt()}°C.")
        }
        return ToolResult.Success(
            message = summary,
            data = mapOf(
                "action" to "check",
                "percentage" to state.percentage,
                "is_charging" to state.isCharging,
                "is_full" to state.isFull,
                "plugged" to state.pluggedSource,
                "temperature" to state.temperatureC,
                "health" to state.health
            )
        )
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        val pct = result.data["percentage"] as? Int
        return if (!result.success) {
            VerificationResult.failure("Battery read failed: ${result.message}")
        } else if (pct != null) {
            VerificationResult.success("Battery read back at $pct%", mapOf("percentage" to pct.toString(), "outcome" to "READBACK"))
        } else {
            VerificationResult.unknown("Battery read returned without a percentage", mapOf("outcome" to "UNPARSED"))
        }
    }
}
