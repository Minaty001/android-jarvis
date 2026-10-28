package com.jarvis.ai

import com.jarvis.cognitive.CognitiveMode
import com.jarvis.cognitive.CognitiveOrchestrator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmReasoningPhase1Test {

    private class MockLlm(private val response: String) : LlmClient {
        var callCount = 0
            private set
        var lastMessages: List<Message>? = null
            private set

        override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> {
            callCount++
            lastMessages = messages
            return Result.success(response)
        }

        override fun cancel() {}
    }

    // 1.1 IntentModel Reasoning Tests
    @Test
    fun testIntentModelDeterministicMatchIncludesReason() = runBlocking {
        val model = IntentModel()
        val resolved = model.classifyWithReasoning("turn on flashlight")
        assertNotNull(resolved)
        assertEquals(AssistantIntent.AUTONOMOUS_CONTROL, resolved!!.intent)
        assertNotNull(resolved.reason)
        assertTrue(resolved.reason!!.isNotBlank())
    }

    @Test
    fun testIntentModelLlmFallbackOnRegexMiss() = runBlocking {
        val mockLlm = MockLlm("""
            {
              "intent": "PLAY_MEDIA",
              "confidence": 0.92,
              "params": {"query": "chill acoustic vibes"},
              "reason": "User phrased a music playback request in colloquial slang"
            }
        """.trimIndent())
        val model = IntentModel(mockLlm)
        val resolved = model.classifyWithReasoning("can we put on some chill acoustic vibes please")
        assertNotNull(resolved)
        assertEquals(AssistantIntent.PLAY_MEDIA, resolved!!.intent)
        assertEquals(0.92f, resolved.confidence, 0.01f)
        assertEquals("User phrased a music playback request in colloquial slang", resolved.reason)
        assertEquals(1, mockLlm.callCount)
    }

    @Test
    fun testIntentModelCachesLlmResult() = runBlocking {
        val mockLlm = MockLlm("""
            {
              "intent": "SEARCH_WEB",
              "confidence": 0.88,
              "params": {"query": "quantum computing basics"},
              "reason": "Informational research query"
            }
        """.trimIndent())
        val model = IntentModel(mockLlm)
        val query = "could you look up quantum computing basics for me"
        val first = model.classifyWithReasoning(query)
        val second = model.classifyWithReasoning(query)
        assertEquals(first?.intent, second?.intent)
        assertEquals(1, mockLlm.callCount) // Only called once due to cache
    }

    @Test
    fun testIntentModelRefusesCloudForSensitiveUtterance() = runBlocking {
        val mockLlm = MockLlm("""{"intent":"SEARCH_WEB","confidence":0.9}""")
        val model = IntentModel(mockLlm)
        val resolved = model.classifyWithReasoning("my bank password is secret123 what is it")
        assertNull(resolved)
        assertEquals(0, mockLlm.callCount)
    }

    // 1.2 ModelRouter Reasoning Tests
    @Test
    fun testModelRouterProvidesExplanationAndComplexityScore() {
        val router = ModelRouter()
        
        val standardRoute = router.route("turn off living room light")
        assertEquals(ModelTier.STANDARD, standardRoute.tier)
        assertTrue(standardRoute.reason.isNotBlank())
        assertTrue(standardRoute.complexityScore < 0.7f)

        val multiStepRoute = router.route("open youtube and then search for kotlin tutorials")
        assertEquals(ModelTier.STRONG, multiStepRoute.tier)
        assertTrue(multiStepRoute.reason.contains("multi-step", ignoreCase = true))
        assertTrue(multiStepRoute.complexityScore >= 0.7f)

        val planningRoute = router.route("coordinate dinner reservations with Sarah tomorrow")
        assertEquals(ModelTier.STRONG, planningRoute.tier)
        assertTrue(planningRoute.reason.contains("planning", ignoreCase = true))

        val privacyRoute = router.route("enter my secret password")
        assertEquals(ModelTier.LOCAL, privacyRoute.tier)
        assertTrue(privacyRoute.privacySensitive)
        assertTrue(privacyRoute.reason.contains("privacy", ignoreCase = true))
    }

    // 1.3 CognitiveOrchestrator Mode Reasoning Tests
    @Test
    fun testCognitiveOrchestratorInspectModeWithReasoning() = runBlocking {
        val orchestrator = CognitiveOrchestrator()
        
        // Fast-path direct tool execution
        val directDecision = orchestrator.inspectModeWithReasoning("turn on flashlight")
        assertEquals(CognitiveMode.DIRECT, directDecision.mode)
        assertTrue(directDecision.fastPath)
        assertNotNull(directDecision.reason)
        assertNotNull(directDecision.reasoning)

        // Ambiguous command clarification
        val clarifyDecision = orchestrator.inspectModeWithReasoning("send a message")
        assertEquals(CognitiveMode.CLARIFY, clarifyDecision.mode)
        assertFalse(clarifyDecision.fastPath)
        assertTrue(clarifyDecision.reason.contains("Missing", ignoreCase = true) || clarifyDecision.reason.contains("ambiguous", ignoreCase = true))

        // Compound planning mode
        val planDecision = orchestrator.inspectModeWithReasoning("open camera and then take a picture")
        assertEquals(CognitiveMode.PLAN, planDecision.mode)
        assertTrue(planDecision.reasoning?.isNotBlank() == true)
    }
}
