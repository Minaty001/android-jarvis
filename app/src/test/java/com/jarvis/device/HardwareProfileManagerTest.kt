package com.jarvis.device

import org.junit.Assert.*
import org.junit.Test

class HardwareProfileManagerTest {

    @Test
    fun testNullContextReturnsSafeFallbackProfile() {
        val profile = HardwareProfileManager.getProfile(null)
        assertNotNull(profile)
        assertTrue(profile.cpuCores >= 1)
        assertEquals(2, profile.recommendedTfliteThreads)
        assertEquals(DeviceTier.MID_RANGE, profile.tier)
        assertEquals(150, profile.maxMemoryStoreResults)
    }

    @Test
    fun testThreadCountCalculationBounds() {
        assertEquals(1, HardwareProfileManager.calculateOptimalThreads(cores = 1, isLowRam = true))
        assertEquals(2, HardwareProfileManager.calculateOptimalThreads(cores = 2, isLowRam = false))
        assertEquals(2, HardwareProfileManager.calculateOptimalThreads(cores = 4, isLowRam = true))
        assertEquals(3, HardwareProfileManager.calculateOptimalThreads(cores = 8, isLowRam = false))
        assertEquals(1, HardwareProfileManager.calculateOptimalThreads(cores = 0, isLowRam = false))
    }

    @Test
    fun testDeviceTierClassification() {
        val lowEnd = HardwareProfileManager.classifyTier(cores = 2, totalRamBytes = 2L * 1024 * 1024 * 1024, isLowRam = true)
        assertEquals(DeviceTier.LOW_END, lowEnd)

        val midRange = HardwareProfileManager.classifyTier(cores = 6, totalRamBytes = 6L * 1024 * 1024 * 1024, isLowRam = false)
        assertEquals(DeviceTier.MID_RANGE, midRange)

        val flagship = HardwareProfileManager.classifyTier(cores = 8, totalRamBytes = 12L * 1024 * 1024 * 1024, isLowRam = false)
        assertEquals(DeviceTier.FLAGSHIP, flagship)
    }
}
