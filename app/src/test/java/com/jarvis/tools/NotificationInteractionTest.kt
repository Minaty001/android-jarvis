package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationInteractionTest {

    @Test
    fun testIntentResolverNotificationActions() {
        val readSecond = IntentResolver.resolve("read notification 2")
        assertNotNull(readSecond)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, readSecond?.intent)
        assertEquals("read_item", readSecond?.params?.get("action"))
        assertEquals("2", readSecond?.params?.get("index"))

        val clearAll = IntentResolver.resolve("clear all notifications")
        assertNotNull(clearAll)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, clearAll?.intent)
        assertEquals("dismiss_all", clearAll?.params?.get("action"))

        val replyNotif = IntentResolver.resolve("reply to notification 1")
        assertNotNull(replyNotif)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, replyNotif?.intent)
        assertEquals("trigger_action", replyNotif?.params?.get("action"))
        assertEquals("1", replyNotif?.params?.get("index"))
    }

    @Test
    fun testNotificationsToolExecutionWithoutService() = runBlocking {
        val tool = NotificationsTool(context = null)
        val readRes = tool.execute(mapOf("action" to "read_item", "index" to "1"))
        assertTrue(readRes is ToolResult)

        val clearRes = tool.execute(mapOf("action" to "dismiss_all"))
        assertTrue(clearRes is ToolResult)
    }
}
