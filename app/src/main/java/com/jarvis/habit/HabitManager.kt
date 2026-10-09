package com.jarvis.habit

import android.util.Log
import com.jarvis.memory.MemoryStore
import com.jarvis.memory.MemoryType
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class HabitManager(private val memoryStore: MemoryStore? = null) {

    private val memoryFallback = ConcurrentHashMap<String, HabitItem>()

    fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    @Synchronized
    fun recordAppUsage(appName: String, timeOfDay: TimeOfDay = getCurrentTimeOfDay()) {
        val clean = appName.trim().replaceFirstChar { it.uppercase() }
        if (clean.isBlank()) return
        recordPattern(
            category = HabitCategory.APP_USAGE,
            target = clean,
            timeOfDay = timeOfDay,
            descriptionTemplate = "Frequently opens $clean in the ${timeOfDay.name.lowercase()}"
        )
    }

    @Synchronized
    fun recordCommunication(contactName: String, timeOfDay: TimeOfDay = getCurrentTimeOfDay()) {
        val clean = contactName.trim().replaceFirstChar { it.uppercase() }
        if (clean.isBlank()) return
        recordPattern(
            category = HabitCategory.COMMUNICATION,
            target = clean,
            timeOfDay = timeOfDay,
            descriptionTemplate = "Frequently messages $clean in the ${timeOfDay.name.lowercase()}"
        )
    }

    @Synchronized
    fun recordMediaPlayback(query: String, timeOfDay: TimeOfDay = getCurrentTimeOfDay()) {
        val clean = query.trim()
        if (clean.isBlank()) return
        recordPattern(
            category = HabitCategory.MEDIA_PLAYBACK,
            target = clean,
            timeOfDay = timeOfDay,
            descriptionTemplate = "Frequently plays '$clean' in the ${timeOfDay.name.lowercase()}"
        )
    }

    @Synchronized
    fun recordCustomHabit(description: String, timeOfDay: TimeOfDay = getCurrentTimeOfDay()) {
        val clean = description.trim()
        if (clean.isBlank()) return
        recordPattern(
            category = HabitCategory.CUSTOM_ROUTINE,
            target = clean,
            timeOfDay = timeOfDay,
            descriptionTemplate = clean
        )
    }

    @Synchronized
    fun recordWifiTransition(ssid: String, timeOfDay: TimeOfDay = getCurrentTimeOfDay()) {
        val clean = ssid.trim()
        if (clean.isBlank()) return
        recordPattern(
            category = HabitCategory.WIFI_NETWORK,
            target = clean,
            timeOfDay = timeOfDay,
            descriptionTemplate = "Frequently connects to Wi-Fi '$clean' in the ${timeOfDay.name.lowercase()}"
        )
    }

    @Synchronized
    fun recordChargingEvent(isCharging: Boolean, timeOfDay: TimeOfDay = getCurrentTimeOfDay()) {
        val status = if (isCharging) "Connected to charger" else "Disconnected from charger"
        recordPattern(
            category = HabitCategory.CHARGING_EVENT,
            target = if (isCharging) "charging_start" else "charging_stop",
            timeOfDay = timeOfDay,
            descriptionTemplate = "$status in the ${timeOfDay.name.lowercase()}"
        )
    }

    private fun recordPattern(
        category: HabitCategory,
        target: String,
        timeOfDay: TimeOfDay,
        descriptionTemplate: String
    ) {
        val habitId = "habit_${category.name.lowercase()}_${target.lowercase().replace(Regex("[^a-z0-9_]"), "_")}_${timeOfDay.name.lowercase()}"
        val existingHabits = getAllHabits()
        val existing = existingHabits.firstOrNull { it.id == habitId }

        val newFrequency = (existing?.frequency ?: 0) + 1
        // Save recurring daily patterns only after repeated observations (>= 3 occurrences)
        val newConfidence = if (newFrequency < 3) {
            0.3f
        } else {
            (0.6f + (0.10f * Math.min(newFrequency - 3, 4))).coerceIn(0.70f, 0.98f)
        }

        val updatedHabit = HabitItem(
            id = habitId,
            category = category,
            target = target,
            timeOfDay = timeOfDay,
            frequency = newFrequency,
            confidenceScore = newConfidence,
            lastOccurredAt = System.currentTimeMillis(),
            description = descriptionTemplate
        )

        memoryFallback[habitId] = updatedHabit

        memoryStore?.let { store ->
            val jsonContent = JSONObject().apply {
                put("id", updatedHabit.id)
                put("category", updatedHabit.category.name)
                put("target", updatedHabit.target)
                put("timeOfDay", updatedHabit.timeOfDay.name)
                put("frequency", updatedHabit.frequency)
                put("confidenceScore", updatedHabit.confidenceScore.toDouble())
                put("lastOccurredAt", updatedHabit.lastOccurredAt)
                put("description", updatedHabit.description)
            }.toString()
            store.archiveByProvenance(habitId)
            store.saveMemory(
                type = MemoryType.LEARNED_PATTERN,
                key = habitId,
                content = jsonContent,
                provenance = habitId,
                score = newConfidence.toDouble(),
                importance = newConfidence.toDouble(),
                namespace = "habits"
            )
        }
        runCatching { Log.i(TAG, "Learned habit updated: $habitId (freq=$newFrequency, conf=$newConfidence)") }
    }

    fun getAllHabits(): List<HabitItem> {
        val store = memoryStore
        if (store != null) {
            val items = store.getHabits(limit = 40)
            val list = items.mapNotNull { item ->
                runCatching {
                    val obj = JSONObject(item.content)
                    HabitItem(
                        id = obj.getString("id"),
                        category = HabitCategory.valueOf(obj.getString("category")),
                        target = obj.getString("target"),
                        timeOfDay = TimeOfDay.valueOf(obj.getString("timeOfDay")),
                        frequency = obj.getInt("frequency"),
                        confidenceScore = obj.getDouble("confidenceScore").toFloat(),
                        lastOccurredAt = obj.getLong("lastOccurredAt"),
                        description = obj.getString("description")
                    )
                }.getOrNull()
            }
            if (list.isNotEmpty()) {
                list.forEach { memoryFallback[it.id] = it }
                return list.sortedByDescending { it.confidenceScore }
            }
        }
        return memoryFallback.values.sortedByDescending { it.confidenceScore }
    }

    fun getHabitsForCurrentTime(timeOfDay: TimeOfDay = getCurrentTimeOfDay()): List<HabitItem> {
        return getAllHabits().filter { it.timeOfDay == timeOfDay || it.timeOfDay == TimeOfDay.ANYTIME }
    }

    fun getProactiveSuggestions(timeOfDay: TimeOfDay = getCurrentTimeOfDay()): List<HabitItem> {
        return getHabitsForCurrentTime(timeOfDay).filter { it.confidenceScore >= 0.6f && it.frequency >= 2 }
    }

    fun clear() {
        memoryFallback.clear()
    }

    fun clearHabits() {
        val store = memoryStore ?: return
        store.getHabits(limit = 1000).forEach { 
            store.archiveMemory(it.id)
        }
        memoryFallback.clear()
    }

    companion object {
        private const val TAG = "HabitManager"
    }
}
