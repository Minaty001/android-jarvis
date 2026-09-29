package com.jarvis

import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.VoiceEnrollmentCalibrator
import com.jarvis.wakeword.VoiceEnrollmentCalibrator.RejectionReason
import com.jarvis.wakeword.VoiceEnrollmentCalibrator.ValidationResult
import com.jarvis.wakeword.VoiceSampleRecorder
import com.jarvis.wakeword.WakeWordDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VoiceEnrollmentAndCalibrationTest {
    private class FakeDetector(
        private val score: Float? = 0.73f,
        private val threshold: Float? = 0.65f,
        private val available: Boolean = true
    ) : WakeWordDetector {
        var scoringCalls = 0
        override fun isModelAvailable() = available
        override fun getModelLoadError() = if (available) null else "missing model"
        override fun getModelThreshold() = threshold
        override fun scoreEnrollmentSample(pcm: ShortArray): Float? { scoringCalls++; return score }
        override fun pushAudio(samples: ShortArray) = 0f
        override fun resetDetectorState() = Unit
        override fun close() = Unit
    }

    private fun speech() = ShortArray(16_000) { (sin(it * 0.1) * 8_000).toInt().toShort() }

    @Test
    fun profileStoresDatasetAndModelIdentityWithoutCalibratingThreshold() {
        val profile = UserVoiceProfile(isEnrolled = true, sampleCount = 50, modelVersion = "7", modelSha256 = "a".repeat(64))
        assertTrue(profile.isEnrolled)
        assertEquals(50, profile.sampleCount)
        assertEquals("7", profile.modelVersion)
        assertEquals("a".repeat(64), profile.modelSha256)
        assertEquals(0f, profile.averageScore, 0.0f)
    }

    @Test
    fun qualityValidationRejectsEmptyShortQuietAndClippedAudio() {
        val calibrator = VoiceEnrollmentCalibrator()
        assertEquals(RejectionReason.EMPTY_AUDIO, (calibrator.validateAudioQuality(shortArrayOf()) as ValidationResult.Rejected).reason)
        assertEquals(RejectionReason.TOO_SHORT, (calibrator.validateAudioQuality(ShortArray(1_600) { 5_000 }) as ValidationResult.Rejected).reason)
        assertEquals(RejectionReason.TOO_QUIET, (calibrator.validateAudioQuality(ShortArray(16_000) { 10 }) as ValidationResult.Rejected).reason)
        val clipped = ShortArray(16_000) { if (it % 20 == 0) 32_500 else 5_000 }
        assertEquals(RejectionReason.CLIPPING_DISTORTION, (calibrator.validateAudioQuality(clipped) as ValidationResult.Rejected).reason)
    }

    @Test
    fun unavailableDetectorIsExplicitAndNeverFabricatesScore() {
        val detector = FakeDetector(score = null, threshold = null, available = false)
        val result = VoiceEnrollmentCalibrator().validateSample(speech(), detector)
        assertEquals(RejectionReason.MODEL_UNAVAILABLE, (result as ValidationResult.Rejected).reason)
        assertEquals(0, detector.scoringCalls)
    }

    @Test
    fun detectorScoreAndManifestThresholdAreUsedVerbatim() {
        val detector = FakeDetector(score = 0.73f, threshold = 0.68f)
        val result = VoiceEnrollmentCalibrator().validateSample(speech(), detector) as ValidationResult.Valid
        assertEquals(0.73f, result.score!!, 0f)
        assertEquals(0.68f, result.threshold!!, 0f)
        assertEquals(1, detector.scoringCalls)
    }

    @Test
    fun sampleBelowActualModelThresholdIsRejected() {
        val result = VoiceEnrollmentCalibrator().validateSample(speech(), FakeDetector(score = 0.61f, threshold = 0.68f))
        assertEquals(RejectionReason.LOW_CONFIDENCE, (result as ValidationResult.Rejected).reason)
    }

    @Test
    fun trimmingProducesOneToOneAndHalfSecondDatasetWindow() {
        val source = ShortArray(32_000) { (sin(it * 0.1) * 8_000).toInt().toShort() }
        val trimmed = VoiceEnrollmentCalibrator().trimForDataset(source)
        assertTrue(trimmed.size in 16_000..24_000)
    }

    @Test
    fun voiceSampleRecorderCapturesCustomPcmSource() = runBlocking {
        var calls = 0
        var meterCalled = false
        val recorder = VoiceSampleRecorder(customAudioSource = { buffer ->
            calls++
            buffer.fill(1_000)
            buffer.size
        })
        val result = recorder.recordSample(durationMs = 200L) { meterCalled = true }
        assertNotNull(result)
        assertEquals(3_200, result!!.size)
        assertTrue(calls >= 5)
        assertTrue(meterCalled)
        assertFalse(recorder.isRecording())
    }
}
