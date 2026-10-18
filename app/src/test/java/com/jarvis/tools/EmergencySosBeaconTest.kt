package com.jarvis.tools

import com.jarvis.emergency.EmergencySosBeaconEngine
import com.jarvis.foundation.LockScreenSecurityPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmergencySosBeaconTest {

    @Before
    fun setUp() {
        EmergencySosBeaconEngine.stopBeacon()
    }

    @Test
    fun testMorseSosTimingsFollowsStandardDistressPattern() {
        val timings = EmergencySosBeaconEngine.MORSE_SOS_TIMINGS
        assertEquals(18, timings.size)

        // First letter 'S': 3 dots
        assertTrue(timings[0].first) // Dot 1 ON
        assertEquals(150L, timings[0].second)
        assertFalse(timings[1].first) // Dot 1 OFF
        assertEquals(150L, timings[1].second)

        assertTrue(timings[2].first) // Dot 2 ON
        assertEquals(150L, timings[2].second)
        assertFalse(timings[3].first) // Dot 2 OFF
        assertEquals(150L, timings[3].second)

        assertTrue(timings[4].first) // Dot 3 ON
        assertEquals(150L, timings[4].second)
        assertFalse(timings[5].first) // Dot 3 OFF - letter gap
        assertEquals(450L, timings[5].second)

        // Second letter 'O': 3 dashes
        assertTrue(timings[6].first) // Dash 1 ON
        assertEquals(450L, timings[6].second)
        assertFalse(timings[7].first) // Dash 1 OFF
        assertEquals(150L, timings[7].second)

        assertTrue(timings[8].first) // Dash 2 ON
        assertEquals(450L, timings[8].second)
        assertFalse(timings[9].first) // Dash 2 OFF
        assertEquals(150L, timings[9].second)

        assertTrue(timings[10].first) // Dash 3 ON
        assertEquals(450L, timings[10].second)
        assertFalse(timings[11].first) // Dash 3 OFF - letter gap
        assertEquals(450L, timings[11].second)

        // Third letter 'S': 3 dots with final cycle gap
        assertTrue(timings[12].first)
        assertEquals(150L, timings[12].second)
        assertTrue(timings[14].first)
        assertEquals(150L, timings[14].second)
        assertTrue(timings[16].first)
        assertEquals(150L, timings[16].second)
        assertFalse(timings[17].first) // Cycle gap
        assertEquals(1000L, timings[17].second)
    }

    @Test
    fun testFormatSosMessageWithCoordinates() {
        val msg = EmergencySosBeaconEngine.formatSosMessage(28.6139, 77.2090)
        assertTrue(msg.contains("EMERGENCY SOS!"))
        assertTrue(msg.contains("https://maps.google.com/?q=28.6139,77.209"))
    }

    @Test
    fun testFormatSosMessageWithoutCoordinates() {
        val msg = EmergencySosBeaconEngine.formatSosMessage(null, null)
        assertTrue(msg.contains("EMERGENCY SOS!"))
        assertTrue(msg.contains("Location unavailable (GPS offline)"))
    }

    @Test
    fun testStartAndStopBeaconState() = runBlocking {
        assertFalse(EmergencySosBeaconEngine.isBeaconActive())

        val startRes = EmergencySosBeaconEngine.startBeacon(null, contactNumber = "911")
        assertTrue(startRes.success)
        assertTrue(EmergencySosBeaconEngine.isBeaconActive())

        val stopped = EmergencySosBeaconEngine.stopBeacon()
        assertTrue(stopped)
        assertFalse(EmergencySosBeaconEngine.isBeaconActive())
    }

    @Test
    fun testEmergencySosToolExecution() = runBlocking {
        val tool = EmergencySosTool(null)

        val resStart = tool.execute(mapOf("action" to "start"))
        assertTrue(resStart.success)
        assertTrue(resStart.message.contains("Emergency SOS Beacon activated"))
        assertTrue(EmergencySosBeaconEngine.isBeaconActive())

        val resStatus = tool.execute(mapOf("action" to "status"))
        assertTrue(resStatus.success)
        assertEquals("true", resStatus.data["active"])

        val resStop = tool.execute(mapOf("action" to "stop"))
        assertTrue(resStop.success)
        assertTrue(resStop.message.contains("Emergency SOS Beacon stopped"))
        assertFalse(EmergencySosBeaconEngine.isBeaconActive())
    }

    @Test
    fun testLockScreenSecurityPolicyPermitsEmergencySos() {
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("EMERGENCY_SOS", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SOS", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("STOP_SOS", emptyMap()))
    }
}
