package com.jarvis.voice

import com.jarvis.wakeword.WakeWordConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordSensitivityTest {

    @Test
    fun testWakeWordConfigFromSensitivityHigh() {
        val config = WakeWordConfig.fromSensitivity("HIGH", isLowEnd = false)
        assertEquals(0.00080, config.minEnergyRms, 0.00001)
        assertEquals(4, config.confirmationWindowCount)
        assertEquals(2, config.requiredPositiveWindows)
        assertEquals(3, config.silenceFramesBeforeReset)
    }

    @Test
    fun testWakeWordConfigFromSensitivityBalanced() {
        val config = WakeWordConfig.fromSensitivity("BALANCED", isLowEnd = false)
        assertEquals(0.00150, config.minEnergyRms, 0.00001)
        assertEquals(4, config.confirmationWindowCount)
        assertEquals(2, config.requiredPositiveWindows)
        assertEquals(3, config.silenceFramesBeforeReset)
    }

    @Test
    fun testWakeWordConfigFromSensitivityLow() {
        val config = WakeWordConfig.fromSensitivity("LOW", isLowEnd = false)
        assertEquals(0.00300, config.minEnergyRms, 0.00001)
        assertEquals(5, config.confirmationWindowCount)
        assertEquals(3, config.requiredPositiveWindows)
        assertEquals(3, config.silenceFramesBeforeReset)
    }

    @Test
    fun testWakeWordConfigFromSensitivityLowEndDeviceScaling() {
        val configNormal = WakeWordConfig.fromSensitivity("BALANCED", isLowEnd = false)
        val configLowEnd = WakeWordConfig.fromSensitivity("BALANCED", isLowEnd = true)
        assertTrue(
            "Low-end device floor (${configLowEnd.minEnergyRms}) must be lower than normal (${configNormal.minEnergyRms})",
            configLowEnd.minEnergyRms < configNormal.minEnergyRms
        )
        assertEquals(configNormal.minEnergyRms * 0.85, configLowEnd.minEnergyRms, 0.00001)
    }

    @Test
    fun testWakeWordEngineSetSensitivityUpdatesConfig() {
        val engine = WakeWordEngine(context = null)
        engine.setSensitivity("HIGH")
        assertEquals(0.00080, engine.getConfig().minEnergyRms, 0.00001)

        engine.setSensitivity("LOW")
        assertEquals(0.00300, engine.getConfig().minEnergyRms, 0.00001)
    }
}
