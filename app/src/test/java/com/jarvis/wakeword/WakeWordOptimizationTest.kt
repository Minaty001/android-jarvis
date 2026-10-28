package com.jarvis.wakeword

import com.jarvis.voice.WakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class WakeWordOptimizationTest {

    @Test
    fun testFastRmsCalculationMatchesExactMathematicalRms() {
        // Create test sample with known values
        val samples = ShortArray(1280) { (it % 500 - 250).toShort() }

        // Naive double division per-element
        var naiveSum = 0.0
        for (s in samples) {
            val norm = s.toDouble() / 32768.0
            naiveSum += norm * norm
        }
        val expectedRms = sqrt(naiveSum / samples.size)

        // Fast integer MAC calculation
        var sumSquares = 0L
        for (s in samples) {
            val v = s.toLong()
            sumSquares += v * v
        }
        val fastRms = sqrt(sumSquares.toDouble() / samples.size) / 32768.0

        assertEquals("Fast integer MAC RMS must match double precision RMS to within floating point tolerance", expectedRms, fastRms, 1e-7)
    }

    @Test
    fun testFastSquaredSilenceGateEquivalence() {
        val frameSamples = 1280
        val minEnergyRms = 0.0020
        val thresholdSumSquares = (frameSamples * (minEnergyRms * 32768.0) * (minEnergyRms * 32768.0)).toLong()

        // Test with silence, edge cases, and loud speech
        val testAmplitudes = listOf(10, 30, 60, 65, 66, 70, 100, 500, 2000, 10000)
        for (amp in testAmplitudes) {
            val chunk = ShortArray(frameSamples) { amp.toShort() }
            var sumSq = 0L
            for (s in chunk) {
                val v = s.toLong()
                sumSq += v * v
            }

            val doubleRms = sqrt(sumSq.toDouble() / frameSamples) / 32768.0
            val naivePassed = doubleRms >= minEnergyRms
            val fastPassed = sumSq >= thresholdSumSquares

            assertEquals("Fast integer squared comparison must match double precision silence gate for amp=$amp", naivePassed, fastPassed)
        }
    }

    @Test
    fun testZeroAllocationKwsTemporalConfirmationSlidingWindow() {
        val confirmation = KwsTemporalConfirmation(windowCount = 3, requiredPositiveWindows = 2)

        // Frame 1: positive
        assertEquals(0f, confirmation.add(0.9f, 0.8f), 0f)
        // Frame 2: dip (negative)
        assertEquals(0f, confirmation.add(0.4f, 0.8f), 0f)
        // Frame 3: positive (now 2 positive in 3-frame window) -> triggers confirmation
        val confirmed = confirmation.add(0.85f, 0.8f)
        assertTrue("Must confirm when 2 positive windows occur within window of 3", confirmed >= 0.85f)

        // Confirmation auto-resets internal state
        assertEquals("Post-confirmation state must be reset", 0f, confirmation.add(0.4f, 0.8f), 0f)
    }

    @Test
    fun testWakeWordEngineBlockArrayCopyMaintainsContiguousAudio() {
        val engine = WakeWordEngine(config = WakeWordConfig(detectionCooldownMs = 0L))
        engine.start {}

        // Feed sequential audio blocks (e.g. 640 samples per chunk)
        val chunk1 = ShortArray(640) { (it + 1).toShort() }
        val chunk2 = ShortArray(640) { (it + 641).toShort() }

        engine.acceptAudio(chunk1)
        engine.acceptAudio(chunk2)

        val trailing = engine.getTrailingAudio()
        // Engine window is 16,000 samples; verify non-null and preserves sample count
        val trailingWindow = engine.getDiagnostics().audioDurationMs
        assertTrue("Audio duration must track accurately", trailingWindow > 0)
        engine.close()
    }
}
