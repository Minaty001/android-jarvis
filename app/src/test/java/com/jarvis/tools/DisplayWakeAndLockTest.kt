package com.jarvis.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayWakeAndLockTest {

    @Test
    fun testWakeDisplayDefaultTimeoutIsCappedAt10Seconds() {
        val res = DisplayWakeEngine.wakeDisplay(null)
        assertTrue(res.success)
        assertEquals("10000", res.data["timeout_ms"])
    }

    @Test
    fun testWakeDisplayCapsExcessiveTimeoutForBatterySafety() {
        val res = DisplayWakeEngine.wakeDisplay(null, timeoutMs = 45000L)
        assertTrue(res.success)
        assertEquals("10000", res.data["timeout_ms"])
    }

    @Test
    fun testWakeDisplayPreservesShorterTimeout() {
        val res = DisplayWakeEngine.wakeDisplay(null, timeoutMs = 3500L)
        assertTrue(res.success)
        assertEquals("3500", res.data["timeout_ms"])
    }

    @Test
    fun testLockDisplayExecutesCleanlyInSimulation() = runBlocking {
        val res = DisplayWakeEngine.lockDisplay(null)
        assertTrue(res.success)
        assertEquals("lock", res.data["action"])
    }

    @Test
    fun testScreenLockToolDelegatesToDisplayWakeEngine() = runBlocking {
        val tool = ScreenLockTool(null)
        val res = tool.execute(emptyMap())
        assertTrue(res.success)
    }

    @Test
    fun testScreenUnlockToolWakeAction() = runBlocking {
        val tool = ScreenUnlockTool(null)
        val res = tool.execute(mapOf("action" to "wake"))
        assertTrue(res.success)
        assertTrue(res.message.contains("woken up") || res.message.contains("wake"))
    }
}
