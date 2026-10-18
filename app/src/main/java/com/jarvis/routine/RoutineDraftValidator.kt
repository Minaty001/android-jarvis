package com.jarvis.routine

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Validates LLM-produced routine data at the tool boundary. */
object RoutineDraftValidator {
    fun validate(routine: SmartRoutine) {
        val actions = JSONArray()
        routine.actions.forEach { action ->
            val params = JSONObject()
            action.params.filterKeys { it != "routine_mode" }.forEach { (key, value) -> params.put(key, value) }
            actions.put(JSONObject().put("toolName", action.toolName).put("params", params).put("description", action.description))
        }
        create(routine.name, routine.trigger.type.name, routine.trigger.parameter, actions.toString())
    }

    fun create(name: String, triggerName: String, parameter: String, actionsJson: String): SmartRoutine {
        require(name.trim().isNotEmpty() && name.length <= 60) { "Give the routine a name (up to 60 characters)." }
        val type = runCatching { TriggerType.valueOf(triggerName.trim().uppercase()) }.getOrNull()
            ?: throw IllegalArgumentException("Unsupported routine trigger.")
        require(type in setOf(TriggerType.TIME_SCHEDULE, TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED,
            TriggerType.BATTERY_LEVEL_DROPS_BELOW, TriggerType.BATTERY_FULL, TriggerType.CHARGER_CONNECTED,
            TriggerType.CHARGER_DISCONNECTED)) { "That trigger is not supported for voice routines." }
        val value = parameter.trim()
        when (type) {
            TriggerType.TIME_SCHEDULE -> require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(value)) { "Use a valid daily time such as 08:30." }
            TriggerType.WIFI_CONNECTED -> require(value.isNotBlank() && !value.equals("wifi", true)) { "Specify the Wi-Fi network name." }
            TriggerType.BATTERY_LEVEL_DROPS_BELOW -> require(value.toIntOrNull() in 1..99) { "Battery threshold must be between 1 and 99." }
            else -> require(value.isEmpty()) { "This trigger does not take a parameter." }
        }
        val json = runCatching { JSONArray(actionsJson) }.getOrElse { throw IllegalArgumentException("Routine actions could not be read.") }
        require(json.length() in 1..8) { "Add between 1 and 8 routine actions." }
        val actions = (0 until json.length()).map { index ->
            val item = json.optJSONObject(index) ?: throw IllegalArgumentException("Action ${index + 1} is invalid.")
            val tool = item.optString("toolName").uppercase()
            val paramsObj = item.optJSONObject("params")
            val params = buildMap {
                if (paramsObj != null) paramsObj.keys().forEach { key -> put(key, paramsObj.optString(key)) }
            }
            val description = item.optString("description")
            val safeParams = when (tool) {
                "SPEAK" -> params.keys == setOf("text") && params["text"].orEmpty().isNotBlank() && params["text"]!!.length <= 300
                "DEVICE_SETTINGS" -> when (params["action"]) {
                    "set_volume" -> params.keys.all { it in setOf("action", "volume_percent", "stream") } &&
                            params["volume_percent"]?.toIntOrNull() in 0..100 && params["stream"] in setOf(null, "music", "ring", "alarm", "notification")
                    "set_ringer" -> params.keys.all { it in setOf("action", "ringer_mode") } && params["ringer_mode"] in setOf("normal", "vibrate", "silent")
                    else -> false
                }
                "MEDIA_CONTROL", "MEDIA_STOP" -> params["action"] == "stop" && params.keys == setOf("action")
                "SYSTEM_SWITCHBOARD" -> params.keys == setOf("action", "target") &&
                        params["action"] in setOf("wifi", "bt", "led") && params["target"] in setOf("on", "off")
                else -> false
            }
            require(safeParams) { "Action ${index + 1} is unsupported or has unsafe parameters." }
            val finalTool = if (tool == "MEDIA_STOP") "MEDIA_CONTROL" else tool
            val finalParams = if (finalTool == "SYSTEM_SWITCHBOARD") params + ("routine_mode" to "true") else params
            RoutineAction(finalTool, finalParams, description.take(100))
        }
        return SmartRoutine(
            id = "routine_custom_${UUID.randomUUID()}", name = name.trim(),
            description = "Daily automation: ${type.name.lowercase().replace('_', ' ')}${value.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}",
            trigger = RoutineTrigger(type, value), actions = actions, cooldownMinutes = 1
        )
    }
}
