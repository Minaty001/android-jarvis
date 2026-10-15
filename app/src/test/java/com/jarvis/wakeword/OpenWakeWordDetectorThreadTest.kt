package com.jarvis.wakeword

import com.jarvis.device.HardwareProfileManager
import org.junit.Assert.*
import org.junit.Test

class OpenWakeWordDetectorThreadTest {

    @Test
    fun testOpenWakeWordDeterminesSafeThreadCount() {
        val threads = HardwareProfileManager.getRecommendedTfliteThreads(null)
        assertTrue("Thread count must be bounded between 1 and 2 for constrained devices", threads in 1..2)
    }

    @Test
    fun testThreadCountDoesNotExceedAvailableProcessors() {
        val available = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val recommended = HardwareProfileManager.calculateOptimalThreads(available, isLowRam = false)
        assertTrue("Recommended threads ($recommended) must not exceed available CPU cores ($available)", recommended <= available)
    }
}
