package com.jarvis.routine

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class CustomRoutineRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_custom_routines", Context.MODE_PRIVATE)

    @Synchronized fun load(): List<SmartRoutine> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { runCatching { decode(array.getJSONObject(it)) }.getOrNull() }
    }.onFailure { Log.e("CustomRoutineRepo", "Could not load saved routines", it) }.getOrDefault(emptyList())

    @Synchronized fun save(routines: List<SmartRoutine>) {
        val array = JSONArray()
        routines.forEach { routine ->
            val obj = JSONObject().put("id", routine.id).put("name", routine.name).put("description", routine.description)
                .put("trigger", routine.trigger.type.name).put("parameter", routine.trigger.parameter)
                .put("enabled", routine.isEnabled).put("lastTriggeredAt", routine.lastTriggeredAt)
                .put("cooldown", routine.cooldownMinutes).put("lastResult", routine.lastResult)
            val actions = JSONArray()
            routine.actions.forEach { action ->
                val params = JSONObject()
                action.params.forEach { (key, value) -> params.put(key, value) }
                actions.put(JSONObject().put("tool", action.toolName).put("params", params).put("description", action.description))
            }
            obj.put("actions", actions)
            array.put(obj)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun decode(obj: JSONObject): SmartRoutine {
        val trigger = TriggerType.valueOf(obj.getString("trigger"))
        val actionsJson = obj.getJSONArray("actions")
        val actions = (0 until actionsJson.length()).map { i ->
            val item = actionsJson.getJSONObject(i)
            val p = item.getJSONObject("params")
            val params = buildMap { p.keys().forEach { key -> put(key, p.getString(key)) } }
            RoutineAction(item.getString("tool"), params, item.optString("description"))
        }
        return SmartRoutine(obj.getString("id"), obj.getString("name"), obj.getString("description"),
            RoutineTrigger(trigger, obj.optString("parameter")), actions, obj.optBoolean("enabled", true),
            obj.optLong("lastTriggeredAt"), obj.optInt("cooldown", 1), obj.optString("lastResult"))
    }

    companion object { private const val KEY = "custom_routine_list_json" }
}
