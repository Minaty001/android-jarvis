package com.jarvis.vision

import com.jarvis.accessibility.ScreenElement
import java.util.Locale

enum class PaymentStatus {
    SUCCESS,
    FAILED,
    PENDING,
    UNKNOWN
}

data class PaymentCheckResult(
    val status: PaymentStatus,
    val explanation: String,
    val transactionId: String? = null,
    val amount: String? = null
)

enum class LinkSafetyStatus {
    SECURE,
    UNSAFE,
    UNKNOWN
}

data class LinkSafetyResult(
    val status: LinkSafetyStatus,
    val reason: String,
    val url: String? = null
)

data class ButtonLocationResult(
    val found: Boolean,
    val index: Int? = null,
    val label: String? = null,
    val boundsDescription: String? = null,
    val isClickable: Boolean = false
)

object VisualQnaEngine {

    private val FAILURE_MARKERS = listOf(
        "payment failed", "failed", "declined", "unsuccessful", "cancelled",
        "rejected", "timed out", "error", "insufficient", "payment fail",
        "transaction failed", "payment unsuccessful", "unable to process"
    )

    private val PENDING_MARKERS = listOf(
        "processing payment", "processing", "please wait", "pending", "authorizing",
        "awaiting confirmation", "in progress", "verifying payment"
    )

    private val SUCCESS_MARKERS = listOf(
        "payment successful", "paid successfully", "payment received", "order placed",
        "transaction successful", "payment complete", "payment confirmed",
        "transferred successfully", "paid", "success"
    )

    private val UNSAFE_LINK_MARKERS = listOf(
        "not secure", "certificate invalid", "deceptive site", "phishing",
        "warning: potential security risk", "http://", "dangerous site", "untrusted"
    )

    private val SECURE_LINK_MARKERS = listOf(
        "https://", "secure connection", "verified", "connection is secure"
    )

    private val TXN_ID_REGEX = Regex("(?:txn|transaction|ref|reference|utr)(?:\\s*id|\\s*#|\\s*no)?[:\\s]*([a-z0-9_-]{6,30})", RegexOption.IGNORE_CASE)

    fun checkPaymentStatus(
        uiTree: String,
        elements: List<ScreenElement> = emptyList(),
        activePackage: String? = null
    ): PaymentCheckResult {
        val app = formatAppName(activePackage)
        val lowerText = uiTree.lowercase(Locale.ROOT)

        // 1. Prioritize failure markers FIRST to avoid false positives on words like "payment"
        val failedMatch = FAILURE_MARKERS.firstOrNull { lowerText.contains(it) }
        if (failedMatch != null) {
            val failureLine = uiTree.lines().firstOrNull { it.contains(failedMatch, ignoreCase = true) }?.trim()
            val reason = failureLine?.replace(Regex("\\[|\\]|\\(.*?\\)"), "")?.trim() ?: failedMatch
            return PaymentCheckResult(
                status = PaymentStatus.FAILED,
                explanation = "Payment failed or was declined on $app. Details: \"$reason\"."
            )
        }

        // 2. Check pending / processing
        val pendingMatch = PENDING_MARKERS.firstOrNull { lowerText.contains(it) }
        if (pendingMatch != null) {
            return PaymentCheckResult(
                status = PaymentStatus.PENDING,
                explanation = "Payment is currently processing or pending confirmation on $app. Please do not close or navigate away."
            )
        }

        // 3. Check success
        val successMatch = SUCCESS_MARKERS.firstOrNull { lowerText.contains(it) }
        if (successMatch != null) {
            val ocr = ScreenOcrParser.parse(uiTree)
            val amount = ocr.totals.firstOrNull() ?: ocr.prices.firstOrNull()
            val txnId = TXN_ID_REGEX.find(uiTree)?.groups?.get(1)?.value

            val explanation = buildString {
                append("Payment was successful on $app.")
                if (!amount.isNullOrBlank()) append(" Amount: $amount.")
                if (!txnId.isNullOrBlank()) append(" Transaction ID: $txnId.")
            }

            return PaymentCheckResult(
                status = PaymentStatus.SUCCESS,
                explanation = explanation,
                transactionId = txnId,
                amount = amount
            )
        }

        return PaymentCheckResult(
            status = PaymentStatus.UNKNOWN,
            explanation = "Could not verify payment status on $app. No clear payment confirmation or failure message was detected on screen."
        )
    }

    fun checkLinkSafety(
        uiTree: String,
        elements: List<ScreenElement> = emptyList()
    ): LinkSafetyResult {
        val lower = uiTree.lowercase(Locale.ROOT)

        val unsafeMatch = UNSAFE_LINK_MARKERS.firstOrNull { lower.contains(it) }
        if (unsafeMatch != null) {
            return LinkSafetyResult(
                status = LinkSafetyStatus.UNSAFE,
                reason = "Warning: this site or link is marked as unencrypted (HTTP) or potentially deceptive ($unsafeMatch)."
            )
        }

        val secureMatch = SECURE_LINK_MARKERS.firstOrNull { lower.contains(it) }
        if (secureMatch != null) {
            return LinkSafetyResult(
                status = LinkSafetyStatus.SECURE,
                reason = "Connection is encrypted via HTTPS with a secure connection."
            )
        }

        return LinkSafetyResult(
            status = LinkSafetyStatus.UNKNOWN,
            reason = "No URL or security certificate indicator was detected on the active screen."
        )
    }

    fun findButton(target: String, elements: List<ScreenElement>): ButtonLocationResult {
        val cleanTarget = target.lowercase(Locale.ROOT).trim()

        val match = elements.firstOrNull { el ->
            val textMatch = el.text?.lowercase(Locale.ROOT)?.contains(cleanTarget) == true
            val descMatch = el.contentDescription?.lowercase(Locale.ROOT)?.contains(cleanTarget) == true
            val idMatch = el.viewId?.lowercase(Locale.ROOT)?.contains(cleanTarget) == true
            textMatch || descMatch || idMatch
        }

        if (match != null) {
            val label = match.text ?: match.contentDescription ?: match.viewId ?: target
            val bounds = match.bounds
            return ButtonLocationResult(
                found = true,
                index = match.index,
                label = label,
                boundsDescription = "[${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]",
                isClickable = match.isClickable
            )
        }

        return ButtonLocationResult(
            found = false,
            index = null,
            label = null,
            boundsDescription = null,
            isClickable = false
        )
    }

    private fun formatAppName(pkg: String?): String {
        if (pkg == null) return "your screen"
        return when {
            pkg.contains("paisa") || pkg.contains("gpay") -> "Google Pay"
            pkg.contains("phonepe") -> "PhonePe"
            pkg.contains("paytm") -> "Paytm"
            pkg.contains("amazon") -> "Amazon"
            pkg.contains("chrome") -> "Chrome"
            else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }
}
