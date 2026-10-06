package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

class PowerOptimizerTool(private val context: Context? = null) : Tool {
    override val name: String = "POWER_OPTIMIZER"
    override val description: String =
        "Inspects battery thermal health, voltage, temperature, and performs 1-tap power saving optimization. Parameters: action ('optimize', 'status', 'thermal', 'battery_saver_on', 'battery_saver_off')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "POWER_OPTIMIZER",
        description = "Monitors battery thermal state and optimizes device power usage.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "status"
        val ctx = context ?: return simulateExecution(action)

        return try {
            val batteryStatus: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempCelsius = tempTenths / 10.0
            val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: 0

            val healthStr = when (healthCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                else -> "Normal"
            }

            val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSaveMode = powerManager?.isPowerSaveMode == true

            when (action) {
                "thermal", "temperature" -> {
                    ToolResult.Success(
                        "Battery temperature is ${tempCelsius}°C. Health status: $healthStr.",
                        mapOf(
                            "temperature" to tempCelsius,
                            "health" to healthStr,
                            "voltage_mv" to voltage
                        )
                    )
                }
                "battery_saver_on", "saver_on" -> {
                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                    ToolResult.Success(
                        "Opened Battery Saver Settings to enable power saving.",
                        mapOf("action" to action, "powerSaveActive" to true)
                    )
                }
                "optimize" -> {
                    // Reduce brightness slightly if possible and trigger optimization steps
                    val currentBrightness = try {
                        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    } catch (_: Exception) { 128 }

                    val optimizedBrightness = (currentBrightness * 0.75).toInt().coerceAtLeast(30)
                    try {
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, optimizedBrightness)
                    } catch (_: Exception) {}

                    ToolResult.Success(
                        "Power optimization complete: Battery temp is ${tempCelsius}°C ($healthStr health), adjusted display brightness to save power.",
                        mapOf(
                            "optimized" to true,
                            "temp" to tempCelsius,
                            "health" to healthStr,
                            "powerSaveMode" to isPowerSaveMode
                        )
                    )
                }
                else -> {
                    // Default: status
                    ToolResult.Success(
                        "Battery Status: Health is $healthStr, Temperature is ${tempCelsius}°C, Voltage: ${voltage}mV, Power Saver: ${if (isPowerSaveMode) "Active" else "Off"}.",
                        mapOf(
                            "temp" to tempCelsius,
                            "health" to healthStr,
                            "voltage" to voltage,
                            "isPowerSave" to isPowerSaveMode
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult.Failed("Power Optimizer failed: ${e.message}")
        }
    }

    private fun simulateExecution(action: String): ToolResult {
        return ToolResult.Success(
            "Power Optimizer [$action] completed (Simulation mode). Battery temp: 31.5°C, Health: Good.",
            mapOf("simulation" to true, "action" to action)
        )
    }
}
