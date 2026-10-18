package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTypingToolTest {

    @Test
    fun testIntentResolverTypeAndDictate() {
        val typeCases = listOf(
            "type hello world" to "hello world",
            "write thank you very much" to "thank you very much",
            "dictate I will be there in 5 minutes" to "I will be there in 5 minutes"
        )
        for ((utterance, expectedText) in typeCases) {
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals(AssistantIntent.VOICE_TYPING, resolved?.intent)
            assertEquals("type", resolved?.params?.get("action"))
            assertEquals(expectedText.lowercase(), resolved?.params?.get("text")?.lowercase())
        }
    }

    @Test
    fun testIntentResolverBackspaceAndClear() {
        val backspaceResolved = IntentResolver.resolve("backspace")
        assertNotNull(backspaceResolved)
        assertEquals(AssistantIntent.VOICE_TYPING, backspaceResolved?.intent)
        assertEquals("backspace", backspaceResolved?.params?.get("action"))

        val clearResolved = IntentResolver.resolve("clear text")
        assertNotNull(clearResolved)
        assertEquals(AssistantIntent.VOICE_TYPING, clearResolved?.intent)
        assertEquals("clear", clearResolved?.params?.get("action"))
    }

    @Test
    fun testIntentResolverPressSendOrEnter() {
        val sendResolved = IntentResolver.resolve("press send")
        assertNotNull(sendResolved)
        assertEquals(AssistantIntent.VOICE_TYPING, sendResolved?.intent)
        assertEquals("send", sendResolved?.params?.get("action"))

        val enterResolved = IntentResolver.resolve("press enter")
        assertNotNull(enterResolved)
        assertEquals(AssistantIntent.VOICE_TYPING, enterResolved?.intent)
        assertEquals("send", enterResolved?.params?.get("action"))
    }

    @Test
    fun testVoiceTypingToolExecutionWithoutService() = runBlocking {
        val tool = VoiceTypingTool(context = null)
        val result = tool.execute(mapOf("action" to "type", "text" to "testing"))
        assertTrue("Execution should handle unbound service gracefully", result is ToolResult.Success || result is ToolResult.Failed)
    }
}
