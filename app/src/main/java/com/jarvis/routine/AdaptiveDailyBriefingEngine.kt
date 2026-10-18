package com.jarvis.routine

import android.content.Context
import com.jarvis.calendar.CalendarManager
import com.jarvis.device.BatteryMonitor
import com.jarvis.notification.NotificationStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Generates an adaptive, executive daily briefing dynamically tailored
 * to the exact time of day (Morning, Midday, Evening, Bedtime).
 */
class AdaptiveDailyBriefingEngine(private val context: Context? = null) {

    enum class BriefingStyle {
        MORNING,
        MIDDAY,
        EVENING,
        BEDTIME
    }

    fun detectCurrentStyle(): BriefingStyle {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> BriefingStyle.MORNING
            in 12..16 -> BriefingStyle.MIDDAY
            in 17..21 -> BriefingStyle.EVENING
            else -> BriefingStyle.BEDTIME
        }
    }

    fun generateBriefing(forcedStyle: BriefingStyle? = null): String {
        val style = forcedStyle ?: detectCurrentStyle()
        val cal = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(cal.time)

        // 1. Battery status
        val battery = BatteryMonitor.getBatteryState(context)
        val batteryText = if (battery.percentage >= 0) {
            "Battery is at ${battery.percentage}%${if (battery.isCharging) " and charging" else ""}."
        } else ""

        // 2. Calendar Agenda
        val calendarManager = CalendarManager(context)
        val agenda = calendarManager.getTodayAgenda()
        val agendaSummary = if (agenda.isEmpty()) {
            "Calendar is clear."
        } else {
            val first = agenda.first()
            val firstTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(first.startTimeMillis))
            "${agenda.size} event${if (agenda.size > 1) "s" else ""} scheduled. Next is '${first.title}' at $firstTime."
        }

        // 3. Unread Notifications
        val unread = NotificationStore.getAll()
        val unreadCount = unread.size
        val notifSummary = when {
            unreadCount == 0 -> "No unread notifications."
            unreadCount == 1 -> "1 unread notification from ${unread.first().appName}."
            else -> "$unreadCount unread notifications waiting."
        }

        return when (style) {
            BriefingStyle.MORNING -> {
                buildString {
                    append("Good morning sir. It is $timeFmt on $dateFmt. ")
                    if (batteryText.isNotBlank()) append("$batteryText ")
                    append("$agendaSummary ")
                    append(notifSummary)
                }.trim()
            }
            BriefingStyle.MIDDAY -> {
                buildString {
                    append("Good afternoon sir. It is $timeFmt on $dateFmt. ")
                    append("$agendaSummary ")
                    if (batteryText.isNotBlank()) append("$batteryText ")
                    append(notifSummary)
                }.trim()
            }
            BriefingStyle.EVENING -> {
                buildString {
                    append("Good evening sir. It is $timeFmt on $dateFmt. ")
                    append("Evening summary: $agendaSummary ")
                    if (batteryText.isNotBlank()) append("$batteryText ")
                    append(notifSummary)
                }.trim()
            }
            BriefingStyle.BEDTIME -> {
                buildString {
                    append("Good night sir. It is $timeFmt on $dateFmt. ")
                    if (battery.percentage in 0..30 && !battery.isCharging) {
                        append("Warning: Battery is low at ${battery.percentage}%. Please plug in your charger. ")
                    } else if (batteryText.isNotBlank()) {
                        append("$batteryText ")
                    }
                    append("All systems resting. Have a restful night.")
                }.trim()
            }
        }
    }
}
