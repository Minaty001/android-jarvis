package com.jarvis.wakeword

import kotlin.math.sqrt

/** Checks recording quality; model scores are always supplied by a real detector. */
class VoiceEnrollmentCalibrator(
    private val minDurationMs: Long = 400L,
    private val minSpeechRms: Double = 70.0,
    private val maxClippingRatio: Double = 0.03,
    private val sampleRate: Int = KwsFeatureSpec.DEFAULT.sampleRate
) {
    sealed class ValidationResult {
        data class Valid(
            val rmsEnergy: Double,
            val speechDurationMs: Long,
            val clippingRatio: Double,
            val score: Float? = null,
            val threshold: Float? = null
        ) : ValidationResult()

        data class Rejected(val reason: RejectionReason, val message: String) : ValidationResult()
    }

    enum class RejectionReason {
        EMPTY_AUDIO,
        TOO_SHORT,
        TOO_QUIET,
        CLIPPING_DISTORTION,
        LOW_CONFIDENCE,
        MODEL_UNAVAILABLE
    }

    fun calculateRms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sumSq = 0.0
        pcm.forEach { sumSq += it.toDouble() * it }
        return sqrt(sumSq / pcm.size)
    }

    fun calculateClippingRatio(pcm: ShortArray, clippingThreshold: Short = 32_000): Double {
        if (pcm.isEmpty()) return 0.0
        return pcm.count { it >= clippingThreshold || it <= -clippingThreshold }.toDouble() / pcm.size
    }

    fun estimateSpeechDurationMs(pcm: ShortArray, frameSize: Int = sampleRate / 25): Long {
        if (pcm.isEmpty()) return 0L
        var activeSamples = 0
        for (start in pcm.indices step frameSize) {
            val end = minOf(start + frameSize, pcm.size)
            if (calculateRms(pcm.copyOfRange(start, end)) >= minSpeechRms * 0.70) {
                activeSamples += end - start
            }
        }
        return activeSamples * 1000L / sampleRate
    }

    /** Audio-only check used for dataset enrollment even when no trained model is installed. */
    fun validateAudioQuality(pcm: ShortArray): ValidationResult {
        if (pcm.isEmpty()) return rejected(RejectionReason.EMPTY_AUDIO, "No audio captured. Try recording again.")

        val totalDurationMs = pcm.size * 1000L / sampleRate
        if (totalDurationMs < minDurationMs) {
            return rejected(RejectionReason.TOO_SHORT, "Recording is too short (${totalDurationMs} ms).")
        }

        val clippingRatio = calculateClippingRatio(pcm)
        if (clippingRatio > maxClippingRatio) {
            return rejected(RejectionReason.CLIPPING_DISTORTION, "Microphone clipping detected (${(clippingRatio * 100).toInt()}%). Speak farther from the microphone.")
        }

        val rms = calculateRms(pcm)
        if (rms < minSpeechRms) {
            return rejected(RejectionReason.TOO_QUIET, "Recording is too quiet (RMS ${rms.toInt()}; minimum ${minSpeechRms.toInt()}).")
        }

        val speechDuration = estimateSpeechDurationMs(pcm)
        if (speechDuration < minDurationMs) {
            return rejected(RejectionReason.TOO_SHORT, "Active speech lasted ${speechDuration} ms; say only 'Jarvis' clearly.")
        }

        return ValidationResult.Valid(rms, speechDuration, clippingRatio)
    }

    /** Detector is mandatory: this result can never fabricate a model score. */
    fun validateSample(pcm: ShortArray, detector: WakeWordDetector): ValidationResult {
        val quality = validateAudioQuality(pcm)
        if (quality !is ValidationResult.Valid) return quality
        if (!detector.isModelAvailable()) {
            return rejected(
                RejectionReason.MODEL_UNAVAILABLE,
                "Wake-word model is unavailable. This recording was not evaluated by the model."
            )
        }

        val threshold = detector.getModelThreshold()
        val score = detector.scoreEnrollmentSample(pcm)
        if (threshold == null || score == null || !threshold.isFinite() || !score.isFinite()) {
            return rejected(RejectionReason.MODEL_UNAVAILABLE, "Wake-word model scoring is unavailable.")
        }
        if (score < threshold) {
            return rejected(RejectionReason.LOW_CONFIDENCE, "Model score ${"%.3f".format(score)} is below its validated threshold.")
        }
        return quality.copy(score = score, threshold = threshold)
    }

    /** Trims leading/trailing low energy and stores a 1.0–1.5 second PCM window. */
    fun trimForDataset(pcm: ShortArray): ShortArray {
        require(pcm.isNotEmpty())
        val frameSize = sampleRate / 100
        val peakFrameRms = (0 until pcm.size step frameSize).maxOf { start ->
            calculateRms(pcm.copyOfRange(start, minOf(start + frameSize, pcm.size)))
        }
        val activeThreshold = maxOf(minSpeechRms * 0.5, peakFrameRms * 0.08)
        var first = pcm.size
        var last = -1
        for (start in 0 until pcm.size step frameSize) {
            val end = minOf(start + frameSize, pcm.size)
            if (calculateRms(pcm.copyOfRange(start, end)) >= activeThreshold) {
                first = minOf(first, start)
                last = maxOf(last, end)
            }
        }

        val pad = sampleRate / 10
        val start = (first - pad).coerceAtLeast(0)
        val end = if (last < 0) pcm.size else (last + pad).coerceAtMost(pcm.size)
        val maxSamples = sampleRate * 3 / 2
        val active = if (end - start > maxSamples) {
            val center = (start + end) / 2
            val cropStart = (center - maxSamples / 2).coerceAtLeast(0).coerceAtMost(pcm.size - maxSamples)
            pcm.copyOfRange(cropStart, cropStart + maxSamples)
        } else {
            pcm.copyOfRange(start, end)
        }

        val minSamples = sampleRate
        if (active.size >= minSamples) return active
        val padded = ShortArray(minSamples)
        val offset = (minSamples - active.size) / 2
        active.copyInto(padded, offset)
        return padded
    }

    private fun rejected(reason: RejectionReason, message: String) = ValidationResult.Rejected(reason, message)
}
