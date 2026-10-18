package com.jarvis.sound

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.tools.TelephonyTool

/**
 * SoundAlertDispatcher
 *
 * Dispatches multi-modal alerts when a safety sound is detected:
 * 1. Spoken TTS audio announcement via earbuds/speaker.
 * 2. Distinctive haptic vibration patterns per category.
 * 3. High-priority heads-up Android notification.
 * 4. Automated emergency SMS for critical alerts (e.g. Smoke Alarm).
 */
object SoundAlertDispatcher {
    private const val TAG = "SoundAlertDispatcher"
    const val CHANNEL_ID = "jarvis_safety_sound_alerts"
    const val NOTIFICATION_ID = 9119

    fun formatAlertAnnouncement(category: SoundCategory): String {
        return when (category) {
            SoundCategory.SMOKE_ALARM -> "Warning: Smoke alarm detected! Check your surroundings immediately."
            SoundCategory.BABY_CRYING -> "Alert: Baby crying detected."
            SoundCategory.DOORBELL -> "Notice: Doorbell or chime sound detected."
            SoundCategory.GLASS_BREAK -> "Alert: Glass breaking sound detected!"
            SoundCategory.CAR_HONK -> "Notice: Car horn detected."
            SoundCategory.NONE -> ""
        }
    }

    fun getVibrationPattern(category: SoundCategory): LongArray {
        return when (category) {
            SoundCategory.SMOKE_ALARM -> longArrayOf(0L, 500L, 200L, 500L, 200L, 500L, 400L)
            SoundCategory.BABY_CRYING -> longArrayOf(0L, 250L, 150L, 250L, 150L, 250L)
            SoundCategory.GLASS_BREAK -> longArrayOf(0L, 400L, 100L, 400L)
            SoundCategory.CAR_HONK -> longArrayOf(0L, 700L)
            SoundCategory.DOORBELL -> longArrayOf(0L, 200L, 200L, 200L)
            SoundCategory.NONE -> longArrayOf()
        }
    }

    suspend fun dispatchAlert(
        context: Context?,
        event: SoundDetectionEvent,
        emergencyContact: String? = null,
        ttsSpeaker: ((String) -> Unit)? = null
    ): Boolean {
        val announcement = formatAlertAnnouncement(event.category)
        if (announcement.isEmpty()) return false

        // 1. Spoken TTS announcement
        try {
            if (ttsSpeaker != null) {
                ttsSpeaker.invoke(announcement)
            } else if (context != null) {
                val serviceTts = try {
                    com.jarvis.service.JarvisForegroundService.instance?.voiceEngine?.ttsEngine
                } catch (_: Throwable) {
                    null
                }
                if (serviceTts != null) {
                    serviceTts.speak(announcement)
                } else {
                    try {
                        com.jarvis.voice.AndroidTtsEngine(context).speak(announcement)
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spoken TTS announcement failed: ${e.message}")
        }

        if (context == null) {
            Log.i(TAG, "Dispatched alert [Simulated]: $announcement")
            return true
        }

        // 2. Distinctive Haptic Feedback
        try {
            val pattern = getVibrationPattern(event.category)
            if (pattern.isNotEmpty()) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }

        // 3. High-Priority Heads-Up Notification (category-specific ID avoids overwriting different alerts)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Safety Sound Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Immediate alerts for detected ambient safety sounds"
                        enableVibration(true)
                    }
                    nm.createNotificationChannel(channel)
                }

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Safety Alert: ${event.category.name.replace('_', ' ')}")
                    .setContentText(announcement)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .build()

                nm.notify(NOTIFICATION_ID + event.category.ordinal, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification dispatch failed: ${e.message}")
        }

        // 4. Automated SMS Alert for life-critical alarms (Smoke Alarm)
        if (event.category == SoundCategory.SMOKE_ALARM && !emergencyContact.isNullOrBlank()) {
            try {
                val telephonyTool = TelephonyTool(context)
                val smsText = "JARVIS SAFETY ALERT: Smoke alarm detected at this device's location. Please check immediately!"
                telephonyTool.execute(
                    mapOf(
                        "action" to "sms_send",
                        "recipient" to emergencyContact,
                        "message" to smsText
                    )
                )
                Log.i(TAG, "Emergency SMS dispatched to $emergencyContact for SMOKE_ALARM")
            } catch (e: Exception) {
                Log.e(TAG, "Emergency SMS dispatch failed: ${e.message}")
            }
        }

        return true
    }
}
