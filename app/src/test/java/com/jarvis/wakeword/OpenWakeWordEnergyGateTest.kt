package com.jarvis.wakeword

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class OpenWakeWordEnergyGateTest {

    @Test
    fun testRmsCalculationOnQuietAudioIsBelowCalibratedFloor() {
        // Typical quiet room noise: ~0.0004 - 0.0008 RMS
        val quietAudio = ShortArray(1280) { 20.toShort() } // 20 / 32768 ~ 0.00061 RMS
        var sum = 0.0
        quietAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / quietAudio.size)
        val config = WakeWordConfig()
        assertTrue("Quiet room audio RMS ($rms) must be below ${config.minEnergyRms} floor", rms < config.minEnergyRms)
    }

    @Test
    fun testRmsCalculationOnMobileSpeechAudioExceedsCalibratedFloor() {
        // Conversational speech at 1-2 ft typically yields ~60-120 amplitude on mobile mic (~0.0020 - 0.0036 RMS)
        val speechAudio = ShortArray(1280) { 70.toShort() } // 70 / 32768 ~ 0.00214 RMS
        var sum = 0.0
        speechAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / speechAudio.size)
        val config = WakeWordConfig()
        assertTrue("Speech audio RMS ($rms) must exceed ${config.minEnergyRms} floor", rms >= config.minEnergyRms)
    }

    @Test
    fun testDefaultConfigCalibratedForMobileMicrophones() {
        val config = WakeWordConfig()
        assertEquals("Default minEnergyRms should be 0.00200 for ambient room rejection", 0.00200, config.minEnergyRms, 0.00001)
        assertEquals("Default silenceFramesBeforeReset should be 2 (~160ms decay)", 2, config.silenceFramesBeforeReset)
        assertEquals("Default detectionCooldownMs should be 2000ms", 2000L, config.detectionCooldownMs)
    }

    @Test
    fun testSilenceDecayWindowLogic() {
        val confirmation = KwsTemporalConfirmation(windowCount = 2, requiredPositiveWindows = 2)
        // Simulate speech frame producing a positive confirmation score
        confirmation.add(1.1f, 1.0f)

        var consecutiveSilenceFrames = 0
        val maxSilenceFrames = 2

        // 1 consecutive silence frame (< 2) should NOT trigger reset
        for (i in 1..1) {
            consecutiveSilenceFrames++
            val shouldReset = consecutiveSilenceFrames >= maxSilenceFrames
            assertFalse("Brief silence frame ($i) must not reset confirmation", shouldReset)
        }

        // 1 more silence frame reaches threshold (2) -> triggers reset
        consecutiveSilenceFrames++
        val shouldReset = consecutiveSilenceFrames >= maxSilenceFrames
        assertTrue("2 consecutive silence frames must trigger reset", shouldReset)
        if (shouldReset) {
            confirmation.reset()
        }
    }
}
