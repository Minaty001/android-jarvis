package com.jarvis.wakeword

import org.json.JSONObject

data class WakeWordModelMetadata(
    val version: Int,
    val phrase: String,
    val threshold: Float,
    val positiveClassIndex: Int,
    val modelSha256: String,
    val positiveDatasetSha256: String,
    val sampleRate: Int,
    val windowSamples: Int,
    val frameLengthSamples: Int,
    val frameStrideSamples: Int,
    val fftSize: Int,
    val numMelFilters: Int,
    val numMfcc: Int,
    val numFrames: Int,
    val labels: List<String>,
    val datasetVersion: Int,
    val trainedSampleCount: Int,
    val trainedAt: String,
    val modelFile: String
) {
    fun validate(spec: KwsFeatureSpec = KwsFeatureSpec.DEFAULT) {
        require(version > 0) { "Model metadata version must be positive" }
        require(phrase == SUPPORTED_PHRASE) { "Only the trained '$SUPPORTED_PHRASE' phrase is supported" }
        require(threshold.isFinite() && threshold in 0f..1f) { "Invalid model threshold: $threshold" }
        require(positiveClassIndex in labels.indices && labels[positiveClassIndex] == "jarvis") {
            "Positive class index must point to 'jarvis'"
        }
        require(labels == listOf("background", "unknown", "jarvis")) { "Unexpected KWS class labels: $labels" }
        require(modelSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid model SHA-256" }
        require(positiveDatasetSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid positive dataset SHA-256" }
        require(sampleRate == spec.sampleRate) { "Model sampleRate=$sampleRate; expected ${spec.sampleRate}" }
        require(windowSamples == spec.windowSamples) { "Model windowSamples=$windowSamples; expected ${spec.windowSamples}" }
        require(frameLengthSamples == spec.frameLengthSamples) { "Model frameLengthSamples=$frameLengthSamples; expected ${spec.frameLengthSamples}" }
        require(frameStrideSamples == spec.frameStrideSamples) { "Model frameStrideSamples=$frameStrideSamples; expected ${spec.frameStrideSamples}" }
        require(fftSize == spec.fftSize) { "Model fftSize=$fftSize; expected ${spec.fftSize}" }
        require(numMelFilters == spec.numMelFilters) { "Model numMelFilters=$numMelFilters; expected ${spec.numMelFilters}" }
        require(numMfcc == spec.numMfcc) { "Model numMfcc=$numMfcc; expected ${spec.numMfcc}" }
        require(numFrames == spec.numFrames) { "Model numFrames=$numFrames; expected ${spec.numFrames}" }
        require(datasetVersion > 0 && trainedSampleCount > 0) { "Model has no recorded training dataset metadata" }
        require(trainedAt.isNotBlank() && modelFile.isNotBlank()) { "Model provenance is incomplete" }
    }

    companion object {
        const val ASSET_NAME = "jarvis_model.json"
        const val MODEL_NAME = "jarvis_dscnn_int8.tflite"
        const val SUPPORTED_PHRASE = "Jarvis"

        fun fromJson(json: String): WakeWordModelMetadata {
            val root = JSONObject(json)
            val labelsArray = root.getJSONArray("labels")
            val labels = List(labelsArray.length()) { labelsArray.getString(it) }
            return WakeWordModelMetadata(
                version = root.getInt("version"),
                phrase = root.getString("phrase"),
                threshold = root.getDouble("threshold").toFloat(),
                positiveClassIndex = root.getInt("positiveClassIndex"),
                modelSha256 = root.getString("modelSha256"),
                positiveDatasetSha256 = root.getString("positiveDatasetSha256"),
                sampleRate = root.getInt("sampleRate"),
                windowSamples = root.getInt("windowSamples"),
                frameLengthSamples = root.getInt("frameLengthSamples"),
                frameStrideSamples = root.getInt("frameStrideSamples"),
                fftSize = root.getInt("fftSize"),
                numMelFilters = root.getInt("numMelFilters"),
                numMfcc = root.getInt("numMfcc"),
                numFrames = root.getInt("numFrames"),
                labels = labels,
                datasetVersion = root.getInt("datasetVersion"),
                trainedSampleCount = root.getInt("trainedSampleCount"),
                trainedAt = root.getString("trainedAt"),
                modelFile = root.getString("modelFile")
            ).also { it.validate() }
        }
    }
}
