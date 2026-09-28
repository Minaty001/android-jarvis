package com.jarvis.voice

import android.content.Context
import android.util.Log
import com.jarvis.wakeword.DsCnnWakeWordDetector
import com.jarvis.wakeword.HybridWakeWordDetector
import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.WakeWordConfig
import com.jarvis.wakeword.WakeWordDetector
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class WakeWordState {
    STOPPED,
    RUNNING,
    TRIGGERED,
    CLOSED
}

/**
 * Wake word engine using continuous PCM audio stream + TFLite KWS detector with acoustic fallback.
 * Uses AudioRecord directly without requesting audio focus, allowing background media
 * (YouTube, Spotify, etc.) to play uninterrupted without pausing or ducking.
 *
 * State lifecycle:
 * - start(): Marks RUNNING, resets detector state, sets callback.
 * - acceptAudio(): Evaluates frame if RUNNING; transitions to TRIGGERED on detection.
 * - stop(): Stops accepting audio, remains loaded.
 * - reset(): Clears detection history and state, returns to RUNNING.
 * - close(): Permanently frees TFLite interpreter and buffers.
 */
class WakeWordEngine(
    private val context: Context? = null,
    private var config: WakeWordConfig = WakeWordConfig(),
    private val customDetector: WakeWordDetector? = null,
    var rollingBuffer: com.jarvis.voice.audio.RollingPcmBuffer? = null,
    var wakeGate: JarvisWakeGate? = null
) {
    private val TAG = "WakeWordEngine"
    private var detector: WakeWordDetector? = customDetector
    private var activeProfileIndex: Int = 0
    private val detectionGate = AtomicBoolean(false)
    private var currentVoiceProfile: UserVoiceProfile = UserVoiceProfile.DEFAULT

    @Volatile
    private var state = WakeWordState.STOPPED
    private var onDetectedCallback: ((String) -> Unit)? = null
    private var lastDetectionAt: Long = 0L
    @Volatile
    private var trailingAudioBuffer: ShortArray? = null

    private fun ensureDetectorInitialized() {
        if (detector == null) {
            val profile = config.profiles.getOrNull(activeProfileIndex)
            val profileConfig = if (currentVoiceProfile.isEnrolled) {
                config.copy(
                    detectionThreshold = currentVoiceProfile.calibratedThreshold,
                    minEnergyRms = currentVoiceProfile.calibratedMinEnergyRms
                )
            } else if (profile != null) {
                config.copy(
                    detectionThreshold = profile.threshold
                )
            } else config
            detector = HybridWakeWordDetector(
                context = context,
                modelAssetPath = profile?.modelAssetPath ?: "jarvis_dscnn_int8.tflite",
                config = profileConfig
            )
        }
    }

    @Synchronized
    fun applyVoiceProfile(profile: UserVoiceProfile) {
        currentVoiceProfile = profile
        val targetThreshold = if (profile.isEnrolled) {
            profile.calibratedThreshold.coerceIn(0.50f, 0.75f)
        } else {
            config.profiles.getOrNull(activeProfileIndex)?.threshold ?: 0.65f
        }
        val targetMinEnergy = if (profile.isEnrolled) {
            profile.calibratedMinEnergyRms.coerceIn(10.0, 45.0)
        } else {
            15.0
        }

        config = config.copy(
            detectionThreshold = targetThreshold,
            minEnergyRms = targetMinEnergy
        )

        if (customDetector == null) {
            detector?.close()
            detector = null
            ensureDetectorInitialized()
        }
        Log.i(TAG, "Applied voice profile (enrolled=${profile.isEnrolled}, threshold=${config.effectiveThreshold()}, minEnergy=$targetMinEnergy)")
    }

    fun getVoiceProfile(): UserVoiceProfile = currentVoiceProfile

    fun getEffectiveThreshold(): Float = config.effectiveThreshold()

    @Synchronized
    fun availableWakeWords(): List<String> = config.profiles.map { it.phrase }

    @Synchronized
    fun setActiveWakeWord(phrase: String): Boolean {
        val index = config.profiles.indexOfFirst { it.phrase.equals(phrase, ignoreCase = true) }
        if (index < 0) return false
        activeProfileIndex = index
        detector?.close()
        detector = null
        ensureDetectorInitialized()
        return true
    }

    fun getTrailingAudio(): ShortArray? = trailingAudioBuffer

    fun isAvailable(): Boolean {
        ensureDetectorInitialized()
        return detector?.isModelAvailable() == true
    }

    fun getDetectorMode(): com.jarvis.wakeword.WakeDetectorMode {
        ensureDetectorInitialized()
        return (detector as? HybridWakeWordDetector)?.getDetectorMode()
            ?: if (detector?.isModelAvailable() == true) com.jarvis.wakeword.WakeDetectorMode.NEURAL_READY else com.jarvis.wakeword.WakeDetectorMode.UNAVAILABLE
    }

    fun getModelError(): String? {
        ensureDetectorInitialized()
        return detector?.getModelLoadError()
    }

    fun start(@Suppress("UNUSED_PARAMETER") kwsDir: File? = null, onDetected: ((String) -> Unit)? = null) {
        if (state == WakeWordState.CLOSED) {
            Log.i(TAG, "Re-opening previously closed wake engine")
            state = WakeWordState.STOPPED
            detector = null
        }

        ensureDetectorInitialized()
        onDetectedCallback = onDetected
        detectionGate.set(false)
        detector?.resetDetectorState()
        lastDetectionAt = 0L
        state = WakeWordState.RUNNING

    Log.i(TAG, "WakeWordEngine started (state=$state, modelAvailable=${isAvailable()})")
    }

    fun acceptAudio(samples: ShortArray): Boolean {
        if (state != WakeWordState.RUNNING || detectionGate.get() || samples.isEmpty()) {
            return false
        }

        val det = detector ?: return false
        val score = det.pushAudio(samples)
        val now = System.currentTimeMillis()
        val threshold = config.effectiveThreshold()

        if (score >= threshold && now - lastDetectionAt >= config.detectionCooldownMs) {
            val candidateAudio = rollingBuffer?.getRecentAudio() ?: samples
            val isVerified = wakeGate?.processWakeScore(score, candidateAudio) ?: true

            if (!isVerified) {
                return false
            }

            if (detectionGate.compareAndSet(false, true)) {
                trailingAudioBuffer = samples.copyOf()
                state = WakeWordState.TRIGGERED
                lastDetectionAt = now
                val phrase = config.profiles.getOrNull(activeProfileIndex)?.phrase ?: "Jarvis"
                Log.i(TAG, "Wake word '$phrase' detected and VERIFIED in PCM stream with score: $score")
                onDetectedCallback?.invoke(phrase)
                return true
            }
        }
        return false
    }

    fun applyOwnerVoiceProfile(profile: com.jarvis.wakeword.OwnerVoiceProfile?) {
        wakeGate?.setOwnerProfile(profile)
    }

    fun stop() {
        if (state == WakeWordState.CLOSED) return
        state = WakeWordState.STOPPED
        detectionGate.set(false)
        onDetectedCallback = null
        Log.i(TAG, "WakeWordEngine stopped listening")
    }

    fun reset() {
        if (state == WakeWordState.CLOSED) return
        detectionGate.set(false)
        detector?.resetDetectorState()
        lastDetectionAt = 0L
        state = WakeWordState.RUNNING
        Log.d(TAG, "WakeWordEngine reset to RUNNING")
    }

    fun close() {
        state = WakeWordState.CLOSED
        detectionGate.set(false)
        onDetectedCallback = null
        detector?.close()
        detector = null
        Log.i(TAG, "WakeWordEngine closed and released")
    }
}
