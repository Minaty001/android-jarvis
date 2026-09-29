package com.jarvis.voice

import android.content.Context
import android.util.Log
import com.jarvis.wakeword.HybridWakeWordDetector
import com.jarvis.wakeword.KwsFeatureSpec
import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.WakeDetectorMode
import com.jarvis.wakeword.WakeWordConfig
import com.jarvis.wakeword.WakeWordDetector
import com.jarvis.wakeword.WakeWordModelMetadata
import kotlin.math.sqrt

enum class WakeWordState { STOPPED, RUNNING, TRIGGERED, CLOSED }
enum class WakeModelStatus { MODEL_MISSING, MODEL_READY, DATASET_ENROLLING, DATASET_READY, MODEL_OUTDATED, MODEL_READY_AND_ENROLLED }

data class WakeWordSampleScore(val score: Float, val threshold: Float, val passed: Boolean)

data class WakeWordDiagnostics(
    val modelAvailable: Boolean,
    val modelError: String?,
    val detectorMode: WakeDetectorMode,
    val modelStatus: WakeModelStatus,
    val phrase: String,
    val threshold: Float?,
    val latestScore: Float,
    val audioRms: Double,
    val audioDurationMs: Long,
    val sampleRate: Int,
    val inputShape: String,
    val outputShape: String,
    val modelVersion: String?,
    val trainedSampleCount: Int?,
    val modelSha256: String?,
    val modelFile: String?,
    val outputPositive: Boolean?,
    val energyGatePassed: Boolean?,
    val ownerVerificationRequired: Boolean
)

/** Receives the single continuous audio route and triggers only on a loaded neural KWS model. */
class WakeWordEngine(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val customDetector: WakeWordDetector? = null,
    var wakeGate: JarvisWakeGate? = null
) {
    private val tag = "WakeWordEngine"
    private val spec = KwsFeatureSpec.DEFAULT
    private var detector: WakeWordDetector? = customDetector
    private var currentVoiceProfile = UserVoiceProfile.DEFAULT
    private val recentAudio = ShortArray(spec.windowSamples)
    private var recentAudioPosition = 0
    private var recentAudioCount = 0
    private var totalAudioSamples = 0L
    private var latestScore = 0f
    private var latestRms = 0.0
    @Volatile private var state = WakeWordState.STOPPED
    @Volatile private var trailingAudioBuffer: ShortArray? = null
    private var onDetectedCallback: ((String) -> Unit)? = null
    private var lastDetectionAt = 0L
    private var detectionInProgress = false

    private fun ensureDetectorInitialized() {
        if (detector == null) detector = HybridWakeWordDetector(context, config = config)
    }

    @Synchronized
    fun applyVoiceProfile(profile: UserVoiceProfile) {
        currentVoiceProfile = profile
        Log.i(tag, "Dataset profile updated (samples=${profile.sampleCount}, model=${profile.modelVersion.ifBlank { "not recorded" }})")
    }

    fun getVoiceProfile(): UserVoiceProfile = currentVoiceProfile

    @Synchronized
    fun availableWakeWords(): List<String> = listOf(WakeWordModelMetadata.SUPPORTED_PHRASE)

    @Synchronized
    fun setActiveWakeWord(phrase: String): Boolean =
        phrase.equals(WakeWordModelMetadata.SUPPORTED_PHRASE, ignoreCase = true) && isAvailable()

    fun getTrailingAudio(): ShortArray? = trailingAudioBuffer?.copyOf()

    @Synchronized
    fun isAvailable(): Boolean {
        ensureDetectorInitialized()
        return detector?.isModelAvailable() == true && detector?.getModelThreshold() != null
    }

    @Synchronized
    fun getDetectorMode(): WakeDetectorMode {
        ensureDetectorInitialized()
        return (detector as? HybridWakeWordDetector)?.getDetectorMode()
            ?: if (detector?.isModelAvailable() == true && detector?.getModelThreshold() != null) WakeDetectorMode.NEURAL_READY else WakeDetectorMode.UNAVAILABLE
    }

    @Synchronized
    fun getModelError(): String? {
        ensureDetectorInitialized()
        return detector?.getModelLoadError() ?: if (detector?.getModelThreshold() == null) "Validated KWS model metadata is unavailable" else null
    }

    @Synchronized
    fun getModelMetadata(): WakeWordModelMetadata? {
        ensureDetectorInitialized()
        return detector?.getModelMetadata()
    }

    @Synchronized
    fun getEffectiveThreshold(): Float? {
        ensureDetectorInitialized()
        return detector?.getModelThreshold()
    }

    @Synchronized
    fun scoreEnrollmentSample(pcm: ShortArray): WakeWordSampleScore? {
        if (pcm.isEmpty()) return null
        ensureDetectorInitialized()
        val det = detector ?: return null
        val threshold = det.getModelThreshold()?.takeIf { it.isFinite() } ?: return null
        if (!det.isModelAvailable()) return null
        det.resetDetectorState()
        return try {
            val score = det.scoreEnrollmentSample(pcm)?.takeIf { it.isFinite() } ?: return null
            WakeWordSampleScore(score, threshold, score >= threshold)
        } finally {
            det.resetDetectorState()
        }
    }

    @Synchronized
    fun getDiagnostics(
        datasetSampleCount: Int = currentVoiceProfile.sampleCount,
        datasetSha256: String = currentVoiceProfile.datasetSha256
    ): WakeWordDiagnostics {
        ensureDetectorInitialized()
        val det = detector
        val metadata = det?.getModelMetadata()
        val available = det?.isModelAvailable() == true && metadata != null
        val modelStatus = when {
            metadata != null && datasetSampleCount > metadata.trainedSampleCount -> WakeModelStatus.MODEL_OUTDATED
            metadata != null && datasetSampleCount > 0 && datasetSha256 != metadata.positiveDatasetSha256 -> WakeModelStatus.MODEL_OUTDATED
            available && datasetSampleCount > 0 && datasetSampleCount == metadata?.trainedSampleCount -> WakeModelStatus.MODEL_READY_AND_ENROLLED
            available -> WakeModelStatus.MODEL_READY
            datasetSampleCount >= com.jarvis.wakeword.WakeWordDatasetStore.TARGET_SAMPLES -> WakeModelStatus.DATASET_READY
            datasetSampleCount > 0 -> WakeModelStatus.DATASET_ENROLLING
            else -> WakeModelStatus.MODEL_MISSING
        }
        return WakeWordDiagnostics(
            modelAvailable = available,
            modelError = if (available) null else det?.getModelLoadError() ?: "MODEL MISSING — TRAIN MODEL FIRST",
            detectorMode = getDetectorMode(),
            modelStatus = modelStatus,
            phrase = metadata?.phrase ?: WakeWordModelMetadata.SUPPORTED_PHRASE,
            threshold = metadata?.threshold,
            latestScore = det?.getLatestRawScore()?.takeIf { it.isFinite() } ?: latestScore,
            audioRms = latestRms,
            audioDurationMs = minOf(totalAudioSamples, spec.windowSamples.toLong()) * 1000L / spec.sampleRate,
            sampleRate = spec.sampleRate,
            inputShape = det?.getInputShape() ?: "unavailable",
            outputShape = det?.getOutputShape() ?: "unavailable",
            modelVersion = metadata?.version?.toString(),
            trainedSampleCount = metadata?.trainedSampleCount,
            modelSha256 = metadata?.modelSha256,
            modelFile = metadata?.modelFile,
            outputPositive = metadata?.threshold?.let { latestScore >= it },
            energyGatePassed = det?.getLatestEnergyGatePassed(),
            ownerVerificationRequired = wakeGate?.isOwnerVerificationRequired() == true
        )
    }

    @Synchronized
    fun start(@Suppress("UNUSED_PARAMETER") kwsDir: java.io.File? = null, onDetected: ((String) -> Unit)? = null) {
        if (state == WakeWordState.CLOSED) {
            state = WakeWordState.STOPPED
            detector = customDetector
        }
        ensureDetectorInitialized()
        recentAudio.fill(0)
        recentAudioPosition = 0
        recentAudioCount = 0
        totalAudioSamples = 0L
        trailingAudioBuffer = null
        onDetectedCallback = onDetected
        detectionInProgress = false
        detector?.resetDetectorState()
        lastDetectionAt = 0L
        state = WakeWordState.RUNNING
        Log.i(tag, "WakeWordEngine started (modelAvailable=${isAvailable()}, mode=${getDetectorMode()})")
    }

    @Synchronized
    fun acceptAudio(samples: ShortArray): Boolean {
        if (state != WakeWordState.RUNNING || detectionInProgress || samples.isEmpty()) return false
        appendRecentAudio(samples)
        latestRms = calculateRms(samples)
        val det = detector ?: return false
        if (!det.isModelAvailable()) return false
        val score = det.pushAudio(samples)
        latestScore = det.getLatestRawScore()?.takeIf { it.isFinite() } ?: score
        val threshold = det.getModelThreshold() ?: return false
        val now = System.currentTimeMillis()
        if (!score.isFinite() || score < threshold || now - lastDetectionAt < config.detectionCooldownMs) return false

        val candidateAudio = recentAudioWindow()
        val verified = wakeGate?.processWakeScore(score, candidateAudio, threshold) ?: true
        if (!verified) return false

        detectionInProgress = true
        trailingAudioBuffer = candidateAudio
        state = WakeWordState.TRIGGERED
        lastDetectionAt = now
        Log.i(tag, "Wake event accepted for '${WakeWordModelMetadata.SUPPORTED_PHRASE}' (score=$score)")
        onDetectedCallback?.invoke(WakeWordModelMetadata.SUPPORTED_PHRASE)
        return true
    }

    fun applyOwnerVoiceProfile(profile: com.jarvis.wakeword.OwnerVoiceProfile?) = wakeGate?.setOwnerProfile(profile) ?: Unit

    @Synchronized
    fun stop() {
        if (state == WakeWordState.CLOSED) return
        state = WakeWordState.STOPPED
        detectionInProgress = false
        onDetectedCallback = null
    }

    @Synchronized
    fun reset() {
        if (state == WakeWordState.CLOSED) return
        detectionInProgress = false
        detector?.resetDetectorState()
        lastDetectionAt = 0L
        state = WakeWordState.RUNNING
    }

    @Synchronized
    fun close() {
        state = WakeWordState.CLOSED
        detectionInProgress = false
        onDetectedCallback = null
        detector?.close()
        detector = null
    }

    private fun appendRecentAudio(samples: ShortArray) {
        samples.forEach { sample ->
            recentAudio[recentAudioPosition] = sample
            recentAudioPosition = (recentAudioPosition + 1) % recentAudio.size
            recentAudioCount = minOf(recentAudioCount + 1, recentAudio.size)
            totalAudioSamples++
        }
    }

    private fun recentAudioWindow(): ShortArray {
        val result = ShortArray(spec.windowSamples)
        if (recentAudioCount == recentAudio.size) {
            val firstLength = recentAudio.size - recentAudioPosition
            System.arraycopy(recentAudio, recentAudioPosition, result, 0, firstLength)
            System.arraycopy(recentAudio, 0, result, firstLength, recentAudioPosition)
        } else {
            System.arraycopy(recentAudio, 0, result, 0, recentAudioCount)
        }
        return result
    }

    private fun calculateRms(samples: ShortArray): Double {
        var sum = 0.0
        samples.forEach { val normalized = it.toDouble() / 32768.0; sum += normalized * normalized }
        return sqrt(sum / samples.size)
    }
}
