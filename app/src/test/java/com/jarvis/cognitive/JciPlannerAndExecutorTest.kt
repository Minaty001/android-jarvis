package com.jarvis.cognitive

import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import com.jarvis.cognitive.capability.JciCapabilityRegistry
import com.jarvis.cognitive.capability.JciEvidenceLevel
import com.jarvis.cognitive.planner.*
import com.jarvis.cognitive.policy.JciPolicyEngine
import com.jarvis.cognitive.verifier.JciVerifier
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JciPlannerAndExecutorTest {

    private lateinit var policyEngine: JciPolicyEngine
    private lateinit var verifier: JciVerifier
    private lateinit var planner: JciPlanner
    private lateinit var executor: JciPlanExecutor

    @Before
    fun setUp() {
        policyEngine = JciPolicyEngine()
        policyEngine.resetRateLimits()
        verifier = JciVerifier()
        planner = JciPlanner(
            llmClient = null,
            policyEngine = policyEngine
        )
        executor = JciPlanExecutor(
            planner = planner,
            policyEngine = policyEngine,
            verifier = verifier,
            maxSteps = 12,
            maxReplans = 2,
            maxRetries = 1,
            taskTimeoutS = 10L
        )
    }

    @Test
    fun testValidPlanExecutesStepByStepToCompletion() = runBlocking {
        val plan = JciPlan(
            goal = "Turn on flashlight and query weather",
            steps = listOf(
                PlanStep(
                    stepId = "step_1",
                    toolName = "FLASHLIGHT",
                    args = mapOf("action" to "on"),
                    expectedEvidence = JciEvidenceLevel.STATE_OBSERVED
                ),
                PlanStep(
                    stepId = "step_2",
                    toolName = "WEATHER",
                    expectedEvidence = JciEvidenceLevel.TOOL_RETURN
                )
            )
        )

        val executedTools = mutableListOf<String>()

        val result = executor.executePlan(
            initialPlan = plan,
            toolDispatcher = { tool, _ ->
                executedTools.add(tool)
                ToolResult.Success("Success for $tool")
            },
            observedStateProvider = { step ->
                if (step.toolName == "FLASHLIGHT") mapOf("torch_on" to true) else null
            }
        )

        assertEquals(PlanExecutionStatus.COMPLETED, result.status)
        assertEquals(2, result.executedSteps.size)
        assertEquals(listOf("FLASHLIGHT", "WEATHER"), executedTools)
        assertTrue(result.finalMessage.contains("Plan completed successfully"))
    }

    @Test
    fun testPlanWithInventedToolRejectedAtValidation() = runBlocking {
        val invalidPlan = JciPlan(
            goal = "Fly a drone to Paris",
            steps = listOf(
                PlanStep(
                    stepId = "step_1",
                    toolName = "NON_EXISTENT_MAGIC_DRONE_TOOL",
                    args = emptyMap()
                )
            )
        )

        var toolExecuted = false

        val result = executor.executePlan(
            initialPlan = invalidPlan,
            toolDispatcher = { _, _ ->
                toolExecuted = true
                ToolResult.Success("Should never run")
            }
        )

        assertEquals(PlanExecutionStatus.FAILED, result.status)
        assertFalse("Tool must never execute when plan is invalid", toolExecuted)
        assertTrue("Error must state unregistered capability", result.finalMessage.contains("unregistered or invented capability"))
    }

    @Test
    fun testHighRiskStepPausesForConfirmationWithoutExecuting() = runBlocking {
        val highRiskPlan = JciPlan(
            goal = "Send WhatsApp message to Bob",
            steps = listOf(
                PlanStep(
                    stepId = "step_1",
                    toolName = "WHATSAPP",
                    args = mapOf("recipient" to "Bob", "message" to "Hello!"),
                    expectedEvidence = JciEvidenceLevel.USER_CONFIRMED
                )
            )
        )

        var toolExecuted = false

        val result = executor.executePlan(
            initialPlan = highRiskPlan,
            toolDispatcher = { _, _ ->
                toolExecuted = true
                ToolResult.Success("Sent")
            },
            isUserConfirmed = { false } // User has NOT confirmed
        )

        assertEquals(PlanExecutionStatus.AWAITING_CONFIRMATION, result.status)
        assertFalse("Tool must NOT be dispatched without confirmation", toolExecuted)
        assertNotNull(result.pendingConfirmationStep)
        assertEquals("step_1", result.pendingConfirmationStep?.stepId)
    }

    @Test
    fun testStepFailureTriggersRetryThenReplan() = runBlocking {
        var step1Attempts = 0

        val plan = JciPlan(
            goal = "Locate file",
            steps = listOf(
                PlanStep(
                    stepId = "step_1",
                    toolName = "FILE_READ",
                    args = mapOf("path" to "/sdcard/doc.txt"),
                    retryable = true
                )
            )
        )

        // Mock planner with replan capability
        val mockLlm = FakeLlmClient(
            cannedResponse = Result.success("""
                {
                  "goal": "Locate file",
                  "reason": "Replanned to search files",
                  "steps": [
                    {
                      "step_id": "step_replan_1",
                      "tool_name": "FILE_MANAGER",
                      "args": {"action": "search"},
                      "description": "Fallback search"
                    }
                  ]
                }
            """.trimIndent())
        )

        val customPlanner = JciPlanner(llmClient = mockLlm, policyEngine = policyEngine)
        val customExecutor = JciPlanExecutor(
            planner = customPlanner,
            policyEngine = policyEngine,
            verifier = verifier,
            maxRetries = 1,
            maxReplans = 2
        )

        val executedTools = mutableListOf<String>()

        val result = customExecutor.executePlan(
            initialPlan = plan,
            toolDispatcher = { tool, _ ->
                executedTools.add(tool)
                if (tool == "FILE_READ") {
                    step1Attempts++
                    ToolResult.Failed("File not found")
                } else {
                    ToolResult.Success("Found via search")
                }
            }
        )

        assertEquals(PlanExecutionStatus.COMPLETED, result.status)
        assertEquals("Initial step must be retried once (1 attempt + 1 retry = 2 calls)", 2, step1Attempts)
        assertEquals(1, result.replanCount)
        assertTrue(executedTools.contains("FILE_MANAGER"))
    }

    @Test
    fun testReplanLimitStopsExecution() = runBlocking {
        val plan = JciPlan(
            goal = "Impossible task",
            steps = listOf(
                PlanStep(
                    stepId = "step_1",
                    toolName = "CALCULATE",
                    args = mapOf("expression" to "attempt_0 / 0"),
                    retryable = false
                )
            )
        )

        // Mock LLM that generates a revised plan with distinct parameters on each replan
        val mockLlm = object : LlmClient {
            var callCount = 0
            override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> {
                callCount++
                return Result.success("""
                    {
                      "goal": "Impossible task",
                      "reason": "Try variant $callCount",
                      "steps": [
                        {
                          "step_id": "step_retry_$callCount",
                          "tool_name": "CALCULATE",
                          "args": {"expression": "attempt_$callCount / 0"},
                          "retryable": false
                        }
                      ]
                    }
                """.trimIndent())
            }
            override fun cancel() {}
            override fun close() {}
        }

        val customPlanner = JciPlanner(llmClient = mockLlm, policyEngine = policyEngine)
        val customExecutor = JciPlanExecutor(
            planner = customPlanner,
            policyEngine = policyEngine,
            verifier = verifier,
            maxReplans = 2
        )

        val result = customExecutor.executePlan(
            initialPlan = plan,
            toolDispatcher = { _, _ -> ToolResult.Failed("Division by zero") }
        )

        assertEquals(PlanExecutionStatus.FAILED, result.status)
        assertEquals(2, result.replanCount)
        assertTrue(result.finalMessage.contains("replan limit (2) exhausted"))
    }

    @Test
    fun testHardLoopLimitStopsExecutionAtMaxSteps() = runBlocking {
        // Create plan with 20 steps
        val manySteps = (1..20).map { i ->
            PlanStep(stepId = "step_$i", toolName = "CALCULATE", args = mapOf("expression" to "$i + 1"))
        }
        val bigPlan = JciPlan("Long task", manySteps)

        val limitedExecutor = JciPlanExecutor(
            planner = planner,
            policyEngine = policyEngine,
            verifier = verifier,
            maxSteps = 5
        )

        var executedCount = 0
        val result = limitedExecutor.executePlan(
            initialPlan = bigPlan,
            toolDispatcher = { _, _ ->
                executedCount++
                ToolResult.Success("Ok")
            }
        )

        assertEquals(PlanExecutionStatus.FAILED, result.status)
        assertEquals(5, executedCount)
        assertTrue(result.finalMessage.contains("Hard loop limit stopped execution"))
    }

    @Test
    fun testCancellationStopsExecutionBetweenSteps() = runBlocking {
        val plan = JciPlan(
            goal = "Multi-step cancel",
            steps = (1..10).map { i ->
                PlanStep(stepId = "step_$i", toolName = "CALCULATE", args = mapOf("expression" to "$i"))
            }
        )

        var executedCount = 0
        val job = launch {
            executor.executePlan(
                initialPlan = plan,
                toolDispatcher = { _, _ ->
                    executedCount++
                    delay(50)
                    ToolResult.Success("Done")
                }
            )
        }

        delay(80) // Let 1-2 steps run
        job.cancelAndJoin()

        assertTrue("Execution must be halted before all 10 steps finish", executedCount < 10)
    }

    @Test
    fun testVerificationFailureMarksStepAsFailed() = runBlocking {
        val plan = JciPlan(
            goal = "Turn on flashlight",
            steps = listOf(
                PlanStep(
                    stepId = "step_1",
                    toolName = "FLASHLIGHT",
                    args = mapOf("action" to "on"),
                    expectedEvidence = JciEvidenceLevel.STATE_OBSERVED,
                    retryable = false
                )
            )
        )

        val result = executor.executePlan(
            initialPlan = plan,
            toolDispatcher = { _, _ ->
                // Tool claims success, but hardware says otherwise!
                ToolResult.Success("Hardware toggle dispatched")
            },
            observedStateProvider = {
                // Observed state says torch is actually OFF!
                mapOf("torch_on" to false)
            }
        )

        // Verification engine should catch that torch_on != true and reject the step!
        assertEquals(PlanExecutionStatus.FAILED, result.status)
        assertFalse("Step must not report success when observed state does not match", result.executedSteps.first().success)
    }
}
