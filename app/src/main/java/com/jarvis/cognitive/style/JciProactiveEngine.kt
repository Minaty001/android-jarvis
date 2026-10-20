package com.jarvis.cognitive.style

import com.jarvis.cognitive.CognitiveConfig
import com.jarvis.emotion.AssistantState
import java.util.Calendar

class JciProactiveEngine(
    var config: CognitiveConfig = CognitiveConfig(enabled = true, proactive = false),
    val minIntervalBetweenAlertsMs: Long = 3600_000L // 1 hour rate limit
) {
    private var lastAlertTimestamp: Long = 0L

    fun resetRateLimit() {
        lastAlertTimestamp = 0L
    }

    /**
     * Checks whether a proactive trigger event should fire an alert.
     * Enforces feature flag, quiet hours, rate limits, and critical safety bypass.
     */
    fun evaluateTrigger(
        event: ProactiveTriggerEvent,
        currentHourOverride: Int? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ProactiveAlert? {
        // 1. Feature Flag Invariant: Disabled by default
        if (!config.enabled || !config.proactive) {
            return null
        }

        val isCritical = event is ProactiveTriggerEvent.CriticalSafety

        // 2. Quiet Hours Gatekeeper (22:00 - 08:00)
        val hour = currentHourOverride ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isQuietHours = hour >= 22 || hour < 8
        if (isQuietHours && !isCritical) {
            // Suppressed during quiet hours unless critical safety
            return null
        }

        // 3. Rate Limit Gatekeeper (Max 1 alert per hour, unless critical safety)
        if (!isCritical && lastAlertTimestamp != 0L && currentTimeMs - lastAlertTimestamp < minIntervalBetweenAlertsMs) {
            return null
        }

        // 4. Evaluate Event
        val alert = when (event) {
            is ProactiveTriggerEvent.BatteryLow -> {
                if (event.percent < 15 && !event.isCharging) {
                    ProactiveAlert(
                        message = "Sir, the battery level has dropped to ${event.percent}%. I recommend connecting a power source.",
                        isCritical = false,
                        emotion = AssistantState.FOCUSED,
                        timestamp = currentTimeMs
                    )
                } else null
            }

            is ProactiveTriggerEvent.SevereWeather -> {
                ProactiveAlert(
                    message = "Severe weather advisory for your area, sir: ${event.alertText}",
                    isCritical = false,
                    emotion = AssistantState.FOCUSED,
                    timestamp = currentTimeMs
                )
            }

            is ProactiveTriggerEvent.UpcomingReminder -> {
                if (event.minutesUntil in 1..10) {
                    ProactiveAlert(
                        message = "A reminder is approaching in ${event.minutesUntil} minutes, sir: ${event.title}",
                        isCritical = false,
                        emotion = AssistantState.FOCUSED,
                        timestamp = currentTimeMs
                    )
                } else null
            }

            is ProactiveTriggerEvent.CriticalSafety -> {
                ProactiveAlert(
                    message = "Critical safety alert, sir: ${event.alertMessage}",
                    isCritical = true,
                    emotion = AssistantState.PANIC,
                    timestamp = currentTimeMs
                )
            }
        }

        if (alert != null) {
            lastAlertTimestamp = currentTimeMs
        }

        return alert
    }
}
