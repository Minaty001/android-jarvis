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
    private var activeConfig: WakeWordConfig = config
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
        if (detector == null) detector = HybridWakeWordDetector(context, config = activeConfig)
    }

    @Synchronized
    fun setSensitivity(sensitivity: String) {
        val isLowEnd = context?.let { com.jarvis.device.HardwareProfileManager.isLowEndDevice(it) } ?: false
        activeConfig = WakeWordConfig.fromSensitivity(sensitivity, isLowEnd)
        if (customDetector == null) {
            detector?.close()
            detector = HybridWakeWordDetector(context, config = activeConfig)
            if (state == WakeWordState.RUNNING) {
                detector?.resetDetectorState()
            }
        }
        Log.i(tag, "WakeWordEngine sensitivity set to '$sensitivity' (minEnergyRms=${activeConfig.minEnergyRms}, windows=${activeConfig.confirmationWindowCount}, required=${activeConfig.requiredPositiveWindows})")
    }

    fun getConfig(): WakeWordConfig = activeConfig

    @Synchronized
    fun applyVoiceProfile(profile: UserVoiceProfile) {
        currentVoiceProfile = profile
        Log.i(tag, "Dataset profile updated (samples=${profile.sampleCount}, model=${profile.modelVersion.ifBlank { "not recorded" }})")
    }

    fun getVoiceProfile(): UserVoiceProfile = currentVoiceProfile

    @Synchronized
    fun availableWakeWords(): List<String> {
        ensureDetectorInitialized()
        return detector?.getSupportedPhrases().orEmpty()
    }

    @Synchronized
    fun setActiveWakeWord(phrase: String): Boolean =
        availableWakeWords().any { it.equals(phrase.trim(), ignoreCase = true) } && isAvailable()

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
        val available = det?.isModelAvailable() == true && det.getModelThreshold() != null
        val modelStatus = when {
            available && metadata == null -> WakeModelStatus.MODEL_READY
            metadata != null && datasetSampleCount > metadata.trainedSampleCount -> WakeModelStatus.MODEL_OUTDATED
            metadata != null && datasetSampleCount > 0 && datasetSha256 != metadata.positiveDatasetSha256 -> WakeModelStatus.MODEL_OUTDATED
            available && datasetSampleCount > 0 && datasetSampleCount == metadata?.trainedSampleCount -> WakeModelStatus.MODEL_READY_AND_ENROLLED
            available -> WakeModelStatus.MODEL_READY
            datasetSampleCount >= com.jarvis.wakeword.WakeWordDatasetStore.TARGET_SAMPLES -> WakeModelStatus.DATASET_READY
            datasetSampleCount > 0 -> WakeModelStatus.DATASET_ENROLLING
            else -> WakeModelStatus.MODEL_MISSING
        }
        val currentRms = if (recentAudioCount > 0) calculateRms(recentAudioWindow()) else latestRms
        return WakeWordDiagnostics(
            modelAvailable = available,
            modelError = if (available) null else det?.getModelLoadError() ?: "MODEL MISSING — TRAIN MODEL FIRST",
            detectorMode = getDetectorMode(),
            modelStatus = modelStatus,
            phrase = metadata?.phrase ?: det?.getSupportedPhrases()?.joinToString(" / ").orEmpty().ifBlank { "unavailable" },
            threshold = det?.getModelThreshold(),
            latestScore = det?.getLatestRawScore()?.takeIf { it.isFinite() } ?: latestScore,
            audioRms = currentRms,
            audioDurationMs = minOf(totalAudioSamples, spec.windowSamples.toLong()) * 1000L / spec.sampleRate,
            sampleRate = spec.sampleRate,
            inputShape = det?.getInputShape() ?: "unavailable",
            outputShape = det?.getOutputShape() ?: "unavailable",
            modelVersion = metadata?.version?.toString(),
            trainedSampleCount = metadata?.trainedSampleCount,
            modelSha256 = metadata?.modelSha256,
            modelFile = metadata?.modelFile,
            outputPositive = det?.getModelThreshold()?.let { latestScore >= it },
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
        lastDetectionAt = System.currentTimeMillis()
        state = WakeWordState.RUNNING
        Log.i(tag, "WakeWordEngine started (modelAvailable=${isAvailable()}, mode=${getDetectorMode()})")
    }

    @Synchronized
    fun acceptAudio(samples: ShortArray): Boolean {
        if (state != WakeWordState.RUNNING || detectionInProgress || samples.isEmpty()) return false
        appendRecentAudio(samples)
        val det = detector ?: return false
        if (!det.isModelAvailable()) return false
        val score = det.pushAudio(samples)
        val raw = det.getLatestRawScore()
        if (raw != null && raw > 0f) {
            latestScore = raw
        } else if (score > 0f) {
            latestScore = score
        }
        val threshold = det.getModelThreshold() ?: return false
        val now = System.currentTimeMillis()
        if (!score.isFinite() || score < threshold || now - lastDetectionAt < activeConfig.detectionCooldownMs) return false

        val candidateAudio = recentAudioWindow()
        val verified = wakeGate?.processWakeScore(score, candidateAudio, threshold) ?: true
        if (!verified) return false

        detectionInProgress = true
        trailingAudioBuffer = candidateAudio
        state = WakeWordState.TRIGGERED
        lastDetectionAt = now
        val phrase = det.getDetectedPhrase() ?: WakeWordModelMetadata.ACTIVE_OPEN_WAKE_PHRASE
        Log.i(tag, "Wake event accepted for '$phrase' (score=$score)")
        onDetectedCallback?.invoke(phrase)
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
        lastDetectionAt = System.currentTimeMillis()
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
        val count = samples.size
        if (count == 0) return
        val capacity = recentAudio.size
        if (count >= capacity) {
            System.arraycopy(samples, count - capacity, recentAudio, 0, capacity)
            recentAudioPosition = 0
            recentAudioCount = capacity
        } else {
            val spaceAtEnd = capacity - recentAudioPosition
            if (count <= spaceAtEnd) {
                System.arraycopy(samples, 0, recentAudio, recentAudioPosition, count)
                recentAudioPosition = (recentAudioPosition + count) % capacity
            } else {
                System.arraycopy(samples, 0, recentAudio, recentAudioPosition, spaceAtEnd)
                val remaining = count - spaceAtEnd
                System.arraycopy(samples, spaceAtEnd, recentAudio, 0, remaining)
                recentAudioPosition = remaining
            }
            recentAudioCount = minOf(recentAudioCount + count, capacity)
        }
        totalAudioSamples += count
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
        if (samples.isEmpty()) return 0.0
        var sumSquares = 0L
        for (i in samples.indices) {
            val s = samples[i].toLong()
            sumSquares += s * s
        }
        return sqrt(sumSquares.toDouble() / samples.size) / 32768.0
    }
}
