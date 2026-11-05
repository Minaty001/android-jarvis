package com.jarvis.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "JarvisNotificationListenerService connected and monitoring notifications")
        // Load initial active notifications
        try {
            activeNotifications?.forEach { sbn ->
                processStatusBarNotification(sbn)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading initial active notifications: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun triggerNotificationAction(index: Int, actionQuery: String? = null, replyText: String? = null): Boolean {
        try {
            val active = activeNotifications ?: return false
            val sbn = active.getOrNull(index - 1) ?: return false
            val notif = sbn.notification ?: return false
            val actions = notif.actions ?: return false
            val actionList = actions.filterNotNull()
            if (actionList.isEmpty()) return false

            val textToSend = replyText?.trim()?.ifBlank { null }
                ?: (if (actionList.any { !it.remoteInputs.isNullOrEmpty() } && !actionQuery.isNullOrBlank() &&
                    actionList.none { it.title?.toString()?.contains(actionQuery, ignoreCase = true) == true }) {
                    actionQuery.trim()
                } else null)

            // If we have text to send via direct reply, prefer action with RemoteInputs or titled "reply"
            val targetAction: Notification.Action = if (!textToSend.isNullOrBlank()) {
                actionList.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
                    ?: actionList.firstOrNull { it.title?.toString()?.contains("reply", ignoreCase = true) == true }
                    ?: actionList.first()
            } else if (!actionQuery.isNullOrBlank()) {
                val query = actionQuery.trim().lowercase(Locale.ROOT)
                actionList.firstOrNull { it.title?.toString()?.lowercase(Locale.ROOT)?.contains(query) == true }
                    ?: actionList.first()
            } else {
                actionList.first()
            }

            val remoteInputs = targetAction.remoteInputs
            if (!textToSend.isNullOrBlank() && !remoteInputs.isNullOrEmpty()) {
                val fillInIntent = Intent()
                val bundle = Bundle()
                for (input in remoteInputs) {
                    bundle.putCharSequence(input.resultKey, textToSend)
                }
                RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, bundle)
                targetAction.actionIntent.send(this, 0, fillInIntent)
                Log.i(TAG, "Successfully sent RemoteInput direct reply to notification $index: '$textToSend'")
                return true
            }

            targetAction.actionIntent.send()
            Log.i(TAG, "Triggered action '${targetAction.title}' on notification $index")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger notification action at index $index: ${e.message}")
            return false
        }
    }

    fun dismissNotification(index: Int): Boolean {
        try {
            val active = activeNotifications ?: return false
            val sbn = active.getOrNull(index - 1) ?: return false
            cancelNotification(sbn.key)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss notification at index $index: ${e.message}")
            return false
        }
    }

    fun dismissAllNotifications(): Boolean {
        try {
            cancelAllNotifications()
            NotificationStore.clearAll()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss all notifications: ${e.message}")
            return false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { processStatusBarNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val notifId = "${it.packageName}:${it.id}"
            NotificationStore.removeNotification(notifId)
            com.jarvis.messaging.UniversalMessagingEngine.instance?.removeReplyAction(notifId)
        }
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val pkg = sbn.packageName.orEmpty()
        // Strict read-only guarantee: Jarvis strictly monitors incoming notifications and never
        // dismisses, cancels, or alters notifications belonging to other applications.
        // Ignore Jarvis's own notifications to avoid recursion.
        if (pkg == packageName) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appLabel = runCatching {
            val pm = packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg.substringAfterLast("."))

        val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0

        val platform = when (pkg) {
            "com.whatsapp" -> com.jarvis.notification.WhatsAppPlatform.STANDARD
            "com.whatsapp.w4b" -> com.jarvis.notification.WhatsAppPlatform.BUSINESS
            else -> com.jarvis.notification.WhatsAppPlatform.UNKNOWN
        }

        val category = NotificationClassifier.classify(pkg, title.trim(), text.trim())
        val item = NotificationItem(
            id = "${pkg}:${sbn.id}",
            packageName = pkg,
            appName = appLabel,
            title = title.trim(),
            text = text.trim(),
            timestamp = sbn.postTime,
            isOngoing = isOngoing,
            platform = platform,
            category = category
        )

        NotificationStore.addNotification(item)
        Log.d(TAG, "Notification intercepted from $appLabel ($title): ${text.take(40)}")

        // Extract direct reply action if RemoteInput is attached
        val replyAction = notif.actions?.firstOrNull { action ->
            !action.remoteInputs.isNullOrEmpty()
        }
        val messagingEngine = com.jarvis.messaging.UniversalMessagingEngine.instance
            ?: com.jarvis.messaging.UniversalMessagingEngine(applicationContext)

        if (replyAction != null && title.isNotBlank()) {
            val notifId = "${pkg}:${sbn.id}"
            messagingEngine.registerReplyAction(notifId, title.trim(), replyAction)
        }

        // Evaluate contextual auto-reply
        messagingEngine.processIncomingNotification(sbn, item)

        if (pkg.contains("whatsapp", ignoreCase = true)) {
            val platformLabel = if (pkg == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp"
            com.jarvis.runtime.JarvisEventBus.shared.post(
                com.jarvis.runtime.JarvisEvent.WhatsAppNotification(
                    sender = title.trim(),
                    message = text.trim(),
                    isDirectReplyAvailable = replyAction != null,
                    platform = platformLabel
                )
            )
        }
    }

    companion object {
        private const val TAG = "JarvisNotifService"

        var instance: JarvisNotificationListenerService? = null
            internal set

        fun isNotificationAccessGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
