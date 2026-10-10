package com.jarvis.notification

import android.util.Log

/**
 * Smart Notification Summarizer.
 * Groups incoming notifications into distinct contextual buckets, filters trivial noise,
 * pinpoints high-urgency notifications (e.g. OTPs, bank transactions, delivery alerts),
 * and generates clean executive digests.
 */
class SmartNotificationSummarizer {

    private val TAG = "SmartNotifSummarizer"

    enum class SemanticBucket {
        FINANCIAL,
        SECURITY_OTP,
        DELIVERY,
        COMMUNICATION,
        GENERAL
    }

    data class ClassifiedNotification(
        val item: NotificationItem,
        val bucket: SemanticBucket,
        val isUrgent: Boolean
    )

    fun classify(item: NotificationItem): ClassifiedNotification {
        val combined = "${item.title} ${item.text}".lowercase()

        val isOtp = combined.contains("otp") ||
                combined.contains("verification code") ||
                combined.contains("security code") ||
                combined.contains("one time password")

        val isFinancial = combined.contains("debited") ||
                combined.contains("credited") ||
                combined.contains("bank") ||
                combined.contains("inr") ||
                combined.contains("usd") ||
                combined.contains("account balance")

        val isDelivery = combined.contains("delivery") ||
                combined.contains("out for delivery") ||
                combined.contains("arriving") ||
                combined.contains("driver") ||
                combined.contains("order placed")

        val isCommunication = combined.contains("message") ||
                item.packageName.contains("whatsapp") ||
                item.packageName.contains("telegram") ||
                item.packageName.contains("mms") ||
                item.packageName.contains("messaging")

        val bucket = when {
            isOtp -> SemanticBucket.SECURITY_OTP
            isFinancial -> SemanticBucket.FINANCIAL
            isDelivery -> SemanticBucket.DELIVERY
            isCommunication -> SemanticBucket.COMMUNICATION
            else -> SemanticBucket.GENERAL
        }

        val isUrgent = isOtp || isFinancial || isDelivery

        return ClassifiedNotification(item, bucket, isUrgent)
    }

    fun generateExecutiveDigest(): String {
        val active: List<NotificationItem> = NotificationStore.getAll()
        if (active.isEmpty()) {
            return "No notifications to summarize."
        }

        val classified: List<ClassifiedNotification> = active.map { classify(it) }
        val urgent: List<ClassifiedNotification> = classified.filter { it.isUrgent }
        val normal: List<ClassifiedNotification> = classified.filter { !it.isUrgent }

        return buildString {
            if (urgent.isNotEmpty()) {
                append("Attention sir: ${urgent.size} urgent alert${if (urgent.size > 1) "s" else ""}. ")
                urgent.take(2).forEach { alert ->
                    append("[${alert.bucket} from ${alert.item.appName}]: ${alert.item.title.ifBlank { alert.item.text }}. ")
                }
            }

            val appCounts: Map<String, Int> = normal.groupBy { it.item.appName }.mapValues { it.value.size }
            if (appCounts.isNotEmpty()) {
                val summaryParts = appCounts.entries.take(3).map { "${it.value} from ${it.key}" }
                append("Also, ${normal.size} general notification${if (normal.size > 1) "s" else ""}: ${summaryParts.joinToString(", ")}.")
            }
        }.trim()
    }

    fun getUrgentNotifications(): List<NotificationItem> {
        return NotificationStore.getAll().filter { classify(it).isUrgent }
    }
}
