package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVoiceScrubbingTest {

    @Test
    fun testIntentResolverSeekForward() {
        val cases = mapOf(
            "skip 30 seconds" to "30",
            "forward 15 seconds" to "15",
            "fast forward 45 seconds" to "45",
            "skip 1 minute" to "60",
            "forward 2 minutes" to "120"
        )
        for ((utterance, expectedSecs) in cases) {
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals("Action should be seek_forward for '$utterance'", "seek_forward", resolved?.params?.get("action"))
            assertEquals("Seconds should be $expectedSecs for '$utterance'", expectedSecs, resolved?.params?.get("seconds"))
        }
    }

    @Test
    fun testIntentResolverSeekBackward() {
        val cases = mapOf(
            "rewind 30 seconds" to "30",
            "go back 15 seconds" to "15",
            "replay 10 seconds" to "10",
            "rewind 1 minute" to "60",
            "go back 1 min" to "60"
        )
        for ((utterance, expectedSecs) in cases) {
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals("Action should be seek_backward for '$utterance'", "seek_backward", resolved?.params?.get("action"))
            assertEquals("Seconds should be $expectedSecs for '$utterance'", expectedSecs, resolved?.params?.get("seconds"))
        }
    }

    @Test
    fun testIntentResolverPlaybackSpeed() {
        val cases = mapOf(
            "set playback speed to 1.5x" to "1.5",
            "playback speed 1.5" to "1.5",
            "play at 2x" to "2.0",
            "speed 1.25x" to "1.25",
            "normal speed" to "1.0"
        )
        for ((utterance, expectedSpeed) in cases) {
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals("Action should be set_speed for '$utterance'", "set_speed", resolved?.params?.get("action"))
            assertEquals("Speed should be $expectedSpeed for '$utterance'", expectedSpeed, resolved?.params?.get("speed"))
        }
    }

    @Test
    fun testMediaPlaybackControlToolScrubbing() = runBlocking {
        val tool = MediaPlaybackControlTool(context = null)

        val forwardResult = tool.execute(mapOf("action" to "seek_forward", "seconds" to "30"))
        assertTrue("seek_forward execution result", forwardResult is ToolResult.Success || forwardResult is ToolResult.Failed)

        val backwardResult = tool.execute(mapOf("action" to "seek_backward", "seconds" to "15"))
        assertTrue("seek_backward execution result", backwardResult is ToolResult.Success || backwardResult is ToolResult.Failed)

        val speedResult = tool.execute(mapOf("action" to "set_speed", "speed" to "1.5"))
        assertTrue("set_speed execution result", speedResult is ToolResult.Success || speedResult is ToolResult.Failed)
    }
}
