package com.jarvis.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbBridgeEngineTest {

    @Test
    fun testParseParamsKeyValueAndDelimiters() {
        val kv = AdbBridgeEngine.parseParams("tool=wake_diagnostics,mode=active")
        assertEquals("wake_diagnostics", kv["tool"])
        assertEquals("active", kv["mode"])

        val colon = AdbBridgeEngine.parseParams("phrase: Hey Jarvis; count: 2")
        assertEquals("Hey Jarvis", colon["phrase"])
        assertEquals("2", colon["count"])

        val empty = AdbBridgeEngine.parseParams(null)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun testPingResponseFormatting() {
        val ping = AdbBridgeEngine.formatPingResponse(null)
        assertTrue("Ping response must start with PONG", ping.startsWith("PONG:"))
        assertTrue("Ping response must contain service state", ping.contains("service="))
        assertTrue("Ping response must contain cores count", ping.contains("cores="))
    }

    @Test
    fun testFormatToolExecutionResult() {
        val success = AdbBridgeEngine.formatToolExecutionResult(true, "Tool executed", mapOf("key" to "value"))
        assertTrue(success.startsWith("STATUS:SUCCESS"))
        assertTrue(success.contains("key=value"))

        val failure = AdbBridgeEngine.formatToolExecutionResult(false, "Unknown tool")
        assertTrue(failure.startsWith("STATUS:FAILED"))
        assertTrue(failure.contains("Unknown tool"))
    }

    @Test
    fun testSupportedActions() {
        assertEquals("com.jarvis.action.PING", AdbBridgeEngine.ACTION_PING)
        assertEquals("com.jarvis.action.EXECUTE_TOOL", AdbBridgeEngine.ACTION_EXECUTE_TOOL)
        assertEquals("com.jarvis.action.EXECUTE_COMMAND", AdbBridgeEngine.ACTION_EXECUTE_COMMAND)
    }
}
