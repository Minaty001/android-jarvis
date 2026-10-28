package com.jarvis.telecom

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi

data class CallSpamScore(
    val isSpam: Boolean,
    val confidence: Float,
    val reason: String
)

class CallSpamScorer {
    fun scoreCall(rawNumber: String, isContactKnown: Boolean): CallSpamScore {
        if (isContactKnown) {
            return CallSpamScore(
                isSpam = false,
                confidence = 0.99f,
                reason = "Caller is in user contacts"
            )
        }

        val clean = rawNumber.filter { it.isDigit() }
        if (clean.isBlank()) {
            return CallSpamScore(
                isSpam = false,
                confidence = 0.5f,
                reason = "Unknown or private caller number"
            )
        }

        if (clean.startsWith("1800") && clean.length >= 10) {
            return CallSpamScore(
                isSpam = false,
                confidence = 0.85f,
                reason = "Recognized toll-free service number"
            )
        }

        if (clean.startsWith("140") && clean.length == 10) {
            return CallSpamScore(
                isSpam = true,
                confidence = 0.95f,
                reason = "Matches 140 telemarketing prefix regulation"
            )
        }

        if (clean.length == 10 && clean.toSet().size <= 2) {
            return CallSpamScore(
                isSpam = true,
                confidence = 0.92f,
                reason = "Repetitive spoofed digit pattern detected"
            )
        }

        return CallSpamScore(
            isSpam = false,
            confidence = 0.60f,
            reason = "Standard non-contact incoming number"
        )
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
class JarvisCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "JarvisCallScreening"
        @Volatile
        var isSpamFilteringEnabled: Boolean = true
    }

    private val spamScorer = CallSpamScorer()

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart.orEmpty().trim()
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        Log.i(TAG, "Screening call: number='$rawNumber', isIncoming=$isIncoming")

        if (!isIncoming) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val contactResolver = ContactResolver(applicationContext)
        val contact = if (rawNumber.isNotBlank()) contactResolver.resolveContact(rawNumber) else null
        val callerName = contact?.name ?: rawNumber.ifBlank { "Unknown Caller" }

        val spamScore = spamScorer.scoreCall(rawNumber, isContactKnown = contact != null)
        val isSpam = isSpamFilteringEnabled && spamScore.isSpam

        val responseBuilder = CallResponse.Builder()

        if (isSpam) {
            Log.w(TAG, "Detected SPAM call from: $rawNumber (${spamScore.reason}, confidence=${spamScore.confidence}). Silencing and rejecting.")
            responseBuilder
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
        } else {
            responseBuilder
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipNotification(false)
        }

        respondToCall(callDetails, responseBuilder.build())

        // Notify Jarvis control plane & CallAndSmsAgent
        val agent = CallAndSmsAgent.instance
        if (!isSpam && rawNumber.isNotBlank()) {
            agent?.onIncomingCallRinging(rawNumber) { announcement ->
                Log.i(TAG, "Call screening announcement ready: $announcement")
            }
        }
    }

    private fun isKnownSpamPattern(number: String): Boolean {
        return spamScorer.scoreCall(number, isContactKnown = false).isSpam
    }
}
