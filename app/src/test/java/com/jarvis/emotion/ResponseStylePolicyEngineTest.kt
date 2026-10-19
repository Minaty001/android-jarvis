package com.jarvis.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResponseStylePolicyEngineTest {

    private lateinit var policyEngine: ResponseStylePolicyEngine

    @Before
    fun setUp() {
        policyEngine = ResponseStylePolicyEngine()
    }

    @Test
    fun testFocusedResponseStyle() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val style = policyEngine.resolveResponseStyle(snapshot)
        assertEquals(AssistantState.FOCUSED, style.state)
        assertEquals("concise", style.verbosity)
        assertEquals("normal", style.urgency)
        assertTrue(style.interruptible)
        assertTrue(style.promptInstruction.contains("concisely"))
    }

    @Test
    fun testHappyResponseStyle() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.HAPPY)
        val style = policyEngine.resolveResponseStyle(snapshot)
        assertEquals(AssistantState.HAPPY, style.state)
        assertEquals("warm_professional", style.empathyStyle)
        assertTrue(style.promptInstruction.contains("satisfaction") || style.promptInstruction.contains("verified"))
    }

    @Test
    fun testSadResponseStyle() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.SAD)
        val style = policyEngine.resolveResponseStyle(snapshot)
        assertEquals(AssistantState.SAD, style.state)
        assertEquals("supportive_empathetic", style.empathyStyle)
        assertFalse(style.humorAllowed)
        assertTrue(style.promptInstruction.contains("empathy") || style.promptInstruction.contains("supportive"))
    }

    @Test
    fun testKiddingResponseStyle() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.KIDDING)
        val style = policyEngine.resolveResponseStyle(snapshot)
        assertEquals(AssistantState.KIDDING, style.state)
        assertEquals("restrained_wit", style.empathyStyle)
        assertTrue(style.humorAllowed)
        assertTrue(style.promptInstruction.contains("humorous") || style.promptInstruction.contains("restrained"))
    }

    @Test
    fun testPanicResponseStyle() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.PANIC)
        val style = policyEngine.resolveResponseStyle(snapshot)
        assertEquals(AssistantState.PANIC, style.state)
        assertEquals("critical", style.urgency)
        assertFalse(style.interruptible)
        assertFalse(style.humorAllowed)
        assertTrue(style.promptInstruction.contains("Urgent") || style.promptInstruction.contains("warning"))
    }

    @Test
    fun testThinkingResponseStyle() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.THINKING)
        val style = policyEngine.resolveResponseStyle(snapshot)
        assertEquals(AssistantState.THINKING, style.state)
        assertTrue(style.allowProgressMessage)
        assertFalse(style.humorAllowed)
    }
}
