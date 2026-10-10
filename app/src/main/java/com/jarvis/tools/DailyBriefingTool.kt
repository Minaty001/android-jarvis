package com.jarvis.tools

import android.content.Context
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.calendar.CalendarManager
import com.jarvis.device.BatteryMonitor
import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.notification.NotificationStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyBriefingTool(private val context: Context? = null) : Tool {
    override val name: String = "DAILY_BRIEFING"
    override val description: String = "Provides a complete daily executive briefing including date, time, battery, calendar agenda, unread messages, and system status."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "DAILY_BRIEFING",
        description = "Executive briefing covering date, time, battery, calendar agenda, unread messages, and active app.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val adaptiveEngine = com.jarvis.routine.AdaptiveDailyBriefingEngine(context)
        val styleParam = params["style"]?.trim()?.uppercase()
        val forcedStyle = when (styleParam) {
            "MORNING" -> com.jarvis.routine.AdaptiveDailyBriefingEngine.BriefingStyle.MORNING
            "MIDDAY" -> com.jarvis.routine.AdaptiveDailyBriefingEngine.BriefingStyle.MIDDAY
            "EVENING" -> com.jarvis.routine.AdaptiveDailyBriefingEngine.BriefingStyle.EVENING
            "BEDTIME" -> com.jarvis.routine.AdaptiveDailyBriefingEngine.BriefingStyle.BEDTIME
            else -> null
        }

        val briefing = adaptiveEngine.generateBriefing(forcedStyle)
        val cal = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

        return ToolResult.Success(
            message = briefing,
            data = mapOf(
                "date" to dateFormat.format(cal.time),
                "time" to timeFormat.format(cal.time),
                "style" to (forcedStyle?.name ?: adaptiveEngine.detectCurrentStyle().name)
            )
        )
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        if (!result.success) {
            return VerificationResult.failure("Briefing generation failed: ${result.message}")
        }
        return if (result.data.containsKey("time")) {
            VerificationResult.success("Briefing report generated", mapOf("outcome" to "REPORT_GENERATED"))
        } else {
            VerificationResult.unknown("Briefing generated but report data not populated", mapOf("outcome" to "UNPARSED"))
        }
    }
}
