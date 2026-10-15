package com.jarvis.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jarvis.service.JarvisForegroundService

/**
 * Central coordinator for all of Jarvis's own notifications.
 * Manages notification channels, builds ongoing service notifications with actionable buttons,
 * throttles rapid state changes to prevent notification shade flicker,
 * and strictly isolates Jarvis notifications so third-party notifications are never touched.
 */
object JarvisNotificationManager {
    const val CHANNEL_SERVICE = "jarvis_service_channel"
    const val CHANNEL_REMINDERS = "jarvis_reminders"
    const val CHANNEL_ALERTS = "jarvis_proactive_channel"
    const val CHANNEL_BRIEFINGS = "jarvis_briefing_channel"
    const val CHANNEL_OVERLAY = "jarvis_overlay_channel"

    const val NOTIFICATION_ID_SERVICE = 1001

    private const val MIN_UPDATE_INTERVAL_MS = 500L
    @Volatile private var lastUpdatedContent: String = ""
    @Volatile private var lastUpdateTimeMs: Long = 0L

    @Synchronized
    fun shouldUpdateNotification(newContent: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (newContent == lastUpdatedContent) return false
        if (currentTimeMs - lastUpdateTimeMs < MIN_UPDATE_INTERVAL_MS) return false
        lastUpdatedContent = newContent
        lastUpdateTimeMs = currentTimeMs
        return true
    }

    @Synchronized
    fun resetThrottlerForTesting() {
        lastUpdatedContent = ""
        lastUpdateTimeMs = 0L
    }

    fun isJarvisNotification(packageName: String, notificationId: Int): Boolean {
        return packageName == "com.jarvis"
    }

    fun initializeChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Service & Background Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Jarvis wake word and background service active"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timed reminders, alarms, and scheduled tasks"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_ALERTS,
                "Proactive Alerts & Health",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Battery warnings, contextual alerts, and system health suggestions"
            },
            NotificationChannel(
                CHANNEL_BRIEFINGS,
                "Daily Briefings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Morning and evening executive digests"
            },
            NotificationChannel(
                CHANNEL_OVERLAY,
                "Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating assistant orb indicator"
                setShowBadge(false)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun buildServiceNotification(
        context: Context,
        content: String
    ): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Talk / Manual Listening Trigger
        val talkIntent = Intent(context, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_START_WITH_WAKE
        }
        val talkPendingIntent = PendingIntent.getService(
            context,
            1,
            talkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Sleep / Stop Engine
        val stopIntent = Intent(context, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_btn_speak_now, "🎙 Talk", talkPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "🛑 Sleep", stopPendingIntent)
            .build()
    }

    /**
     * Direct navigation to Android system notification settings for Jarvis or a specific channel.
     */
    fun openNotificationChannelSettings(context: Context, channelId: String? = null) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId != null) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
