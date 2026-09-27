package com.jarvis.wakeword

import android.content.Context
import android.util.Log

/**
 * Explicit operational modes for the keyword spotting detector.
 */
enum class WakeDetectorMode {
    /** Trained neural TFLite DS-CNN classifier is loaded and active. */
    NEURAL_READY,
    /**
     * Degraded heuristic fallback: RMS energy, zero-crossing, and envelope matching.
     * Clearly segregated from neural detection.
     */
    DEGRADED_ACOUSTIC,
    /** No valid detector available; microphone remains quiet or requires manual tap-to-talk. */
    UNAVAILABLE
}

/**
 * Hybrid dual-layer wake word detector fusing neural TFLite classification
 * with acoustic template matching.
 *
 * Provides honest capability reporting:
 * - When TFLite neural model is loaded -> NEURAL_READY.
 * - When model is absent and degraded mode is allowed -> DEGRADED_ACOUSTIC.
 * - When model is absent and degraded mode is disallowed -> UNAVAILABLE.
 */
class HybridWakeWordDetector(
    private val context: Context? = null,
    val modelAssetPath: String = "jarvis_dscnn_int8.tflite",
    val config: WakeWordConfig = WakeWordConfig(),
    customPrimary: WakeWordDetector? = null,
    val allowDegradedFallback: Boolean = config.allowDegradedAcousticFallback
) : WakeWordDetector {

    private val TAG = "HybridWakeDetector"

    val acousticDetector: AcousticWakeWordDetector = AcousticWakeWordDetector(config)
    var neuralDetector: WakeWordDetector? = customPrimary
        private set

    init {
        if (neuralDetector == null && context != null) {
            try {
                neuralDetector = DsCnnWakeWordDetector(context, modelAssetPath, config)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize DsCnnWakeWordDetector; mode will reflect degraded/unavailable state", e)
                neuralDetector = null
            }
        }
    }

    fun getDetectorMode(): WakeDetectorMode {
        return when {
            neuralDetector?.isModelAvailable() == true -> WakeDetectorMode.NEURAL_READY
            allowDegradedFallback && acousticDetector.isModelAvailable() -> WakeDetectorMode.DEGRADED_ACOUSTIC
            else -> WakeDetectorMode.UNAVAILABLE
        }
    }

    override fun isModelAvailable(): Boolean {
        return getDetectorMode() != WakeDetectorMode.UNAVAILABLE
    }

    fun isNeuralModelActive(): Boolean {
        return neuralDetector?.isModelAvailable() == true
    }

    override fun getModelLoadError(): String? {
        if (neuralDetector?.isModelAvailable() == true) return null
        return neuralDetector?.getModelLoadError()
    }

    @Synchronized
    override fun pushAudio(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0.0f

        val mode = getDetectorMode()
        if (mode == WakeDetectorMode.UNAVAILABLE) {
            return 0.0f
        }

        if (mode == WakeDetectorMode.NEURAL_READY) {
            val neuralScore = try {
                neuralDetector?.pushAudio(samples) ?: 0.0f
            } catch (e: Exception) {
                Log.w(TAG, "Neural detector inference exception", e)
                0.0f
            }

            if (neuralScore >= config.effectiveThreshold()) {
                Log.i(TAG, "Wake detected via Neural KWS (score: $neuralScore)")
                return neuralScore
            }

            return 0.0f
        } else {
            // DEGRADED_ACOUSTIC mode: only active if allowDegradedFallback is true and neural model is unavailable
            return acousticDetector.pushAudio(samples)
        }
    }

    @Synchronized
    override fun resetDetectorState() {
        neuralDetector?.resetDetectorState()
        acousticDetector.resetDetectorState()
    }

    override fun close() {
        try {
            neuralDetector?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing neural detector", e)
        }
        try {
            acousticDetector.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing acoustic detector", e)
        }
        neuralDetector = null
    }
}
