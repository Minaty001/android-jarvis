package com.jarvis.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.calendar.CalendarManager
import com.jarvis.autonomous.AmbientState
import com.jarvis.autonomous.AmbientContextEngine
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class ProactiveMessagingEngine(
    private val context: Context,
    private val ambientEngine: AmbientContextEngine,
    private val calendarManager: CalendarManager,
    private val messagingEngine: UniversalMessagingEngine
) {
    private val TAG = "ProactiveMessaging"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private var lastState = AmbientState.STANDBY

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Log.i(TAG, "Starting ProactiveMessagingEngine...")

        createNotificationChannel()

        // 1. Listen for Context Transitions
        scope.launch {
            lastState = ambientEngine.inferCurrentContext().state
            while (isRunning.get()) {
                val current = ambientEngine.inferCurrentContext().state
                if (current != lastState) {
                    handleContextTransition(lastState, current)
                    lastState = current
                }
                delay(10_000L) // Poll every 10s
            }
        }

        // 2. Monitor Calendar Events
        scope.launch {
            while (isRunning.get()) {
                checkUpcomingMeetings()
                delay(60_000L * 5) // Check every 5 mins
            }
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        scope.cancel()
        Log.i(TAG, "ProactiveMessagingEngine stopped.")
    }

    private fun handleContextTransition(from: AmbientState, to: AmbientState) {
        val draft = when {
            from == AmbientState.STANDBY && to == AmbientState.DRIVING ->
                "Started driving, on my way."
            from == AmbientState.DRIVING && to == AmbientState.STANDBY ->
                "Just arrived safely."
            to == AmbientState.NIGHT_WIND_DOWN && from != AmbientState.NIGHT_WIND_DOWN ->
                "Heading to sleep soon. Good night!"
            else -> null
        }

        if (draft != null) {
            Log.i(TAG, "Context transition ($from -> $to) triggered draft: $draft")
            suggestMessage(draft, "Status Update")
        }
    }

    private fun checkUpcomingMeetings() {
        val now = System.currentTimeMillis()
        val in15Mins = now + (15 * 60_000L)
        val upcoming = calendarManager.getEventsForRange(now, in15Mins)

        if (upcoming.isNotEmpty()) {
            val event = upcoming.first()
            val timeToStart = event.startTimeMillis - now

            // If event starts in < 15 mins AND we are driving
            if (timeToStart > 0 && lastState == AmbientState.DRIVING) {
                val draft = "I'm currently driving and might be slightly late for the ${event.title} meeting."
                Log.i(TAG, "Meeting proximity triggered draft: $draft")
                suggestMessage(draft, "Meeting Status Update")
            }
        }
    }

    private fun suggestMessage(draft: String, reason: String) {
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        // Default to a known contact if empty for demo/testing purposes
        val primaryContact = prefs.getString("primary_contact_name", "Spouse") ?: "Spouse"
        
        if (primaryContact.isBlank()) {
            Log.w(TAG, "No primary contact configured. Skipping proactive message: $draft")
            return
        }

        Log.i(TAG, "Suggesting proactive message to $primaryContact: $draft")
        sendSuggestionNotification(primaryContact, draft, reason)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Proactive Suggestions"
            val descriptionText = "Jarvis proactive message suggestions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("proactive_messages", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSuggestionNotification(contact: String, draft: String, title: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to send via SMS
        val smsIntent = Intent(context, ProactiveActionReceiver::class.java).apply {
            action = "ACTION_SEND_PROACTIVE_SMS"
            putExtra("contact", contact)
            putExtra("message", draft)
        }
        val smsPending = PendingIntent.getBroadcast(
            context, System.currentTimeMillis().toInt(), smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "proactive_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jarvis: $title")
            .setContentText("Send to $contact: \"$draft\"")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Send to $contact: \"$draft\""))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_send, "Send SMS", smsPending)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
