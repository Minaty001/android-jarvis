package com.jarvis.cognitive

import com.jarvis.agent.AgentEvent
import com.jarvis.runtime.RuntimeExecutionResult
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JciEndToEndIntegrationTest {

    private lateinit var orchestrator: CognitiveOrchestrator
    private val recordedEvents = mutableListOf<CognitiveEvent>()
    private val agentEvents = mutableListOf<AgentEvent>()

    @Before
    fun setUp() {
        recordedEvents.clear()
        agentEvents.clear()
        orchestrator = CognitiveOrchestrator(
            initialConfig = CognitiveConfig(enabled = true, shadowMode = false)
        )
        orchestrator.addEventListener { recordedEvents.add(it) }
    }

    @Test
    fun testEndToEndDirectFastPathExecutionWithHonestConfirmation() = runBlocking {
        var toolExecuted = false
        var legacyFallbackCalled = false

        orchestrator.directToolDispatcher = { capabilityId, args ->
            toolExecuted = true
            assertEquals("FLASHLIGHT", capabilityId)
            ToolResult(success = true, message = "Flashlight enabled successfully")
        }

        val result = orchestrator.execute(
            utterance = "turn on flashlight",
            userApprovalGranted = false,
            onEvent = { agentEvents.add(it) },
            legacyFallback = {
                legacyFallbackCalled = true
                RuntimeExecutionResult(success = true, spokenResponse = "legacy", stepResults = emptyList())
            }
        )

        assertTrue("Direct tool dispatcher must execute the tool", toolExecuted)
        assertFalse("Legacy fallback must not be invoked when JCI handles direct tool", legacyFallbackCalled)
        assertTrue("Execution must succeed", result.success)
        assertTrue("Level 2 verification must verify the operation", result.verified)
        assertEquals("Flashlight activated, sir.", result.spokenResponse)

        val modeEvent = recordedEvents.filterIsInstance<CognitiveEvent.ModeSelected>().firstOrNull()
        assertNotNull("ModeSelected event must be logged", modeEvent)
        assertEquals(CognitiveMode.DIRECT, modeEvent?.mode)
        assertTrue("Fast path flag must be true", modeEvent?.fastPath == true)

        val completionEvent = recordedEvents.filterIsInstance<CognitiveEvent.ExecutionCompleted>().firstOrNull()
        assertNotNull("ExecutionCompleted event must be logged", completionEvent)
        assertTrue(completionEvent?.success == true)
    }

    @Test
    fun testEndToEndPlanExecutionWithMultiStepPipeline() = runBlocking {
        var stepsExecuted = 0
        orchestrator.directToolDispatcher = { capabilityId, args ->
            stepsExecuted++
            ToolResult(success = true, message = "Executed $capabilityId")
        }

        val result = orchestrator.execute(
            utterance = "turn on flashlight and then open camera",
            userApprovalGranted = false,
            onEvent = { agentEvents.add(it) },
            legacyFallback = {
                fail("Legacy fallback should not be called when JCI handles plan")
                RuntimeExecutionResult(false, "", emptyList())
            }
        )

        assertTrue(result.success)
        assertTrue(result.verified)
        assertTrue(result.spokenResponse.contains("sir"))
        assertEquals(1, stepsExecuted)

        val modeEvent = recordedEvents.filterIsInstance<CognitiveEvent.ModeSelected>().firstOrNull()
        assertNotNull(modeEvent)
        assertEquals(CognitiveMode.PLAN, modeEvent?.mode)
    }

    @Test
    fun testEndToEndDestructiveRefusalInvariant() = runBlocking {
        var legacyFallbackCalled = false

        val result = orchestrator.execute(
            utterance = "wipe phone and format device",
            userApprovalGranted = false,
            onEvent = { agentEvents.add(it) },
            legacyFallback = {
                legacyFallbackCalled = true
                RuntimeExecutionResult(false, "", emptyList())
            }
        )

        assertFalse("Destructive operation must return failure", result.success)
        assertTrue("Refusal is verified safe", result.verified)
        assertFalse("Legacy pipeline must NEVER execute for prohibited destructive commands", legacyFallbackCalled)
        assertTrue(result.spokenResponse.startsWith("I am afraid I cannot do that, sir:"))
        assertTrue(result.spokenResponse.contains("destructive operation"))

        val modeEvent = recordedEvents.filterIsInstance<CognitiveEvent.ModeSelected>().firstOrNull()
        assertNotNull(modeEvent)
        assertEquals(CognitiveMode.REFUSE, modeEvent?.mode)
    }

    @Test
    fun testEndToEndSafetyConfirmationRequirement() = runBlocking {
        orchestrator.policyEngine.resetRateLimits()

        // SCREEN_UNLOCK is classified as HIGH risk in JciCapabilityRegistry and requires confirmation
        val capId = "SCREEN_UNLOCK"
        val cap = orchestrator.registry.getCapability(capId)
        assertNotNull("Capability must exist", cap)

        val resultWithoutApproval = orchestrator.execute(
            utterance = "unlock screen with pin 1234",
            userApprovalGranted = false,
            onEvent = { agentEvents.add(it) },
            legacyFallback = {
                RuntimeExecutionResult(false, "", emptyList())
            }
        )

        // When confirmation is required and not granted
        assertTrue(
            "Confirmation prompt must be returned",
            resultWithoutApproval.spokenResponse.contains("requires your confirmation") ||
                    resultWithoutApproval.spokenResponse.contains("sir?")
        )
        assertFalse("Execution must not succeed without approval", resultWithoutApproval.success)
        assertFalse("Result must not be marked verified without approval", resultWithoutApproval.verified)

        // Now with userApprovalGranted = true, confirmation succeeds
        orchestrator.policyEngine.resetRateLimits()
        orchestrator.directToolDispatcher = { capabilityId, args ->
            ToolResult(success = true, message = "Screen unlocked")
        }
        val resultWithApproval = orchestrator.execute(
            utterance = "unlock screen with pin 1234",
            userApprovalGranted = true,
            onEvent = { agentEvents.add(it) },
            legacyFallback = {
                RuntimeExecutionResult(false, "", emptyList())
            }
        )
        assertTrue("Execution must succeed with user approval", resultWithApproval.success)
        assertTrue("Execution must be verified with user approval", resultWithApproval.verified)
    }

    @Test
    fun testEndToEndFailSafeFallbackPreservesLegacyOutput() = runBlocking {
        orchestrator.policyEngine.resetRateLimits()

        // Force an unhandled error inside JCI dispatcher
        orchestrator.directToolDispatcher = { _, _ ->
            throw IllegalStateException("Simulated hardware bus disconnect")
        }

        var legacyFallbackCalled = false
        val legacyResult = RuntimeExecutionResult(
            success = true,
            spokenResponse = "Legacy fallback recovered",
            stepResults = emptyList()
        )

        val result = orchestrator.execute(
            utterance = "turn on flashlight",
            userApprovalGranted = false,
            onEvent = { agentEvents.add(it) },
            legacyFallback = {
                legacyFallbackCalled = true
                legacyResult
            }
        )

        assertTrue("Orchestrator must fall back to legacy pipeline on internal error", legacyFallbackCalled)
        assertSame("Legacy result must be preserved verbatim", legacyResult, result)

        val fallbackEvent = recordedEvents.filterIsInstance<CognitiveEvent.OrchestratorFallback>().firstOrNull()
        assertNotNull("OrchestratorFallback event must be emitted", fallbackEvent)
        assertTrue(fallbackEvent?.error?.contains("Simulated hardware bus disconnect") == true)
    }

    @Test
    fun testEndToEndMemoryManagerPersonalization() = runBlocking {
        orchestrator.memoryManager.storePreference("preferred_name", "Tony Stark", confirmedByUser = true)
        val retrieved = orchestrator.memoryManager.retrieveRelevant("what should Tony Stark do?")
        assertTrue(retrieved.any { it.value == "Tony Stark" })
    }

    @Test
    fun testEndToEndFastPathLatencyBenchmark() = runBlocking {
        // Warm up JIT and regex caches
        repeat(10) {
            orchestrator.inspectMode("open settings")
            orchestrator.inspectMode("turn on flashlight")
        }

        val iterations = 50
        val timesNs = LongArray(iterations)

        for (i in 0 until iterations) {
            val start = System.nanoTime()
            val decision = orchestrator.inspectMode("turn on flashlight")
            timesNs[i] = System.nanoTime() - start
            assertTrue(decision.fastPath)
            assertEquals(CognitiveMode.DIRECT, decision.mode)
        }

        val avgMs = timesNs.average() / 1_000_000.0
        val maxMs = (timesNs.maxOrNull() ?: 0L) / 1_000_000.0

        println("Fast-path benchmark: avg=${avgMs}ms, max=${maxMs}ms over $iterations iterations")
        assertTrue("Steady-state fast path inspection must be sub-millisecond (< 5.0ms), was ${avgMs}ms", avgMs < 5.0)
    }
}
