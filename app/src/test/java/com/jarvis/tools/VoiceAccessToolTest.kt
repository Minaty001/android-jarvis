package com.jarvis.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoiceAccessToolTest {

    @Test
    fun testVoiceAccessToolMetadata() {
        val tool = VoiceAccessTool()
        assertEquals("VOICE_ACCESS", tool.name)
        assertTrue(tool.description.contains("numbered badges", ignoreCase = true))
        assertEquals(2, tool.metadata.parameters.size)
    }

    @Test
    fun testVoiceAccessToolExecutionWithoutContext() = runBlocking {
        val tool = VoiceAccessTool(null)
        val showResult = tool.execute(mapOf("action" to "show"))
        assertFalse(showResult.success)
        assertTrue(showResult.message.contains("Context required", ignoreCase = true))

        val hideResult = tool.execute(mapOf("action" to "hide"))
        assertTrue(hideResult.success)

        val clickNoNumber = tool.execute(mapOf("action" to "click"))
        assertFalse(clickNoNumber.success)
        assertTrue(clickNoNumber.message.contains("Specify a valid badge number", ignoreCase = true))

        val clickMissing = tool.execute(mapOf("action" to "click", "number" to "99"))
        assertFalse(clickMissing.success)
        assertTrue(clickMissing.message.contains("not found", ignoreCase = true))
    }
}
