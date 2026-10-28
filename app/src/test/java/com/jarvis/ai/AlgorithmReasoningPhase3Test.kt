package com.jarvis.ai

import com.jarvis.agent.SkillDiscoveryEngine
import com.jarvis.clipboard.ClipboardIntelligenceEngine
import com.jarvis.memory.MemoryQueryType
import com.jarvis.memory.MemoryRetrievalProfile
import com.jarvis.memory.MemoryStore
import com.jarvis.rag.DocumentChunk
import com.jarvis.rag.RagIndexStore
import com.jarvis.rag.RagRetriever
import com.jarvis.voice.BargeInDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmReasoningPhase3Test {

    private class MockLlm(private val response: String) : LlmClient {
        var callCount = 0
            private set
        override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> {
            callCount++
            return Result.success(response)
        }
        override fun cancel() {}
    }

    // 3.1 SkillDiscoveryEngine LLM Skill Proposer
    @Test
    fun testSkillDiscoveryEngineProposesSkillFromRepetitiveGoals() = runBlocking {
        val mockLlm = MockLlm("""
            {
              "skill_id": "morning_briefing_routine",
              "proposed_name": "Morning Briefing",
              "description": "Reads unread notifications and plays morning radio",
              "triggers": ["good morning jarvis", "start my morning"],
              "clarification_prompt": "I noticed you regularly check notifications and play morning news. Would you like me to make this a Morning Briefing skill?",
              "steps": [
                {"step_index": 1, "action_type": "READ_NOTIFICATIONS", "params": {}},
                {"step_index": 2, "action_type": "MUSIC_PLAY", "params": {"query": "morning news radio"}}
              ]
            }
        """.trimIndent())

        val engine = SkillDiscoveryEngine()
        val proposal = engine.analyzeForSkillOpportunity(
            listOf(
                "read my notifications and play news",
                "check notifications and then play morning news",
                "read notifications and start morning radio"
            ),
            llm = mockLlm
        )

        assertNotNull(proposal)
        assertEquals("morning_briefing_routine", proposal!!.skillId)
        assertEquals("Morning Briefing", proposal.proposedName)
        assertEquals(2, proposal.steps.size)
        assertTrue(proposal.suggestedTriggers.contains("good morning jarvis"))
    }

    // 3.2 MemoryStore Adaptive Retrieval Weights
    @Test
    fun testMemoryStoreAdaptiveRetrievalProfiles() {
        val prefProfile = MemoryRetrievalProfile.forType(MemoryQueryType.PREFERENCE)
        assertEquals(0.40, prefProfile.wImportance, 0.01)
        assertTrue(prefProfile.rationale.contains("preference", ignoreCase = true))

        val episodicProfile = MemoryRetrievalProfile.forType(MemoryQueryType.EPISODIC)
        assertEquals(0.40, episodicProfile.wRecency, 0.01)
        assertTrue(episodicProfile.rationale.contains("recency", ignoreCase = true))

        val searchProfile = MemoryRetrievalProfile.forType(MemoryQueryType.SEARCH)
        assertEquals(0.40, searchProfile.wOverlap, 0.01)
        assertTrue(searchProfile.rationale.contains("overlap", ignoreCase = true))
    }

    // 3.3 RagRetriever Query Rewriting
    @Test
    fun testRagRetrieverQueryRewriting() = runBlocking {
        val mockLlm = MockLlm("comprehensive battery optimization and power management guide")
        val indexStore = RagIndexStore(com.jarvis.util.MockContext())

        val retriever = RagRetriever(indexStore)
        val result = retriever.retrieveWithQueryRewrite(
            query = "battery",
            limit = 2,
            llm = mockLlm
        )

        assertNotNull(result)
        assertEquals("comprehensive battery optimization and power management guide", result.query)
        assertEquals(1, mockLlm.callCount)
    }

    // 3.4 ClipboardIntelligenceEngine LLM Classification Fallback
    @Test
    fun testClipboardIntelligenceLlmFallback() = runBlocking {
        val mockLlm = MockLlm("""
            {
              "type": "EMAIL_ADDRESS",
              "extracted_value": "sarah.connor@cyberdyne.org",
              "suggested_action": "compose_email",
              "prompt_text": "Copied email address for Sarah Connor. Would you like to draft a message?"
            }
        """.trimIndent())

        val engine = ClipboardIntelligenceEngine(com.jarvis.util.MockContext())
        val analysis = engine.analyzeTextWithReasoning(
            "Please send the schematics to sarah.connor@cyberdyne.org when ready.",
            llm = mockLlm
        )

        assertNotNull(analysis)
        assertEquals("EMAIL_ADDRESS", analysis!!.type)
        assertEquals("sarah.connor@cyberdyne.org", analysis.extractedValue)
        assertEquals(1, mockLlm.callCount)
    }

    // 3.5 BargeInDetector Adaptive Calibration
    @Test
    fun testBargeInDetectorAdaptiveSpeakerCalibration() {
        val detector = BargeInDetector(baseThresholdRms = 380f)
        assertEquals(380f, detector.getEffectiveThreshold(), 0.1f)

        // Calibrate from quiet speaker playback (e.g. 150 RMS) -> 150 * 1.8 = 270
        detector.calibrateFromSpeakerEcho(150f)
        assertEquals(270f, detector.getEffectiveThreshold(), 0.1f)

        // Calibrate from loud speaker playback (e.g. 400 RMS) -> 400 * 1.8 = 720
        detector.calibrateFromSpeakerEcho(400f)
        assertEquals(720f, detector.getEffectiveThreshold(), 0.1f)

        // Bounds check (clamped to 800)
        detector.calibrateFromSpeakerEcho(900f)
        assertEquals(800f, detector.getEffectiveThreshold(), 0.1f)
    }
}
