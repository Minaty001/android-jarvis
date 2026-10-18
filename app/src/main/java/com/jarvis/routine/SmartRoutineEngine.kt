package com.jarvis.routine

import android.util.Log
import com.jarvis.tools.ToolExecutor
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class SmartRoutineEngine(
    private val toolExecutor: ToolExecutor? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val repository: CustomRoutineRepository? = null
) {
    private val TAG = "SmartRoutineEngine"
    private val routines = ConcurrentHashMap<String, SmartRoutine>()
    @Volatile private var draft: Pair<SmartRoutine, Long>? = null

    init {
        registerDefaultRoutines()
        repository?.load()?.forEach { routines[it.id] = it }
    }

    private fun registerDefaultRoutines() {
        val lowBattery = SmartRoutine(
            id = "routine_low_battery",
            name = "Low Battery Saver",
            description = "Lowers volume, speaks alert, and ensures torch is off when battery drops below 20%.",
            trigger = RoutineTrigger(TriggerType.BATTERY_LEVEL_DROPS_BELOW, parameter = "20"),
            actions = listOf(
                RoutineAction("SPEAK", mapOf("text" to "Boss, battery dropped below 20%. Switching to battery saver mode."), "Speak battery warning"),
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_volume", "volume_percent" to "20"), "Lower media volume to 20%"),
                RoutineAction("FLASHLIGHT", mapOf("mode" to "off"), "Turn off torch")
            ),
            isEnabled = true,
            cooldownMinutes = 30
        )

        val bedtime = SmartRoutine(
            id = "routine_bedtime",
            name = "Bedtime Wind-Down",
            description = "Silences device, stops active media, and sets volume to 10% when charging at night.",
            trigger = RoutineTrigger(TriggerType.CHARGER_CONNECTED, parameter = "night"),
            actions = listOf(
                RoutineAction("MEDIA_STOP", emptyMap(), "Stop playing media"),
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_ringer", "ringer_mode" to "vibrate"), "Set ringer to vibrate"),
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_volume", "volume_percent" to "10"), "Lower media volume to 10%"),
                RoutineAction("SPEAK", mapOf("text" to "Good night boss. Setting night profile and lowering volume."), "Good night greeting")
            ),
            isEnabled = true,
            cooldownMinutes = 60
        )

        val workMode = SmartRoutine(
            id = "routine_work_mode",
            name = "Work Arrival",
            description = "Sets ringer to vibrate and lowers volume when connected to office Wi-Fi.",
            trigger = RoutineTrigger(TriggerType.WIFI_CONNECTED, parameter = "Office"),
            actions = listOf(
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_ringer", "ringer_mode" to "vibrate"), "Set ringer to vibrate"),
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_volume", "volume_percent" to "15"), "Set volume to 15%"),
                RoutineAction("SPEAK", mapOf("text" to "Connected to office network. Ringer set to vibrate."), "Office arrival announcement")
            ),
            isEnabled = true,
            cooldownMinutes = 45
        )

        val homeArrival = SmartRoutine(
            id = "routine_home_arrival",
            name = "Home Arrival",
            description = "Restores normal ringer and volume, and announces daily briefing when connected to home Wi-Fi.",
            trigger = RoutineTrigger(TriggerType.WIFI_CONNECTED, parameter = "Home"),
            actions = listOf(
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_ringer", "ringer_mode" to "normal"), "Set ringer to normal"),
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_volume", "volume_percent" to "75"), "Set volume to 75%"),
                RoutineAction("DAILY_BRIEFING", emptyMap(), "Announce daily briefing")
            ),
            isEnabled = true,
            cooldownMinutes = 45
        )

        val gymArrival = SmartRoutine(
            id = "routine_gym_arrival",
            name = "Gym Arrival",
            description = "Turns ringer to vibrate and greets when arriving at the gym.",
            trigger = RoutineTrigger(TriggerType.GEOFENCE_ENTER, parameter = "gym"),
            actions = listOf(
                RoutineAction("DEVICE_SETTINGS", mapOf("action" to "set_ringer", "ringer_mode" to "vibrate"), "Set ringer to vibrate"),
                RoutineAction("SPEAK", mapOf("text" to "Welcome to the gym, boss. Let's get it."), "Gym greeting")
            ),
            isEnabled = true,
            cooldownMinutes = 120
        )

        routines[lowBattery.id] = lowBattery
        routines[bedtime.id] = bedtime
        routines[workMode.id] = workMode
        routines[homeArrival.id] = homeArrival
        routines[gymArrival.id] = gymArrival
    }

    fun getAllRoutines(): List<SmartRoutine> = routines.values.toList()

    fun getRoutine(idOrName: String): SmartRoutine? {
        val clean = idOrName.trim().lowercase()
        return routines[clean]
            ?: routines.values.firstOrNull { it.id.equals(clean, true) || it.name.lowercase().contains(clean) }
    }

    fun registerRoutine(routine: SmartRoutine) {
        routines[routine.id] = routine
    }

    fun addCustomRoutine(routine: SmartRoutine) {
        routines[routine.id] = routine
        persistCustomRoutines()
    }

    fun setPendingDraft(routine: SmartRoutine): Boolean {
        if (pendingDraft() != null) return false
        draft = routine to (System.currentTimeMillis() + DRAFT_TTL_MS)
        return true
    }

    fun pendingDraft(): SmartRoutine? {
        val current = draft ?: return null
        if (System.currentTimeMillis() >= current.second) { draft = null; return null }
        return current.first
    }

    fun confirmPendingDraft(): SmartRoutine? {
        val routine = pendingDraft() ?: return null
        draft = null
        addCustomRoutine(routine)
        return routine
    }

    fun discardPendingDraft(): Boolean {
        val existed = pendingDraft() != null
        draft = null
        return existed
    }

    fun deleteRoutine(idOrName: String): Boolean {
        val routine = getRoutine(idOrName) ?: return false
        routines.remove(routine.id)
        persistCustomRoutines()
        return true
    }

    fun enableRoutine(idOrName: String, enable: Boolean): Boolean {
        val routine = getRoutine(idOrName) ?: return false
        routine.isEnabled = enable
        persistCustomRoutines()
        return true
    }

    fun onWifiConnected(ssid: String) {
        val cleanSsid = ssid.trim()
        routines.values
            .filter { it.isEnabled && it.trigger.type == TriggerType.WIFI_CONNECTED }
            .forEach { routine ->
                val targetSsid = routine.trigger.parameter.trim()
                val isMatch = targetSsid.isBlank() ||
                        targetSsid.equals("wifi", ignoreCase = true) ||
                        cleanSsid.contains(targetSsid, ignoreCase = true)
                if (isMatch) {
                    evaluateAndExecute(routine, "Connected to Wi-Fi '$cleanSsid'")
                }
            }
    }

    fun onWifiDisconnected() {
        routines.values
            .filter { it.isEnabled && it.trigger.type == TriggerType.WIFI_DISCONNECTED }
            .forEach { routine ->
                evaluateAndExecute(routine, "Disconnected from Wi-Fi")
            }
    }

    fun onBatteryLevelChanged(level: Int, isCharging: Boolean) {
        // Battery level drops below threshold
        routines.values
            .filter { it.isEnabled && it.trigger.type == TriggerType.BATTERY_LEVEL_DROPS_BELOW }
            .forEach { routine ->
                val threshold = routine.trigger.parameter.toIntOrNull() ?: 20
                if (level <= threshold && !isCharging) {
                    evaluateAndExecute(routine, "Battery level is $level% (threshold $threshold%)")
                }
            }

        // Battery full (100%)
        if (level >= 100) {
            routines.values
                .filter { it.isEnabled && it.trigger.type == TriggerType.BATTERY_FULL }
                .forEach { routine ->
                    evaluateAndExecute(routine, "Battery is fully charged (100%)")
                }
        }
    }

    fun onChargerConnected() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 22 || hour < 6

        routines.values
            .filter { it.isEnabled && it.trigger.type == TriggerType.CHARGER_CONNECTED }
            .forEach { routine ->
                if (routine.trigger.parameter.equals("night", ignoreCase = true)) {
                    if (isNight) {
                        evaluateAndExecute(routine, "Charger connected during night hours ($hour:00)")
                    }
                } else {
                    evaluateAndExecute(routine, "Charger connected")
                }
            }
    }

    fun onChargerDisconnected() {
        routines.values
            .filter { it.isEnabled && it.trigger.type == TriggerType.CHARGER_DISCONNECTED }
            .forEach { routine ->
                evaluateAndExecute(routine, "Charger disconnected")
            }
    }

    fun onTimeTick(hour: Int, minute: Int) {
        val timeStr = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
        routines.values
            .filter { it.isEnabled && it.trigger.type == TriggerType.TIME_SCHEDULE }
            .forEach { routine ->
                if (routine.trigger.parameter.trim() == timeStr) {
                    evaluateAndExecute(routine, "Scheduled time $timeStr reached")
                }
            }
    }

    fun onGeofenceTransition(regionId: String, entered: Boolean) {
        val triggerType = if (entered) TriggerType.GEOFENCE_ENTER else TriggerType.GEOFENCE_EXIT
        routines.values
            .filter { it.isEnabled && it.trigger.type == triggerType }
            .forEach { routine ->
                val targetRegion = routine.trigger.parameter.trim()
                if (targetRegion.equals(regionId, ignoreCase = true) || targetRegion.isBlank()) {
                    val actionStr = if (entered) "Entered" else "Exited"
                    evaluateAndExecute(routine, "$actionStr geofence region '$regionId'")
                }
            }
    }

    suspend fun executeRoutine(routine: SmartRoutine, isManual: Boolean = false): RoutineExecutionResult {
        if (routine.id.startsWith("routine_custom_")) {
            runCatching { RoutineDraftValidator.validate(routine) }.onFailure {
                return RoutineExecutionResult(routine.id, routine.name, false, "Routine validation failed: ${it.message}", errors = listOf(it.message ?: "Invalid routine"))
            }
        }
        val now = System.currentTimeMillis()
        if (!isManual) {
            if (!routine.isEnabled) {
                return RoutineExecutionResult(routine.id, routine.name, false, "Routine is disabled")
            }
            val cooldownMs = routine.cooldownMinutes * 60 * 1000L
            if (routine.lastTriggeredAt > 0 && (now - routine.lastTriggeredAt) < cooldownMs) {
                val remainingMins = ((cooldownMs - (now - routine.lastTriggeredAt)) / 60000L).coerceAtLeast(1)
                return RoutineExecutionResult(
                    routine.id,
                    routine.name,
                    false,
                    "Cooldown active (remaining: ${remainingMins}m)"
                )
            }
        }

        routine.lastTriggeredAt = now
        persistCustomRoutines()
        val errors = mutableListOf<String>()
        var executedCount = 0

        for (action in routine.actions) {
            try {
                if (toolExecutor != null) {
                    val result = toolExecutor.execute(action.toolName, action.params, userApprovalGranted = true)
                    if (result is ToolResult.Success) {
                        executedCount++
                    } else {
                        val error = (result as? ToolResult.Failed)?.error ?: result.message
                        errors.add("${action.toolName}: $error")
                    }
                } else {
                    executedCount++
                }
            } catch (e: Exception) {
                errors.add("${action.toolName}: ${e.message}")
            }
        }

        runCatching {
            Log.i(TAG, "Routine '${routine.name}' executed ($executedCount/${routine.actions.size} actions, errors=${errors.size})")
        }

        val success = errors.isEmpty() && executedCount == routine.actions.size
        val reason = when {
            success -> "Executed successfully"
            executedCount == 0 -> "All ${routine.actions.size} actions failed"
            else -> "Partially executed ($executedCount/${routine.actions.size} actions succeeded)"
        }
        routine.lastResult = if (success) reason else "$reason: ${errors.joinToString("; ")}".take(500)
        persistCustomRoutines()
        return RoutineExecutionResult(
            routineId = routine.id,
            routineName = routine.name,
            executed = success,
            reason = reason,
            executedActionsCount = executedCount,
            errors = errors
        )
    }

    private fun evaluateAndExecute(routine: SmartRoutine, triggerReason: String) {
        val now = System.currentTimeMillis()
        val cooldownMs = routine.cooldownMinutes * 60 * 1000L
        if (routine.lastTriggeredAt > 0 && (now - routine.lastTriggeredAt) < cooldownMs) {
            return
        }

        coroutineScope.launch {
            try {
                runCatching { Log.i(TAG, "Triggering routine '${routine.name}' due to: $triggerReason") }
                executeRoutine(routine, isManual = false)
            } catch (e: Exception) {
                runCatching { Log.e(TAG, "Failed executing routine '${routine.name}'", e) }
            }
        }
    }

    fun clear() {
        routines.clear()
    }

    fun resetToDefaults() {
        routines.clear()
        registerDefaultRoutines()
        repository?.load()?.forEach { routines[it.id] = it }
    }

    private fun persistCustomRoutines() {
        repository?.save(routines.values.filter { it.id.startsWith("routine_custom_") })
    }

    companion object {
        private const val DRAFT_TTL_MS = 60_000L
        @Volatile
        var instance: SmartRoutineEngine? = null
    }
}
