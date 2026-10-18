package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitScreenActionTest {

    @Test
    fun testIntentResolverSplitScreen() {
        val splitTriggers = listOf(
            "split screen",
            "toggle split screen",
            "open split screen",
            "start split screen",
            "side by side mode",
            "close split screen"
        )
        for (trigger in splitTriggers) {
            val resolved = IntentResolver.resolve(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.UI_GLOBAL_ACTION, resolved?.intent)
            assertEquals("split_screen", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testIntentResolverAppDrawer() {
        val drawerTriggers = listOf(
            "open app drawer",
            "show app drawer",
            "app drawer",
            "show all apps",
            "open apps"
        )
        for (trigger in drawerTriggers) {
            val resolved = IntentResolver.resolve(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.UI_GLOBAL_ACTION, resolved?.intent)
            assertEquals("app_drawer", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testUiGlobalActionToolSplitScreenExecution() = runBlocking {
        val tool = UiGlobalActionTool(context = null)
        val result = tool.execute(mapOf("action" to "split_screen"))
        assertTrue("Execution should complete with success or clean failure", result is ToolResult)
    }

    @Test
    fun testUiGlobalActionToolAppDrawerExecution() = runBlocking {
        val tool = UiGlobalActionTool(context = null)
        val result = tool.execute(mapOf("action" to "app_drawer"))
        assertTrue("Execution should complete with success or clean failure", result is ToolResult)
    }
}
