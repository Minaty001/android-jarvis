package com.jarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridWakeWordDetectorTest {
    private class FakeNeuralDetector(
        var available: Boolean = true,
        private val score: Float = 0.73f
    ) : WakeWordDetector {
        var pushes = 0
        var resets = 0
        var closed = false

        override fun isModelAvailable() = available
        override fun getModelLoadError() = if (available) null else "model unavailable"
        override fun getModelThreshold(): Float? = if (available) 0.7f else null
        override fun pushAudio(samples: ShortArray): Float { pushes++; return score }
        override fun resetDetectorState() { resets++ }
        override fun close() { closed = true }
    }

    @Test
    fun missingNeuralModelDisablesWakeDetection() {
        val detector = HybridWakeWordDetector()
        assertFalse(detector.isModelAvailable())
        assertEquals(WakeDetectorMode.UNAVAILABLE, detector.getDetectorMode())
        assertEquals(0f, detector.pushAudio(ShortArray(16_000) { Short.MAX_VALUE }), 0f)
    }

    @Test
    fun neuralDetectorScoreIsPassedThrough() {
        val neural = FakeNeuralDetector(score = 0.73f)
        val detector = HybridWakeWordDetector(customPrimary = neural)
        assertTrue(detector.isModelAvailable())
        assertEquals(WakeDetectorMode.NEURAL_READY, detector.getDetectorMode())
        assertEquals(0.73f, detector.pushAudio(ShortArray(1_600)), 0f)
        assertEquals(1, neural.pushes)
    }

    @Test
    fun unavailableNeuralDetectorDoesNotFallBackOrReceiveAudio() {
        val neural = FakeNeuralDetector(available = false)
        val detector = HybridWakeWordDetector(customPrimary = neural)
        assertFalse(detector.isModelAvailable())
        assertEquals(0f, detector.pushAudio(ShortArray(16_000) { Short.MAX_VALUE }), 0f)
        assertEquals(0, neural.pushes)
    }

    @Test
    fun resetAndCloseAreDelegatedToNeuralDetector() {
        val neural = FakeNeuralDetector()
        val detector = HybridWakeWordDetector(customPrimary = neural)
        detector.resetDetectorState()
        detector.close()
        assertEquals(1, neural.resets)
        assertTrue(neural.closed)
        assertFalse(detector.isModelAvailable())
    }
}
