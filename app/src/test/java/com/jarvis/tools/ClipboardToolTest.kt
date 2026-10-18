package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClipboardToolTest {

    @Test
    fun testIntentResolverClipboard() {
        val readClip = IntentResolver.resolve("read clipboard")
        assertNotNull(readClip)
        assertEquals(AssistantIntent.CLIPBOARD_CONTROL, readClip?.intent)
        assertEquals("read", readClip?.params?.get("action"))

        val pasteText = IntentResolver.resolve("paste clipboard")
        assertNotNull(pasteText)
        assertEquals(AssistantIntent.CLIPBOARD_CONTROL, pasteText?.intent)
        assertEquals("paste", pasteText?.params?.get("action"))

        val pasteOtp = IntentResolver.resolve("paste otp")
        assertNotNull(pasteOtp)
        assertEquals(AssistantIntent.CLIPBOARD_CONTROL, pasteOtp?.intent)
        assertEquals("paste_otp", pasteOtp?.params?.get("action"))
    }

    @Test
    fun testClipboardToolExecutionWithoutContext() = runBlocking {
        val tool = ClipboardTool(context = null)
        val readRes = tool.execute(mapOf("action" to "read"))
        assertTrue(readRes is ToolResult)

        val pasteOtpRes = tool.execute(mapOf("action" to "paste_otp"))
        assertTrue(pasteOtpRes is ToolResult)
    }
}
