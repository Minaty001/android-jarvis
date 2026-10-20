package com.jarvis.cognitive.planner

import android.util.Log
import com.jarvis.cognitive.capability.JciCapabilityRegistry
import com.jarvis.cognitive.policy.JciPolicyEngine
import com.jarvis.cognitive.verifier.JciVerifier
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

class JciPlanExecutor(
    private val planner: JciPlanner,
    private val policyEngine: JciPolicyEngine = JciPolicyEngine(),
    private val verifier: JciVerifier = JciVerifier(),
    val maxSteps: Int = 12,
    val maxReplans: Int = 2,
    val maxRetries: Int = 1,
    val taskTimeoutS: Long = 120L
) {
    private val TAG = "JciPlanExecutor"

    suspend fun executePlan(
        initialPlan: JciPlan,
        toolDispatcher: suspend (toolName: String, params: Map<String, String>) -> ToolResult,
        isUserConfirmed: (step: PlanStep) -> Boolean = { false },
        observedStateProvider: (step: PlanStep) -> Map<String, Any>? = { null }
    ): PlanExecutionResult {
        // Validate plan first
        val validation = planner.validatePlan(initialPlan)
        if (!validation.valid) {
            return PlanExecutionResult(
                status = PlanExecutionStatus.FAILED,
                executedSteps = emptyList(),
                replanCount = 0,
                finalMessage = "Plan validation failed: ${validation.error}"
            )
        }

        val timedOut = withTimeoutOrNull(taskTimeoutS * 1000L) {
            runExecutionLoop(initialPlan, toolDispatcher, isUserConfirmed, observedStateProvider)
        }

        return timedOut ?: PlanExecutionResult(
            status = PlanExecutionStatus.TIMED_OUT,
            executedSteps = emptyList(),
            replanCount = 0,
            finalMessage = "Task execution timed out after ${taskTimeoutS}s"
        )
    }

    private suspend fun runExecutionLoop(
        initialPlan: JciPlan,
        toolDispatcher: suspend (toolName: String, params: Map<String, String>) -> ToolResult,
        isUserConfirmed: (step: PlanStep) -> Boolean,
        observedStateProvider: (step: PlanStep) -> Map<String, Any>?
    ): PlanExecutionResult {
        var currentPlan = initialPlan
        var currentStepIndex = 0
        var replanCount = 0
        var totalStepsExecuted = 0
        val executionHistory = mutableListOf<StepExecutionRecord>()

        while (currentStepIndex < currentPlan.steps.size) {
            // 1. Cancellation check before every step
            coroutineContext.ensureActive()

            // 2. Global step limit check
            if (totalStepsExecuted >= maxSteps) {
                return PlanExecutionResult(
                    status = PlanExecutionStatus.FAILED,
                    executedSteps = executionHistory,
                    replanCount = replanCount,
                    finalMessage = "Hard loop limit stopped execution: reached maximum steps ($maxSteps)."
                )
            }

            val step = currentPlan.steps[currentStepIndex]
            val capability = JciCapabilityRegistry.getCapability(step.toolName)
            if (capability == null) {
                return PlanExecutionResult(
                    status = PlanExecutionStatus.FAILED,
                    executedSteps = executionHistory,
                    replanCount = replanCount,
                    finalMessage = "Unknown capability '${step.toolName}' encountered during execution."
                )
            }

            // 3. Policy re-check
            val confirmed = isUserConfirmed(step)
            val policyDecision = policyEngine.evaluateCapability(capability, step.args, userConfirmed = confirmed)

            if (policyDecision.requiresConfirmation) {
                return PlanExecutionResult(
                    status = PlanExecutionStatus.AWAITING_CONFIRMATION,
                    executedSteps = executionHistory,
                    replanCount = replanCount,
                    finalMessage = "Step '${step.stepId}' requires user confirmation before execution.",
                    pendingConfirmationStep = step
                )
            }

            if (!policyDecision.allowed) {
                return PlanExecutionResult(
                    status = PlanExecutionStatus.FAILED,
                    executedSteps = executionHistory,
                    replanCount = replanCount,
                    finalMessage = "Step '${step.stepId}' blocked by policy: ${policyDecision.reason}"
                )
            }

            // 4. Step execution and bounded retry
            var stepSuccess = false
            var retriesUsed = 0
            var lastToolOutput = ""
            var lastVerificationResult: com.jarvis.cognitive.verifier.JciVerificationResult? = null
            var lastError: String? = null

            val startMs = System.currentTimeMillis()

            while (retriesUsed <= maxRetries && !stepSuccess) {
                coroutineContext.ensureActive()
                try {
                    val toolResult = toolDispatcher(capability.id, step.args)
                    lastToolOutput = toolResult.message

                    val observation = observedStateProvider(step)
                    val verification = verifier.verifyStep(
                        capability = capability,
                        params = step.args,
                        toolSuccess = toolResult.success,
                        toolMessage = toolResult.message,
                        observedState = observation,
                        userConfirmed = confirmed
                    )
                    lastVerificationResult = verification

                    if (verification.canReportDone) {
                        stepSuccess = true
                    } else {
                        lastError = verification.message
                        if (step.retryable && retriesUsed < maxRetries) {
                            retriesUsed++
                            Log.w(TAG, "Step '${step.stepId}' failed verification: ${verification.message}. Retrying ($retriesUsed/$maxRetries)...")
                        } else {
                            break
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                    if (step.retryable && retriesUsed < maxRetries) {
                        retriesUsed++
                    } else {
                        break
                    }
                }
            }

            val elapsedMs = System.currentTimeMillis() - startMs
            totalStepsExecuted++

            val stepRecord = StepExecutionRecord(
                step = step,
                success = stepSuccess,
                retryCount = retriesUsed,
                toolOutput = lastToolOutput,
                verification = lastVerificationResult,
                durationMs = elapsedMs,
                error = if (!stepSuccess) lastError else null
            )
            executionHistory.add(stepRecord)

            // 5. Handle step outcome
            if (stepSuccess) {
                currentStepIndex++
            } else {
                // Step failed! Check if we can replan
                if (replanCount < maxReplans) {
                    replanCount++
                    Log.i(TAG, "Triggering bounded replan ($replanCount/$maxReplans) after step failure...")
                    val replanResult = planner.createPlan(currentPlan.goal, executionHistory)

                    if (replanResult.isSuccess) {
                        currentPlan = replanResult.getOrThrow()
                        currentStepIndex = 0
                        Log.i(TAG, "Replan succeeded. Resuming execution with ${currentPlan.steps.size} new steps.")
                    } else {
                        return PlanExecutionResult(
                            status = PlanExecutionStatus.FAILED,
                            executedSteps = executionHistory,
                            replanCount = replanCount,
                            finalMessage = "Step '${step.stepId}' failed and replanning failed: ${replanResult.exceptionOrNull()?.message}"
                        )
                    }
                } else {
                    // Replans exhausted!
                    return PlanExecutionResult(
                        status = PlanExecutionStatus.FAILED,
                        executedSteps = executionHistory,
                        replanCount = replanCount,
                        finalMessage = "Step '${step.stepId}' failed and replan limit ($maxReplans) exhausted. Error: $lastError"
                    )
                }
            }
        }

        return PlanExecutionResult(
            status = PlanExecutionStatus.COMPLETED,
            executedSteps = executionHistory,
            replanCount = replanCount,
            finalMessage = "Plan completed successfully: ${executionHistory.size} steps executed."
        )
    }
}
