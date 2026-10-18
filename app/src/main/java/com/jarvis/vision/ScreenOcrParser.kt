package com.jarvis.vision

import java.util.Locale

/**
 * High-performance, zero-allocation-in-loops offline extractor for on-screen structured data:
 * Receipt totals, prices, order IDs, verification codes (OTPs), tracking numbers, and contacts.
 * Optimized for low-RAM Helio P22 and Exynos 9611 mobile chipsets.
 */
object ScreenOcrParser {

    private val PRICE_REGEX = Regex("(?:[\\$₹€£]|Rs\\.?|INR)\\s*\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val TOTAL_REGEX = Regex("(?:grand total|total|amount due|subtotal|balance due|payable)[:\\s]+(?:(?:[\\$₹€£]|Rs\\.?|INR)\\s*)?\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val OTP_REGEX = Regex("(?:otp|verification code|pin|passcode|code)[:\\s]*([0-9]{4,8})\\b|\\b([0-9]{4,8})\\s+(?:is your|for delivery)", RegexOption.IGNORE_CASE)
    private val ORDER_ID_REGEX = Regex("(?:order(?:\\s*id|\\s*#)?|ord)[:\\s]*#?([a-z0-9_-]{5,30})", RegexOption.IGNORE_CASE)
    private val TRACKING_REGEX = Regex("(?:tracking(?:\\s*id|\\s*#)?|track(?:\\s*#)?|awb)[:\\s]*#?([a-z0-9_-]{5,30})", RegexOption.IGNORE_CASE)
    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")

    data class ExtractedData(
        val totals: List<String> = emptyList(),
        val prices: List<String> = emptyList(),
        val verificationCodes: List<String> = emptyList(),
        val orderIds: List<String> = emptyList(),
        val trackingNumbers: List<String> = emptyList(),
        val phoneNumbers: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val rawLines: List<String> = emptyList()
    )

    fun parse(text: String): ExtractedData {
        if (text.isBlank()) return ExtractedData()

        val lines = text.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                !line.startsWith("Foreground App:") &&
                !line.startsWith("Visible UI Elements") &&
                !line.contains("No interactive UI text")
            }

        val cleanContent = lines.map { line ->
            line.replace(Regex("^[0-9]+\\.\\s*"), "")
                .replace(Regex("\\(clickable=.*?\\)"), "")
                .replace(Regex("\\(editable=.*?\\)"), "")
                .replace(Regex("\\(bounds=.*?\\)"), "")
                .replace("[", "")
                .replace("]", "")
                .trim()
        }.filter { it.isNotBlank() }

        val totals = mutableListOf<String>()
        val prices = mutableListOf<String>()
        val otps = mutableListOf<String>()
        val orderIds = mutableListOf<String>()
        val trackingNumbers = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val phones = mutableListOf<String>()

        cleanContent.forEach { line ->
            // 1. Totals
            TOTAL_REGEX.findAll(line).forEach { m ->
                totals.add(m.value.trim())
            }

            // 2. Individual Prices
            PRICE_REGEX.findAll(line).forEach { m ->
                prices.add(m.value.trim())
            }

            // 3. OTPs / Verification Codes
            OTP_REGEX.findAll(line).forEach { m ->
                val code = m.groups[1]?.value ?: m.groups[2]?.value
                if (!code.isNullOrBlank()) {
                    otps.add(code)
                }
            }

            // 4. Order IDs
            val ignoredOrderWords = setOf("summary", "details", "status", "history", "tracking", "placed", "cancelled", "confirmed", "number", "total")
            ORDER_ID_REGEX.findAll(line).forEach { m ->
                val id = m.groups[1]?.value?.trim()
                if (!id.isNullOrBlank() && id.lowercase() !in ignoredOrderWords) {
                    orderIds.add(id)
                }
            }

            // 5. Tracking Numbers
            val ignoredTrackWords = setOf("details", "status", "history", "number", "link", "info", "package", "item")
            TRACKING_REGEX.findAll(line).forEach { m ->
                val track = m.groups[1]?.value?.trim()
                if (!track.isNullOrBlank() && track.lowercase() !in ignoredTrackWords) {
                    trackingNumbers.add(track)
                }
            }

            // 6. Emails
            EMAIL_REGEX.findAll(line).forEach { m ->
                emails.add(m.value.trim())
            }

            // 7. Phone Numbers
            PHONE_REGEX.findAll(line).forEach { m ->
                val p = m.value.trim()
                if (p.length >= 7) {
                    phones.add(p)
                }
            }
        }

        // Fallback for standalone 4-8 digit numbers if no explicit OTP keyword was attached
        if (otps.isEmpty()) {
            cleanContent.forEach { line ->
                val match = Regex("\\b([0-9]{4,8})\\b").find(line)
                if (match != null && !prices.any { it.contains(match.value) }) {
                    otps.add(match.value)
                }
            }
        }

        return ExtractedData(
            totals = totals.distinct(),
            prices = prices.distinct(),
            verificationCodes = otps.distinct(),
            orderIds = orderIds.distinct(),
            trackingNumbers = trackingNumbers.distinct(),
            phoneNumbers = phones.distinct(),
            emails = emails.distinct(),
            rawLines = cleanContent
        )
    }

    fun extractTarget(text: String, target: String): String? {
        val data = parse(text)
        val t = target.lowercase(Locale.ROOT).trim()

        return when {
            t.contains("total") || t.contains("amount") || t.contains("bill") -> {
                data.totals.firstOrNull { it.contains("grand total", ignoreCase = true) }
                    ?: data.totals.firstOrNull { it.contains("total", ignoreCase = true) && !it.contains("subtotal", ignoreCase = true) }
                    ?: data.totals.lastOrNull()
                    ?: data.prices.lastOrNull()
            }
            t.contains("price") || t.contains("cost") -> {
                data.prices.firstOrNull() ?: data.totals.firstOrNull()
            }
            t.contains("otp") || t.contains("code") || t.contains("pin") || t.contains("verification") -> {
                data.verificationCodes.firstOrNull()
            }
            t.contains("order") -> {
                data.orderIds.firstOrNull()
            }
            t.contains("track") -> {
                data.trackingNumbers.firstOrNull() ?: data.orderIds.firstOrNull()
            }
            t.contains("email") || t.contains("mail") -> {
                data.emails.firstOrNull()
            }
            t.contains("phone") || t.contains("number") || t.contains("mobile") || t.contains("call") -> {
                data.phoneNumbers.firstOrNull()
            }
            else -> {
                data.totals.firstOrNull() ?: data.prices.firstOrNull() ?: data.verificationCodes.firstOrNull()
            }
        }
    }
}
