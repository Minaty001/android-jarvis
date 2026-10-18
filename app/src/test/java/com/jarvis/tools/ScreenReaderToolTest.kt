package com.jarvis.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScreenReaderToolTest {

    @Test
    fun testScreenReaderMetadata() {
        val tool = ScreenReaderTool()
        assertEquals("SCREEN_READER", tool.name)
        assertTrue(tool.description.contains("on-screen text", ignoreCase = true))
    }

    @Test
    fun testScreenReaderStopAction() = runBlocking {
        val tool = ScreenReaderTool()
        val result = tool.execute(mapOf("action" to "stop"))
        assertTrue(result.success)
        assertTrue(result.message.contains("stopped", ignoreCase = true))
    }

    @Test
    fun testScreenReaderWithoutAccessibilityService() = runBlocking {
        val tool = ScreenReaderTool()
        val result = tool.execute(mapOf("action" to "read"))
        // In unit test environment without running service, fails gracefully with descriptive message
        assertFalse(result.success)
        assertTrue(result.message.contains("Accessibility Service", ignoreCase = true))
    }
}
