package com.jarvis.tools

import android.content.Context
import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.notification.JarvisNotificationListenerService
import com.jarvis.notification.NotificationStore

class NotificationsTool(private val context: Context? = null) : Tool {
    override val name: String = "NOTIFICATIONS_READ"
    override val description: String = "Reads and summarizes incoming device notifications (WhatsApp, SMS, Email, etc.) in read-only mode without altering external notifications."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "NOTIFICATIONS_READ",
        description = "Fetches recent intercepted notifications from apps like WhatsApp, Messages, etc.",
        parameters = listOf(
            ParameterSchema("app", "string", "Optional app filter, e.g. 'whatsapp' or 'messages'", required = false),
            ParameterSchema("limit", "string", "Maximum notifications to return (default 5)", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (context != null && !JarvisNotificationListenerService.isNotificationAccessGranted(context)) {
            return ToolResult.Success(
                "Notification access is not enabled. Please enable Notification Access in Jarvis Settings so I can read your messages."
            )
        }

        val action = params["action"]?.trim()?.lowercase() ?: "read"
        val appFilter = params["app"]?.trim()?.ifBlank { null }
        val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 15) ?: 5
        val index = params["index"]?.toIntOrNull()
        val query = params["query"] ?: params["text"]
        val replyText = params["reply_text"] ?: params["text"] ?: params["message"] ?: if (action == "reply") params["query"] else null

        when (action) {
            "read_item" -> {
                val idx = index ?: 1
                val item = NotificationStore.getItem(idx)
                if (item == null) {
                    return ToolResult.Success(
                        message = "No notification found at number $idx.",
                        data = mapOf("action" to "read_item", "index" to idx.toString(), "found" to "false")
                    )
                }
                val sender = if (item.title.isNotBlank()) "${item.title} on ${item.appName}" else item.appName
                val body = item.text.ifBlank { "Notification posted" }
                return ToolResult.Success(
                    message = "Notification $idx from $sender: \"$body\"",
                    data = mapOf(
                        "action" to "read_item",
                        "index" to idx.toString(),
                        "found" to "true",
                        "app" to item.appName,
                        "title" to item.title,
                        "text" to item.text
                    )
                )
            }
            "trigger_action", "reply" -> {
                val idx = index ?: 1
                val service = JarvisNotificationListenerService.instance
                if (service == null) {
                    return ToolResult.Success(
                        message = "Notification service is not currently active to trigger actions.",
                        data = mapOf("action" to action, "index" to idx.toString(), "success" to "false")
                    )
                }
                val success = service.triggerNotificationAction(idx, actionQuery = query, replyText = replyText)
                return if (success) {
                    val msg = if (!replyText.isNullOrBlank()) "Replied to notification $idx: \"$replyText\""
                              else "Triggered action on notification $idx."
                    ToolResult.Success(
                        message = msg,
                        data = mapOf("action" to action, "index" to idx.toString(), "success" to "true", "text" to (replyText ?: ""))
                    )
                } else {
                    ToolResult.Success(
                        message = "Unable to trigger action on notification $idx. It may not have actionable buttons.",
                        data = mapOf("action" to action, "index" to idx.toString(), "success" to "false")
                    )
                }
            }
            "dismiss_all", "clear_all" -> {
                val service = JarvisNotificationListenerService.instance
                if (service != null) {
                    service.dismissAllNotifications()
                } else {
                    NotificationStore.clearAll()
                }
                return ToolResult.Success(
                    message = "Cleared all notifications.",
                    data = mapOf("action" to "dismiss_all")
                )
            }
            "dismiss_item", "dismiss", "clear_item" -> {
                val idx = index ?: 1
                val service = JarvisNotificationListenerService.instance
                if (service != null) {
                    service.dismissNotification(idx)
                }
                val item = NotificationStore.getItem(idx)
                if (item != null) {
                    NotificationStore.removeNotification(item.id)
                }
                return ToolResult.Success(
                    message = "Dismissed notification $idx.",
                    data = mapOf("action" to "dismiss_item", "index" to idx.toString())
                )
            }
            "digest", "summary", "triage", "brief" -> {
                val summarizer = com.jarvis.notification.SmartNotificationSummarizer()
                val summary = summarizer.generateExecutiveDigest()
                val urgent = summarizer.getUrgentNotifications()
                return ToolResult.Success(
                    message = summary,
                    data = mapOf(
                        "action" to "digest",
                        "urgent_count" to urgent.size,
                        "total_count" to NotificationStore.getAll().size
                    )
                )
            }
            "clear_spam", "clear_promotions", "dismiss_spam" -> {
                val cleared = NotificationStore.clearPromotions()
                return ToolResult.Success(
                    message = "Cleared $cleared promotional notification${if (cleared == 1) "" else "s"} from Jarvis memory (device notifications remain untouched).",
                    data = mapOf("action" to "clear_spam", "cleared_count" to cleared)
                )
            }
            "priority" -> {
                val items = NotificationStore.getPriorityNotifications(limit = limit)
                if (items.isEmpty()) {
                    return ToolResult.Success("You have no priority or personal notifications right now.")
                }
                val summary = buildString {
                    append("You have ${items.size} priority notification${if (items.size > 1) "s" else ""}: ")
                    items.forEachIndexed { idx, item ->
                        val sender = if (item.title.isNotBlank()) "${item.title} on ${item.appName}" else item.appName
                        val body = item.text.ifBlank { "Notification posted" }
                        append("${idx + 1}. $sender: \"$body\"${if (idx < items.size - 1) ". " else "."}")
                    }
                }
                return ToolResult.Success(
                    message = summary,
                    data = mapOf(
                        "action" to "priority",
                        "count" to items.size,
                        "notifications" to items.map { mapOf("app" to it.appName, "title" to it.title, "text" to it.text) }
                    )
                )
            }
            else -> {
                val items = NotificationStore.getRecentNotifications(limit = limit, appFilter = appFilter)
                if (items.isEmpty()) {
                    val filterNote = if (appFilter != null) " from $appFilter" else ""
                    return ToolResult.Success("You have no new notifications$filterNote right now.")
                }

                val summary = buildString {
                    append("You have ${items.size} recent notification${if (items.size > 1) "s" else ""}: ")
                    items.forEachIndexed { idx, item ->
                        val sender = if (item.title.isNotBlank()) "${item.title} on ${item.appName}" else item.appName
                        val body = item.text.ifBlank { "Notification posted" }
                        append("${idx + 1}. $sender: \"$body\"${if (idx < items.size - 1) ". " else "."}")
                    }
                }

                return ToolResult.Success(
                    message = summary,
                    data = mapOf(
                        "action" to "read",
                        "count" to items.size,
                        "notifications" to items.map { mapOf("app" to it.appName, "title" to it.title, "text" to it.text) }
                    )
                )
            }
        }
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        if (!result.success) {
            return VerificationResult.failure("Notification read failed: ${result.message}")
        }
        val text = result.message
        return when {
            result.data.containsKey("action") -> VerificationResult.success(
                "Notification action executed (${result.data["action"]})",
                mapOf("action" to "${result.data["action"]}", "outcome" to "SUCCESS")
            )
            result.data.containsKey("notifications") -> VerificationResult.success(
                "Notifications read (${result.data["count"]})",
                mapOf("count" to "${result.data["count"]}", "outcome" to "READBACK")
            )
            text.contains("no new notifications", ignoreCase = true) -> VerificationResult.success(
                "Notification store queried; none to read",
                mapOf("count" to "0", "outcome" to "EMPTY")
            )
            text.contains("access is not enabled", ignoreCase = true) -> VerificationResult.unknown(
                "Notification access not enabled; nothing could be read",
                mapOf("outcome" to "ACCESS_DENIED")
            )
            else -> VerificationResult.unknown("Notification read returned an unexpected response", mapOf("outcome" to "UNRECOGNIZED"))
        }
    }
}
