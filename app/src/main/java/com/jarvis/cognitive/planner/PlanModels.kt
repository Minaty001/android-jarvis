package com.jarvis.cognitive.planner

import com.jarvis.cognitive.capability.JciEvidenceLevel
import com.jarvis.cognitive.verifier.JciVerificationResult

data class PlanStep(
    val stepId: String,
    val toolName: String,
    val args: Map<String, String> = emptyMap(),
    val expectedEvidence: JciEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
    val description: String = "",
    val retryable: Boolean = true,
    val stopCondition: String = ""
)

data class JciPlan(
    val goal: String,
    val steps: List<PlanStep>,
    val reason: String = ""
)

data class PlanValidationResult(
    val valid: Boolean,
    val error: String? = null,
    val stepsNeedingConfirmation: List<String> = emptyList()
)

data class StepExecutionRecord(
    val step: PlanStep,
    val success: Boolean,
    val retryCount: Int = 0,
    val toolOutput: String = "",
    val verification: JciVerificationResult? = null,
    val durationMs: Long = 0L,
    val error: String? = null
)

enum class PlanExecutionStatus {
    COMPLETED,
    FAILED,
    AWAITING_CONFIRMATION,
    TIMED_OUT,
    CANCELLED
}

data class PlanExecutionResult(
    val status: PlanExecutionStatus,
    val executedSteps: List<StepExecutionRecord>,
    val replanCount: Int = 0,
    val finalMessage: String,
    val pendingConfirmationStep: PlanStep? = null
)
