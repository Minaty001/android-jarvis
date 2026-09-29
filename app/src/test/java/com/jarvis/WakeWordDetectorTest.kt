package com.jarvis

import com.jarvis.voice.JarvisWakeGate
import com.jarvis.voice.WakeWordEngine
import com.jarvis.wakeword.HybridWakeWordDetector
import com.jarvis.wakeword.KwsTemporalConfirmation
import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.WakeWordConfig
import com.jarvis.wakeword.WakeWordDetector
import com.jarvis.wakeword.WakeWordModelContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectorTest {
    private class FakeDetector(
        private var available: Boolean = true,
        private var score: Float = 0.9f,
        private val threshold: Float = 0.7f
    ) : WakeWordDetector {
        var audioSizes = mutableListOf<Int>()
        override fun isModelAvailable() = available
        override fun getModelLoadError() = if (available) null else "model missing"
        override fun getModelThreshold() = threshold.takeIf { available }
        override fun pushAudio(samples: ShortArray): Float { audioSizes += samples.size; return score }
        override fun resetDetectorState() = Unit
        override fun close() { available = false }
    }

    @Test
    fun missingNeuralModelDoesNotFallbackToAcousticDetection() {
        val hybrid = HybridWakeWordDetector(context = null)
        assertFalse(hybrid.isModelAvailable())
        assertEquals(0f, hybrid.pushAudio(ShortArray(16_000) { 10_000 }), 0f)
        assertEquals(com.jarvis.wakeword.WakeDetectorMode.UNAVAILABLE, hybrid.getDetectorMode())
    }

    @Test
    fun modelManifestThresholdOverridesEnrollmentProfile() {
        val detector = FakeDetector(score = 0.66f, threshold = 0.70f)
        val engine = WakeWordEngine(
            config = WakeWordConfig(detectionCooldownMs = 0L),
            customDetector = detector
        )
        engine.applyVoiceProfile(UserVoiceProfile(isEnrolled = true, calibratedThreshold = 0.40f, calibratedMinEnergyRms = 1.0))
        assertEquals(0.70f, engine.getEffectiveThreshold()!!, 0f)
        engine.start {}
        assertFalse(engine.acceptAudio(ShortArray(16_000) { 1_000 }))
        engine.close()
    }

    @Test
    fun oneConfirmedKwsEventDoesNotNeedAnotherGateHitAndCarriesOneSecond() {
        val detector = FakeDetector(score = 0.9f, threshold = 0.7f)
        val gate = JarvisWakeGate(ownerVerificationAvailable = { false }, verifyOwner = { false })
        val engine = WakeWordEngine(
            config = WakeWordConfig(detectionCooldownMs = 0L),
            customDetector = detector,
            wakeGate = gate
        )
        var activations = 0
        engine.start { activations++ }
        assertTrue(engine.acceptAudio(ShortArray(16_000) { 2_000 }))
        assertEquals(1, activations)
        assertEquals(16_000, engine.getTrailingAudio()!!.size)
        assertEquals(listOf(16_000), detector.audioSizes)
        engine.close()
    }

    @Test
    fun unavailableDetectorNeverTriggersAndExplainsWhy() {
        val engine = WakeWordEngine(customDetector = FakeDetector(available = false))
        assertFalse(engine.isAvailable())
        assertNotNull(engine.getModelError())
        engine.start {}
        assertFalse(engine.acceptAudio(ShortArray(16_000) { 2_000 }))
    }

    @Test
    fun temporalConfirmationRemainsInsideKwsDetectorOnly() {
        val confirmation = KwsTemporalConfirmation(windowCount = 3, requiredPositiveWindows = 2)
        assertEquals(0f, confirmation.add(0.8f, 0.7f), 0f)
        assertEquals(0f, confirmation.add(0.2f, 0.7f), 0f)
        assertEquals(0.9f, confirmation.add(0.9f, 0.7f), 0f)
    }

    @Test
    fun tensorContractAcceptsOnlyRequiredInt8Shapes() {
        WakeWordModelContract.validate(intArrayOf(1, 49, 10, 1), "INT8", intArrayOf(1, 3), "INT8")
        val badInput = runCatching {
            WakeWordModelContract.validate(intArrayOf(1, 490), "INT8", intArrayOf(1, 3), "INT8")
        }.exceptionOrNull()
        assertTrue(badInput?.message?.contains("Expected input [1, 49, 10, 1] INT8") == true)
        val badOutput = runCatching {
            WakeWordModelContract.validate(intArrayOf(1, 49, 10, 1), "INT8", intArrayOf(1, 2), "INT8")
        }.exceptionOrNull()
        assertTrue(badOutput?.message?.contains("Expected output [1, 3] INT8") == true)
    }
}
