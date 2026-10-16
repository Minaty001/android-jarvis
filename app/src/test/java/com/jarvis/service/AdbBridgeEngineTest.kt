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
    fun testFormatToolExecutionResultWithAcousticDiagnostics() {
        val diag = mapOf(
            "mode" to "NEURAL_READY",
            "modelAvailable" to true,
            "latestScore" to "0.750",
            "threshold" to "0.50",
            "audioRms" to "0.00043",
            "energyFloor" to "0.00030",
            "silenceDecayLimit" to 5
        )
        val result = AdbBridgeEngine.formatToolExecutionResult(true, "Wake word telemetry captured", diag)
        assertTrue(result.contains("energyFloor=0.00030"))
        assertTrue(result.contains("silenceDecayLimit=5"))
        assertTrue(result.contains("threshold=0.50"))
    }

    @Test
    fun testParseInjectAudioParams() {
        val params = AdbBridgeEngine.parseParams("type=sine,samples=1280,amplitude=1500,freq=300.0")
        assertEquals("sine", params["type"])
        assertEquals("1280", params["samples"])
        assertEquals("1500", params["amplitude"])
        assertEquals("300.0", params["freq"])
    }

    @Test
    fun testSupportedActions() {
        assertEquals("com.jarvis.action.PING", AdbBridgeEngine.ACTION_PING)
        assertEquals("com.jarvis.action.EXECUTE_TOOL", AdbBridgeEngine.ACTION_EXECUTE_TOOL)
        assertEquals("com.jarvis.action.EXECUTE_COMMAND", AdbBridgeEngine.ACTION_EXECUTE_COMMAND)
    }
}
