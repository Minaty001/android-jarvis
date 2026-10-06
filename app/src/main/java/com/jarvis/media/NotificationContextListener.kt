package com.jarvis.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.util.Log

class NotificationContextListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        
        // Track WhatsApp incoming messages
        if (packageName.contains("whatsapp", ignoreCase = true)) {
            Log.d("NotificationContext", "WhatsApp notification from: $title")
            WhatsAppSessionManager.recordChat(
                contactName = title,
                message = text,
                isOutbound = false
            )
        }
        
        // Could also add Telegram, Instagram direct, etc. here in the future
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not currently tracking removed notifications
    }
}
