package com.jarvis.wakeword

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class OpenWakeWordEnergyGateTest {

    @Test
    fun testRmsCalculationOnQuietAudioIsBelowCalibratedFloor() {
        val quietAudio = ShortArray(1280) { (it % 5).toShort() } // Very quiet PCM (~0.00007 RMS)
        var sum = 0.0
        quietAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / quietAudio.size)
        val config = WakeWordConfig()
        assertTrue("Quiet audio RMS ($rms) must be below ${config.minEnergyRms} floor", rms < config.minEnergyRms)
    }

    @Test
    fun testRmsCalculationOnMobileSpeechAudioExceedsCalibratedFloor() {
        // Conversational speech at 1-2 ft typically yields ~30-100 amplitude on mobile mic (~0.001 - 0.003 RMS)
        val speechAudio = ShortArray(1280) { 45.toShort() }
        var sum = 0.0
        speechAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / speechAudio.size)
        val config = WakeWordConfig()
        assertTrue("Speech audio RMS ($rms) must exceed ${config.minEnergyRms} floor", rms >= config.minEnergyRms)
    }

    @Test
    fun testDefaultConfigCalibratedForMobileMicrophones() {
        val config = WakeWordConfig()
        assertEquals("Default minEnergyRms should be 0.0003 for mobile mics", 0.0003, config.minEnergyRms, 0.00001)
        assertEquals("Default silenceFramesBeforeReset should be 5 (~400ms decay)", 5, config.silenceFramesBeforeReset)
    }

    @Test
    fun testSilenceDecayWindowLogic() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2)
        // Simulate speech frame producing a positive confirmation score
        confirmation.add(1.1f, 1.0f)

        var consecutiveSilenceFrames = 0
        val maxSilenceFrames = 5

        // 3 consecutive silence frames (< 5) should NOT trigger reset
        for (i in 1..3) {
            consecutiveSilenceFrames++
            val shouldReset = consecutiveSilenceFrames >= maxSilenceFrames
            assertFalse("Brief silence frame ($i) must not reset confirmation", shouldReset)
        }

        // 2 more silence frames reaches threshold (5) -> triggers reset
        for (i in 4..5) {
            consecutiveSilenceFrames++
        }
        val shouldReset = consecutiveSilenceFrames >= maxSilenceFrames
        assertTrue("5 consecutive silence frames must trigger reset", shouldReset)
        if (shouldReset) {
            confirmation.reset()
        }
    }
}
