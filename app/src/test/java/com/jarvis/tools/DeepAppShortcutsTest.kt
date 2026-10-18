package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeepAppShortcutsTest {

    @Test
    fun testIntentResolverAppSearchShortcuts() {
        val ytSearch = IntentResolver.resolve("search cat videos on youtube")
        assertNotNull(ytSearch)
        assertEquals(AssistantIntent.APP_OPEN, ytSearch?.intent)
        assertEquals("youtube", ytSearch?.params?.get("app"))
        assertEquals("cat videos", ytSearch?.params?.get("query"))

        val googleSearch = IntentResolver.resolve("search quantum physics on chrome")
        assertNotNull(googleSearch)
        assertEquals(AssistantIntent.APP_OPEN, googleSearch?.intent)
        assertEquals("chrome", googleSearch?.params?.get("app"))
        assertEquals("quantum physics", googleSearch?.params?.get("query"))
    }

    @Test
    fun testOpenAppToolSearchExecutionWithoutContext() = runBlocking {
        val tool = OpenAppTool(context = null)
        val res = tool.execute(mapOf("app" to "youtube", "query" to "ambient music", "action" to "search_in_app"))
        assertTrue(res is ToolResult)
    }
}
