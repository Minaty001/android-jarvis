package com.jarvis.wakeword

import android.content.Context
import android.util.Log

enum class WakeDetectorMode {
    NEURAL_READY,
    UNAVAILABLE
}

/** The sole production detector: packaged openWakeWord heads share one streaming frontend. */
class HybridWakeWordDetector(
    private val context: Context? = null,
    val modelAssetPath: String = WakeWordModelMetadata.MODEL_NAME,
    val config: WakeWordConfig = WakeWordConfig(),
    customPrimary: WakeWordDetector? = null
) : WakeWordDetector {
    private val tag = "HybridWakeDetector"
    private var neuralDetector: WakeWordDetector? = customPrimary

    init {
        if (neuralDetector == null) {
            neuralDetector = context?.let { appContext ->
                runCatching {
                    OpenWakeWordDetector(appContext, config)
                }.onFailure { Log.e(tag, "KWS model unavailable; wake detection disabled", it) }.getOrNull()
            }
        }
    }

    fun getDetectorMode(): WakeDetectorMode =
        if (neuralDetector?.isModelAvailable() == true) WakeDetectorMode.NEURAL_READY else WakeDetectorMode.UNAVAILABLE

    override fun isModelAvailable(): Boolean = getDetectorMode() == WakeDetectorMode.NEURAL_READY
    override fun getModelLoadError(): String? = neuralDetector?.getModelLoadError()
    override fun getModelThreshold(): Float? = neuralDetector?.getModelThreshold()
    override fun getInputShape(): String? = neuralDetector?.getInputShape()
    override fun getOutputShape(): String? = neuralDetector?.getOutputShape()
    override fun getLatestRawScore(): Float? = neuralDetector?.getLatestRawScore()
    override fun getLatestEnergyGatePassed(): Boolean? = neuralDetector?.getLatestEnergyGatePassed()
    override fun getModelMetadata(): WakeWordModelMetadata? = neuralDetector?.getModelMetadata()
    override fun getDetectedPhrase(): String? = neuralDetector?.getDetectedPhrase()
    override fun getSupportedPhrases(): List<String> = neuralDetector?.getSupportedPhrases().orEmpty()

    @Synchronized
    override fun pushAudio(samples: ShortArray): Float {
        if (!isModelAvailable() || samples.isEmpty()) return 0f
        return runCatching { neuralDetector?.pushAudio(samples) ?: 0f }
            .onFailure { Log.e(tag, "KWS inference failed; wake detection disabled", it) }
            .getOrDefault(0f)
    }

    override fun scoreEnrollmentSample(pcm: ShortArray): Float? {
        if (!isModelAvailable() || pcm.isEmpty()) return null
        return neuralDetector?.scoreEnrollmentSample(pcm)?.takeIf { it.isFinite() }
    }

    @Synchronized
    override fun resetDetectorState() = neuralDetector?.resetDetectorState() ?: Unit

    override fun close() {
        runCatching { neuralDetector?.close() }
        neuralDetector = null
    }
}
