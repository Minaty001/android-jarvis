package com.jarvis.cognitive

import java.util.UUID

sealed class CognitiveEvent(
    val traceId: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    data class RequestReceived(
        val trace: String,
        val sanitizedUtterance: String
    ) : CognitiveEvent(trace)

    data class ModeSelected(
        val trace: String,
        val mode: CognitiveMode,
        val confidence: Float,
        val reason: String,
        val fastPath: Boolean
    ) : CognitiveEvent(trace)

    data class ShadowDecision(
        val trace: String,
        val proposedMode: CognitiveMode,
        val confidence: Float,
        val reason: String,
        val fastPath: Boolean,
        val evaluationTimeMs: Long
    ) : CognitiveEvent(trace)

    data class ToolInvoked(
        val trace: String,
        val capabilityId: String,
        val argsHash: String
    ) : CognitiveEvent(trace)

    data class ToolResultObserved(
        val trace: String,
        val capabilityId: String,
        val success: Boolean,
        val evidenceLevel: String
    ) : CognitiveEvent(trace)

    data class ExecutionCompleted(
        val trace: String,
        val success: Boolean,
        val durationMs: Long
    ) : CognitiveEvent(trace)

    data class OrchestratorFallback(
        val trace: String,
        val error: String,
        val fallbackReason: String
    ) : CognitiveEvent(trace)

    companion object {
        fun newTraceId(): String = "trace_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        fun redactSensitiveData(input: String): String {
            if (input.isBlank()) return input
            var redacted = input
            // Redact PIN / password occurrences (supporting "pin is 1234", "password: xyz", "passcode = 1234")
            redacted = redacted.replace(Regex("(?i)\\b(pin|password|passcode)\\s*(?:is|[:=])?\\s*([A-Za-z0-9@#\$%^&*]+)"), "$1: [REDACTED]")
            // Redact 4 to 8 digit standalone numeric PINs following unlock/pin keywords
            redacted = redacted.replace(Regex("(?i)\\b(pin|code)\\s+(?:is\\s+)?(\\d{4,8})\\b"), "$1 [REDACTED]")
            // Redact 6 digit OTP codes
            redacted = redacted.replace(Regex("(?i)\\b(otp|verification code)\\s*(?:is|:)?\\s*(\\d{4,8})\\b"), "$1 [REDACTED]")
            // Redact 13-16 digit credit card patterns
            redacted = redacted.replace(Regex("\\b(?:\\d[ -]*?){13,16}\\b"), "[CARD_REDACTED]")
            return redacted
        }
    }
}
