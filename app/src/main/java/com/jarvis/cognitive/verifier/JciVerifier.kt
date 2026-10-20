package com.jarvis.cognitive.verifier

import com.jarvis.cognitive.capability.JciCapability
import com.jarvis.cognitive.capability.JciEvidenceLevel

enum class JciVerificationStatus {
    VERIFIED,           // Meets expected evidence level, success confirmed
    REJECTED,           // Execution failed or observed state conflicted
    SUBMITTED_ONLY,     // Level 4: dispatched/queued, cannot report "done"
    UNVERIFIED          // Level 5: state check missing or unobservable
}

data class JciVerificationResult(
    val status: JciVerificationStatus,
    val attainedEvidenceLevel: JciEvidenceLevel,
    val expectedEvidenceLevel: JciEvidenceLevel,
    val canReportDone: Boolean,
    val message: String,
    val evidence: Map<String, Any> = emptyMap()
)

class JciVerifier {

    /**
     * Strictly verifies execution outcome against the capability's expected evidence level.
     * Prevents false success reporting by requiring real evidence before allowing "done".
     */
    fun verifyStep(
        capability: JciCapability,
        params: Map<String, String> = emptyMap(),
        toolSuccess: Boolean,
        toolMessage: String = "",
        observedState: Map<String, Any>? = null,
        expectedStatePredicate: ((Map<String, Any>) -> Boolean)? = null,
        userConfirmed: Boolean = false
    ): JciVerificationResult {
        val expected = capability.expectedEvidenceLevel

        // 1. Tool execution failure is immediately rejected
        if (!toolSuccess) {
            return JciVerificationResult(
                status = JciVerificationStatus.REJECTED,
                attainedEvidenceLevel = JciEvidenceLevel.UNVERIFIABLE,
                expectedEvidenceLevel = expected,
                canReportDone = false,
                message = "Tool execution failed: $toolMessage",
                evidence = mapOf("toolSuccess" to false, "message" to toolMessage)
            )
        }

        // 2. Level 4: Submission Acknowledgment (e.g. app close, TTS speech, UI clicks)
        // Hard Rule 7 & Section 6.3: Submission ack can report "sent/submitted", NEVER "done"
        if (expected == JciEvidenceLevel.SUBMISSION_ACK) {
            return JciVerificationResult(
                status = JciVerificationStatus.SUBMITTED_ONLY,
                attainedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
                expectedEvidenceLevel = expected,
                canReportDone = false,
                message = "Action dispatched to subsystem. Outcome cannot be independently verified.",
                evidence = mapOf("action" to capability.id, "dispatched" to true)
            )
        }

        // 3. Level 3: User Confirmation Required (high/critical risk actions)
        if (expected == JciEvidenceLevel.USER_CONFIRMED) {
            if (!userConfirmed) {
                return JciVerificationResult(
                    status = JciVerificationStatus.UNVERIFIED,
                    attainedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
                    expectedEvidenceLevel = expected,
                    canReportDone = false,
                    message = "Action '${capability.id}' requires explicit user confirmation to verify.",
                    evidence = mapOf("userConfirmed" to false)
                )
            }
            return JciVerificationResult(
                status = JciVerificationStatus.VERIFIED,
                attainedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
                expectedEvidenceLevel = expected,
                canReportDone = true,
                message = "Action confirmed by user.",
                evidence = mapOf("userConfirmed" to true)
            )
        }

        // 4. Level 2: Observable State Verification (e.g. flashlight, volume, wifi, file write)
        if (expected == JciEvidenceLevel.STATE_OBSERVED) {
            if (observedState == null || observedState.isEmpty()) {
                return JciVerificationResult(
                    status = JciVerificationStatus.UNVERIFIED,
                    attainedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
                    expectedEvidenceLevel = expected,
                    canReportDone = false,
                    message = "Expected state change could not be verified (no observation available).",
                    evidence = mapOf("observedStateMissing" to true)
                )
            }

            val predicate = expectedStatePredicate ?: deriveDefaultPredicate(capability, params)
            if (predicate != null) {
                val matches = predicate(observedState)
                if (!matches) {
                    return JciVerificationResult(
                        status = JciVerificationStatus.REJECTED,
                        attainedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
                        expectedEvidenceLevel = expected,
                        canReportDone = false,
                        message = "Observed real-world state does not match expected target state.",
                        evidence = observedState
                    )
                }
            }

            return JciVerificationResult(
                status = JciVerificationStatus.VERIFIED,
                attainedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
                expectedEvidenceLevel = expected,
                canReportDone = true,
                message = "State change successfully observed and verified.",
                evidence = observedState
            )
        }

        // 5. Level 1: Tool Returned Success
        if (expected == JciEvidenceLevel.TOOL_RETURN) {
            return JciVerificationResult(
                status = JciVerificationStatus.VERIFIED,
                attainedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
                expectedEvidenceLevel = expected,
                canReportDone = true,
                message = "Tool execution completed successfully.",
                evidence = mapOf("toolSuccess" to true)
            )
        }

        // 6. Level 0: None (best-effort)
        return JciVerificationResult(
            status = JciVerificationStatus.VERIFIED,
            attainedEvidenceLevel = JciEvidenceLevel.NONE,
            expectedEvidenceLevel = expected,
            canReportDone = true,
            message = "Best-effort action completed.",
            evidence = emptyMap()
        )
    }

    private fun deriveDefaultPredicate(
        capability: JciCapability,
        params: Map<String, String>
    ): ((Map<String, Any>) -> Boolean)? {
        return when (capability.id) {
            "FLASHLIGHT" -> {
                val action = params["action"]?.lowercase() ?: "on"
                when (action) {
                    "on" -> { state: Map<String, Any> -> state["torch_on"] == true }
                    "off" -> { state: Map<String, Any> -> state["torch_on"] == false }
                    else -> null
                }
            }
            "SCREEN_LOCK" -> { state: Map<String, Any> -> state["is_locked"] == true }
            "FILE_WRITE" -> { state: Map<String, Any> -> state["file_exists"] == true }
            else -> { state: Map<String, Any> ->
                if (state.containsKey("verified")) state["verified"] == true
                else if (state.containsKey("success")) state["success"] == true
                else true
            }
        }
    }
}
