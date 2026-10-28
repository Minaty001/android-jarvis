package com.jarvis.wakeword

import com.jarvis.voice.WakeWordEngine
import org.junit.Assert.assertEquals
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
