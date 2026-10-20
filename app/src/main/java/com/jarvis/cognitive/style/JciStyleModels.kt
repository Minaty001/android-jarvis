package com.jarvis.cognitive.style

import com.jarvis.emotion.AssistantState

data class FormattedResponse(
    val spokenText: String,
    val displayText: String = spokenText,
    val targetEmotion: AssistantState,
    val isHonestConfirmation: Boolean = true
)

sealed class ProactiveTriggerEvent {
    data class BatteryLow(val percent: Int, val isCharging: Boolean) : ProactiveTriggerEvent()
    data class SevereWeather(val alertText: String) : ProactiveTriggerEvent()
    data class UpcomingReminder(val title: String, val minutesUntil: Int) : ProactiveTriggerEvent()
    data class CriticalSafety(val alertMessage: String) : ProactiveTriggerEvent()
}

data class ProactiveAlert(
    val message: String,
    val isCritical: Boolean,
    val emotion: AssistantState,
    val timestamp: Long = System.currentTimeMillis()
)
