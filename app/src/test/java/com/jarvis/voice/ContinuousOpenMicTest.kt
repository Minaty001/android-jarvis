package com.jarvis.voice

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.conversation.ConversationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousOpenMicTest {

    private val conversationManager = ConversationManager()

    @Test
    fun testOpenMicExitUtterancesRecognized() {
        val exitPhrases = listOf(
            "jarvis sleep",
            "sleep",
            "go to sleep",
            "stop listening",
            "stop open mic",
            "exit open mic",
            "open mic band karo",
            "chup ho jao",
            "so jao"
        )
        for (phrase in exitPhrases) {
            assertTrue("Expected '$phrase' to be recognized as an exit utterance",
                conversationManager.isExitUtterance(phrase, "Jarvis"))
        }
    }

    @Test
    fun testOpenMicExitResponses() {
        val sleepResponse = conversationManager.getExitResponse("jarvis sleep")
        assertTrue(sleepResponse.contains("standby", ignoreCase = true) || sleepResponse.contains("sleep", ignoreCase = true))

        val openMicResponse = conversationManager.getExitResponse("stop open mic")
        assertTrue(openMicResponse.contains("open mic", ignoreCase = true) || openMicResponse.contains("standby", ignoreCase = true))
    }

    @Test
    fun testIntentResolverOpenMicStart() {
        val utterances = listOf("open mic", "turn on open mic", "start open mic", "walkie talkie mode", "continuous listening")
        for (u in utterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be AMBIENT_OPEN_MIC for '$u'", AssistantIntent.AMBIENT_OPEN_MIC, resolved?.intent)
            assertEquals("Action should be start for '$u'", "start", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testIntentResolverOpenMicStop() {
        val utterances = listOf("stop open mic", "turn off open mic", "exit open mic", "jarvis sleep", "go to sleep")
        for (u in utterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be AMBIENT_OPEN_MIC for '$u'", AssistantIntent.AMBIENT_OPEN_MIC, resolved?.intent)
            assertEquals("Action should be stop for '$u'", "stop", resolved?.params?.get("action"))
        }
    }
}
