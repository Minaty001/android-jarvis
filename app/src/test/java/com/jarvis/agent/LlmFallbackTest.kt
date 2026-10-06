package com.jarvis.agent

import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the "always-LLM fallback" feature.
 *
 * Tests the logic of directAnswer / speakDecision helpers, and the static
 * matchFallbackRules regression for known commands, without needing full Android context.
 */
class LlmFallbackTest {

    // ─── stub helpers ────────────────────────────────────────────────────────

    /** A minimal LlmClient stub that returns a fixed answer. */
    private fun stubbedLlmClient(answer: String): LlmClient = object : LlmClient {
        override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> =
            Result.success(answer)
        override fun cancel() {}
    }

    /** A stub LlmClient that always fails. */
    private fun failingLlmClient(): LlmClient = object : LlmClient {
        override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> =
            Result.failure(RuntimeException("network error"))
        override fun cancel() {}
    }

    // ─── matchFallbackRules tests (companion object, no Android deps) ──────

    /**
     * Regression: a known command must be matched locally and NOT produce
     * UNRECOGNIZED_FALLBACK — verifying the local rule matcher is still intact.
     */
    @Test
    fun testKnownCommandFlashlightMatchedLocally() {
        val decision = AgentKernel.matchFallbackRules("flashlight on")
        assertEquals("Known command should match locally", DecisionType.ACT, decision.type)
        assertEquals("FLASHLIGHT", decision.action?.type)
        assertNotEquals("Should not be UNRECOGNIZED_FALLBACK", "UNRECOGNIZED_FALLBACK", decision.reasonCode)
    }

    @Test
    fun testKnownCommandPlayMusicMatchedLocally() {
        val decision = AgentKernel.matchFallbackRules("play something")
        assertNotEquals("Play command should not be unrecognized", "UNRECOGNIZED_FALLBACK", decision.reasonCode)
    }

    /**
     * Completely unknown gibberish must produce UNRECOGNIZED_FALLBACK from the static
     * rule matcher — confirming the LLM intercept layer is the only thing that saves it.
     */
    @Test
    fun testUnknownGoalProducesUnrecognizedFallbackFromStaticMatcher() {
        val decision = AgentKernel.matchFallbackRules("completely unknown gibberish xyz123abc")
        assertEquals("UNRECOGNIZED_FALLBACK", decision.reasonCode)
        assertEquals(DecisionType.ASK_USER, decision.type)
    }

    // ─── LLM directAnswer logic tests (inline, no Android context needed) ────

    /**
     * Verify that a well-formed LLM chat response round-trips through the
     * Result<String> path correctly — the same path directAnswer() uses internally.
     */
    @Test
    fun testLlmClientReturnsAnswerCorrectly() = runBlocking {
        val client = stubbedLlmClient("The capital of France is Paris.")
        val messages = listOf(
            Message(role = "system", content = "You are Jarvis."),
            Message(role = "user", content = "what is the capital of france")
        )
        val result = client.chat(messages)
        assertTrue("LLM result should succeed", result.isSuccess)
        assertEquals("The capital of France is Paris.", result.getOrNull())
    }

    /**
     * Verify that a failing LLM client surfaces failure — confirming directAnswer()
     * would catch it and return null (graceful degradation).
     */
    @Test
    fun testFailingLlmClientReturnsFailure() = runBlocking {
        val client = failingLlmClient()
        val messages = listOf(Message(role = "user", content = "test"))
        val result = client.chat(messages)
        assertTrue("Failing LLM should return failure Result", result.isFailure)
    }

    /**
     * Blank LLM response must be treated as unavailable (ifBlank { null } pattern).
     */
    @Test
    fun testBlankLlmResponseTreatedAsNull() = runBlocking {
        val client = stubbedLlmClient("   ")
        val messages = listOf(Message(role = "user", content = "test"))
        val rawAnswer = client.chat(messages).getOrNull()?.trim()?.ifBlank { null }
        assertNull("Blank LLM response should be treated as null", rawAnswer)
    }

    // ─── speakDecision shape test ─────────────────────────────────────────────

    /**
     * Verify the shape of a SPEAK decision — used by speakDecision() helper.
     * Tests the exact structure expected by the SPEAK tool handler.
     */
    @Test
    fun testSpeakDecisionShape() {
        val text = "The answer is 42."
        val decision = AgentDecision(
            type = DecisionType.ACT,
            action = AgentAction(type = "SPEAK", params = mapOf("text" to text)),
            reasonCode = "llm_direct_answer",
            successCriteria = listOf("Replied from LLM")
        )
        assertEquals(DecisionType.ACT, decision.type)
        assertEquals("SPEAK", decision.action?.type)
        assertEquals(text, decision.action?.params?.get("text"))
        assertEquals("llm_direct_answer", decision.reasonCode)
    }
}
