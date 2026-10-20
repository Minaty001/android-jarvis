package com.jarvis.cognitive

import com.jarvis.runtime.RuntimeExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CognitiveOrchestratorTest {

    private lateinit var orchestrator: CognitiveOrchestrator
    private val recordedEvents = mutableListOf<CognitiveEvent>()

    @Before
    fun setUp() {
        recordedEvents.clear()
        orchestrator = CognitiveOrchestrator(
            initialConfig = CognitiveConfig(enabled = false, shadowMode = true)
        )
        orchestrator.addEventListener { recordedEvents.add(it) }
    }

    @Test
    fun testDefaultConfigIsDisabledAndShadowModeTrue() {
        val defaultConfig = CognitiveConfig()
        assertFalse("JCI_ENABLED must default to false", defaultConfig.enabled)
        assertTrue("JCI_SHADOW must default to true when enabled", defaultConfig.shadowMode)
        assertFalse("JCI_PROACTIVE must default to false", defaultConfig.proactive)
        assertEquals(120, defaultConfig.limits.taskTimeoutS)
        assertEquals(20, defaultConfig.limits.llmTimeoutS)
        assertEquals(1, defaultConfig.limits.maxLlmCallsSimple)
        assertEquals(6, defaultConfig.limits.maxLlmCallsComplex)
        assertEquals(12, defaultConfig.limits.maxSteps)
        assertEquals(2, defaultConfig.limits.maxReplans)
        assertEquals(1, defaultConfig.limits.maxRetries)
    }

    @Test
    fun testDisabledOrchestratorPassesThroughDirectly() {
        runBlocking {
            orchestrator.updateConfig(CognitiveConfig(enabled = false))
            var legacyCalled = false

            val result = orchestrator.execute(
                utterance = "open youtube",
                legacyFallback = {
                    legacyCalled = true
                    RuntimeExecutionResult(success = true, spokenResponse = "Opening YouTube", stepResults = emptyList())
                }
            )

            assertTrue("Legacy pipeline must be executed when JCI is disabled", legacyCalled)
            assertEquals("Opening YouTube", result.spokenResponse)
            assertTrue("No events should be logged when JCI is disabled", recordedEvents.isEmpty())
        }
    }

    @Test
    fun testShadowModeLogsDecisionAndPreservesLegacyOutput() {
        runBlocking {
            orchestrator.updateConfig(CognitiveConfig(enabled = true, shadowMode = true))
            var legacyCalled = false

            val legacyResult = RuntimeExecutionResult(
                success = true,
                spokenResponse = "Torch activated",
                stepResults = emptyList()
            )

            val result = orchestrator.execute(
                utterance = "turn on flashlight",
                legacyFallback = {
                    legacyCalled = true
                    legacyResult
                }
            )

            assertTrue("Legacy pipeline must be executed under shadow mode", legacyCalled)
            assertSame("Legacy result must be returned unmodified", legacyResult, result)

            // Verify structured events
            assertTrue("RequestReceived event must be logged", recordedEvents.any { it is CognitiveEvent.RequestReceived })
            val shadowDecision = recordedEvents.filterIsInstance<CognitiveEvent.ShadowDecision>().firstOrNull()
            assertNotNull("ShadowDecision must be logged", shadowDecision)
            assertEquals("Fast path command should be identified as DIRECT mode", CognitiveMode.DIRECT, shadowDecision?.proposedMode)
            assertTrue("Fast path flag must be true", shadowDecision?.fastPath == true)
            assertTrue("Trace ID must be non-empty", shadowDecision?.traceId?.isNotBlank() == true)

            val completed = recordedEvents.filterIsInstance<CognitiveEvent.ExecutionCompleted>().firstOrNull()
            assertNotNull("ExecutionCompleted must be logged", completed)
            assertEquals(shadowDecision?.traceId, completed?.traceId)
        }
    }

    @Test
    fun testFastPathDetectionZeroLlmCallsAndNegligibleLatency() {
        runBlocking {
            orchestrator.updateConfig(CognitiveConfig(enabled = true, shadowMode = true))

            // Warm up classloader, intent regexes, and JIT
            repeat(5) {
                orchestrator.inspectMode("open settings")
            }

            val startNs = System.nanoTime()
            val decision = orchestrator.inspectMode("open settings")
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

            assertEquals(CognitiveMode.DIRECT, decision.mode)
            assertTrue(decision.fastPath)
            assertTrue("Fast path inspection must be sub-millisecond, was: ${elapsedMs}ms", elapsedMs < 20.0)
        }
    }

    @Test
    fun testAmbiguousOrMultiStepModeInspection() {
        val multiStep = orchestrator.inspectMode("turn on flashlight and then open camera and take a picture")
        assertEquals(CognitiveMode.PLAN, multiStep.mode)

        val clarify = orchestrator.inspectMode("send message")
        assertEquals(CognitiveMode.CLARIFY, clarify.mode)

        val directQuestion = orchestrator.inspectMode("who was Albert Einstein?")
        assertEquals(CognitiveMode.DIRECT, directQuestion.mode)
        assertFalse("General question is not a fast-path direct command", directQuestion.fastPath)
    }

    @Test
    fun testSensitiveDataRedaction() {
        val textWithPin = "my secret pin is 1234 and password is supersecretpass"
        val redacted = CognitiveEvent.redactSensitiveData(textWithPin)

        assertFalse(redacted.contains("1234"))
        assertFalse(redacted.contains("supersecretpass"))
        assertTrue(redacted.contains("[REDACTED]"))

        val textWithCard = "card number 4111 2222 3333 4444 details"
        val redactedCard = CognitiveEvent.redactSensitiveData(textWithCard)
        assertFalse(redactedCard.contains("4111"))
        assertTrue(redactedCard.contains("[CARD_REDACTED]"))
    }

    @Test
    fun testInjectedExceptionFallsBackToLegacyPipeline() {
        runBlocking {
            orchestrator.updateConfig(CognitiveConfig(enabled = true, shadowMode = false))
            orchestrator.injectExceptionForTesting = RuntimeException("Simulated orchestrator failure")

            var legacyCalled = false
            val fallbackResult = RuntimeExecutionResult(
                success = true,
                spokenResponse = "Fallback executed successfully",
                stepResults = emptyList()
            )

            val result = orchestrator.execute(
                utterance = "turn on flashlight",
                legacyFallback = {
                    legacyCalled = true
                    fallbackResult
                }
            )

            assertTrue("Legacy pipeline must be executed when orchestrator throws", legacyCalled)
            assertEquals("Fallback executed successfully", result.spokenResponse)

            val fallbackEvent = recordedEvents.filterIsInstance<CognitiveEvent.OrchestratorFallback>().firstOrNull()
            assertNotNull("OrchestratorFallback event must be emitted", fallbackEvent)
            assertTrue(fallbackEvent?.error?.contains("Simulated orchestrator failure") == true)
        }
    }

    @Test(expected = CancellationException::class)
    fun testCancellationPropagatesWithoutFallback() {
        runBlocking {
            orchestrator.updateConfig(CognitiveConfig(enabled = true, shadowMode = false))
            orchestrator.injectExceptionForTesting = CancellationException("Task cancelled by user")

            orchestrator.execute(
                utterance = "some command",
                legacyFallback = {
                    fail("Legacy fallback must NOT be called on CancellationException")
                    RuntimeExecutionResult(false, "", emptyList())
                }
            )
        }
    }
}
