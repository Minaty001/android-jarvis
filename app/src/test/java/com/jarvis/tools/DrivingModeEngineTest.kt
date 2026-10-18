package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.automotive.DrivingModeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingModeEngineTest {

    @Test
    fun testIntentResolverDrivingMode() {
        val startTriggers = listOf(
            "start driving mode",
            "enable driving mode",
            "driving mode on",
            "i am driving",
            "car mode"
        )
        for (trigger in startTriggers) {
            val resolved = IntentResolver.resolve(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.DRIVING_MODE, resolved?.intent)
            assertEquals("start", resolved?.params?.get("action"))
        }

        val stopTriggers = listOf(
            "stop driving mode",
            "disable driving mode",
            "driving mode off",
            "i have arrived",
            "exit car mode"
        )
        for (trigger in stopTriggers) {
            val resolved = IntentResolver.resolve(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.DRIVING_MODE, resolved?.intent)
            assertEquals("stop", resolved?.params?.get("action"))
        }

        val statusResolved = IntentResolver.resolve("driving mode status")
        assertNotNull(statusResolved)
        assertEquals(AssistantIntent.DRIVING_MODE, statusResolved?.intent)
        assertEquals("status", statusResolved?.params?.get("action"))
    }

    @Test
    fun testDrivingModeEngineAndToolExecution() = runBlocking {
        val engine = DrivingModeEngine(context = null)
        val tool = DrivingModeTool(context = null, drivingModeEngine = engine)

        assertFalse("Initially driving mode should be inactive", engine.isEnabled())

        // 1. Start driving mode
        val startResult = tool.execute(mapOf("action" to "start"))
        assertTrue("Start driving mode should succeed", startResult is ToolResult.Success)
        assertTrue("Engine should be enabled", engine.isEnabled())

        // 2. Check status
        val statusResult = tool.execute(mapOf("action" to "status"))
        assertTrue("Status check should succeed", statusResult is ToolResult.Success)

        // 3. Stop driving mode
        val stopResult = tool.execute(mapOf("action" to "stop"))
        assertTrue("Stop driving mode should succeed", stopResult is ToolResult.Success)
        assertFalse("Engine should be disabled", engine.isEnabled())
    }
}
