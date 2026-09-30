package com.jarvis.wakeword

interface WakeWordDetector {
    fun isModelAvailable(): Boolean
    fun getModelLoadError(): String?
    fun pushAudio(samples: ShortArray): Float
    fun resetDetectorState()
    fun close()

    /** Null means a real model and manifest threshold are not available. */
    fun getModelThreshold(): Float? = null

    /** Scores a saved enrollment sample through the same model without temporal confirmation. */
    fun scoreEnrollmentSample(pcm: ShortArray): Float? = null

    fun getInputShape(): String? = null
    fun getOutputShape(): String? = null
    fun getLatestRawScore(): Float? = null
    fun getLatestEnergyGatePassed(): Boolean? = null
    fun getModelMetadata(): WakeWordModelMetadata? = null
    fun getDetectedPhrase(): String? = null
    fun getSupportedPhrases(): List<String> = listOf("Jarvis", "Hey Jarvis")
}
