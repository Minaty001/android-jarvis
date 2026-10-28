package com.jarvis.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Deep Battery Saver & Thermal Governor.
 * Proactively orchestrates low-power states, monitors thermal escalation,
 * and throttles non-essential background processes when power or heat crosses safety boundaries.
 */
class DeepBatteryGovernor(private val context: Context? = null) {

    private val TAG = "DeepBatteryGovernor"
    private val listeners = CopyOnWriteArrayList<(ThrottleState) -> Unit>()

    enum class ThrottleState {
        NORMAL,
        BATTERY_SAVING,
        CRITICAL_SAVINGS,
        THERMAL_THROTTLED
    }

    var currentThrottleState: ThrottleState = ThrottleState.NORMAL
        private set

    fun addThrottleListener(listener: (ThrottleState) -> Unit) {
        listeners.add(listener)
    }

    fun removeThrottleListener(listener: (ThrottleState) -> Unit) {
        listeners.remove(listener)
    }

    fun notifyThrottleChanged(newState: ThrottleState) {
        currentThrottleState = newState
        listeners.forEach { runCatching { it(newState) } }
        if (newState != ThrottleState.NORMAL) {
            JarvisEventBus.shared.post(
                JarvisEvent.SystemAlert(
                    level = if (newState == ThrottleState.THERMAL_THROTTLED || newState == ThrottleState.CRITICAL_SAVINGS) "CRITICAL" else "WARNING",
                    message = "Device entered throttle state: ${newState.name}. Throttling non-essential background tasks."
                )
            )
        }
    }

    data class HealthReport(
        val percentage: Int,
        val isCharging: Boolean,
        val temperatureCelsius: Double,
        val voltageMv: Int,
        val throttleState: ThrottleState,
        val recommendation: String
    )

    data class BatteryTrendReport(
        val percentage: Int,
        val isCharging: Boolean,
        val temperatureCelsius: Double,
        val voltageMv: Int,
        val throttleState: ThrottleState,
        val recommendation: String,
        val drainRatePerHour: Double = 0.0,
        val estimatedMinutesRemaining: Int? = null,
        val rationale: String = ""
    )

    fun evaluateGovernorStateWithReasoning(
        customPercentage: Int? = null,
        customCharging: Boolean? = null,
        drainRateOverride: Double? = null
    ): BatteryTrendReport {
        val baseReport = evaluateGovernorState()
        val pct = customPercentage ?: baseReport.percentage
        val isCharging = customCharging ?: baseReport.isCharging
        val drainRate = drainRateOverride ?: 0.0

        val estimatedMinutes = if (drainRate > 0.0 && !isCharging && pct > 0) {
            ((pct.toDouble() / drainRate) * 60.0).toInt().coerceAtLeast(1)
        } else null

        val adjustedState = if (estimatedMinutes != null && estimatedMinutes <= 45 && baseReport.throttleState == ThrottleState.NORMAL) {
            ThrottleState.BATTERY_SAVING
        } else {
            baseReport.throttleState
        }

        val rationale = if (estimatedMinutes != null) {
            "Projected battery depletion in ~$estimatedMinutes minutes under current drain rate ($drainRate%/hr). Throttle state: ${adjustedState.name}."
        } else {
            baseReport.recommendation
        }

        return BatteryTrendReport(
            percentage = pct,
            isCharging = isCharging,
            temperatureCelsius = baseReport.temperatureCelsius,
            voltageMv = baseReport.voltageMv,
            throttleState = adjustedState,
            recommendation = baseReport.recommendation,
            drainRatePerHour = drainRate,
            estimatedMinutesRemaining = estimatedMinutes,
            rationale = rationale
        )
    }

    fun evaluateGovernorState(): HealthReport {
        val ctx = context ?: return HealthReport(
            percentage = 100,
            isCharging = false,
            temperatureCelsius = 25.0,
            voltageMv = 4000,
            throttleState = ThrottleState.NORMAL,
            recommendation = "Optimal (Simulated)"
        )

        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10.0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isSystemPowerSave = powerManager?.isPowerSaveMode == true

        val newState = when {
            tempC >= 42.0 -> ThrottleState.THERMAL_THROTTLED
            currentThrottleState == ThrottleState.THERMAL_THROTTLED && tempC >= 40.0 -> ThrottleState.THERMAL_THROTTLED
            pct in 0..10 && !isCharging -> ThrottleState.CRITICAL_SAVINGS
            (pct in 0..20 && !isCharging) || isSystemPowerSave -> ThrottleState.BATTERY_SAVING
            else -> ThrottleState.NORMAL
        }

        if (newState != currentThrottleState) {
            notifyThrottleChanged(newState)
        } else {
            currentThrottleState = newState
        }

        val recommendation = when (newState) {
            ThrottleState.THERMAL_THROTTLED -> "Device temperature (${tempC}°C) is high. Throttling background tasks to cool down."
            ThrottleState.CRITICAL_SAVINGS -> "Battery critical ($pct%). Minimal power profile active; non-urgent voice synthesis muted."
            ThrottleState.BATTERY_SAVING -> "Battery saving active ($pct%). Background RAG & aggressive polling deferred."
            ThrottleState.NORMAL -> "Battery and thermal state are optimal."
        }

        Log.i(TAG, "Governor evaluated: state=$newState, pct=$pct, temp=${tempC}°C")

        return HealthReport(
            percentage = pct,
            isCharging = isCharging,
            temperatureCelsius = tempC,
            voltageMv = voltage,
            throttleState = newState,
            recommendation = recommendation
        )
    }

    fun isHighThroughputAllowed(): Boolean {
        return currentThrottleState == ThrottleState.NORMAL
    }
}
