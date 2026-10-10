package com.jarvis.finance

import android.util.Log
import java.util.Locale

/**
 * Automated Expense Splitter & Receipt Parser Engine.
 * Calculates bill shares, applies custom tips or taxes, and prepares
 * a clean executive breakdown suitable for payment requests.
 */
class ExpenseSplitterEngine {

    private val TAG = "ExpenseSplitterEngine"

    data class ParticipantShare(
        val name: String,
        val shareAmount: Double
    )

    data class BillSplitResult(
        val totalAmount: Double,
        val tipAmount: Double,
        val grandTotal: Double,
        val participantShares: List<ParticipantShare>,
        val perPersonRounded: Double,
        val summaryText: String
    )

    fun splitBill(
        baseAmount: Double,
        participants: List<String>,
        tipPercent: Double = 0.0
    ): BillSplitResult {
        val count = if (participants.isEmpty()) 1 else participants.size
        val tip = (baseAmount * (tipPercent / 100.0))
        val grandTotal = baseAmount + tip
        val sharePerPerson = grandTotal / count

        val shares = participants.map { name ->
            ParticipantShare(name = name.trim(), shareAmount = sharePerPerson)
        }

        val roundedShare = Math.round(sharePerPerson * 100.0) / 100.0

        val formattedShares = shares.joinToString("\n") { "• ${it.name}: $${String.format(Locale.US, "%.2f", it.shareAmount)}" }
        val summary = "Expense Split (${shares.size} people):\n" +
                "Base: $${String.format(Locale.US, "%.2f", baseAmount)}, Tip ($tipPercent%): $${String.format(Locale.US, "%.2f", tip)}\n" +
                "Total: $${String.format(Locale.US, "%.2f", grandTotal)}\n" +
                "Share each: $${String.format(Locale.US, "%.2f", roundedShare)}\n$formattedShares"

        Log.i(TAG, "Bill split computed: total=$grandTotal, people=$count")

        return BillSplitResult(
            totalAmount = baseAmount,
            tipAmount = tip,
            grandTotal = grandTotal,
            participantShares = shares,
            perPersonRounded = roundedShare,
            summaryText = summary
        )
    }
}
