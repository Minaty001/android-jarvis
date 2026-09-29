package com.jarvis.wakeword

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordModelMetadataTest {
    @Test
    fun parsesValidatedModelProvenanceAndFeatureContract() {
        val json = JSONObject().apply {
            put("version", 4)
            put("phrase", "Jarvis")
            put("threshold", 0.73)
            put("positiveClassIndex", 2)
            put("modelSha256", "a".repeat(64))
            put("positiveDatasetSha256", "b".repeat(64))
            put("sampleRate", 16_000)
            put("windowSamples", 16_000)
            put("frameLengthSamples", 400)
            put("frameStrideSamples", 320)
            put("fftSize", 512)
            put("numMelFilters", 40)
            put("numMfcc", 10)
            put("numFrames", 49)
            put("labels", JSONArray(listOf("background", "unknown", "jarvis")))
            put("datasetVersion", 1)
            put("trainedSampleCount", 50)
            put("trainedAt", "2026-09-29T00:00:00Z")
            put("modelFile", WakeWordModelMetadata.MODEL_NAME)
        }

        val metadata = WakeWordModelMetadata.fromJson(json.toString())
        assertEquals(4, metadata.version)
        assertEquals("Jarvis", metadata.phrase)
        assertEquals(0.73f, metadata.threshold, 0f)
        assertEquals(WakeWordModelMetadata.MODEL_NAME, metadata.modelFile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMetadataWithUnsupportedFeatureShape() {
        val json = JSONObject().apply {
            put("version", 1)
            put("phrase", "Jarvis")
            put("threshold", 0.7)
            put("positiveClassIndex", 2)
            put("modelSha256", "a".repeat(64))
            put("positiveDatasetSha256", "b".repeat(64))
            put("sampleRate", 16_000)
            put("windowSamples", 16_000)
            put("frameLengthSamples", 512)
            put("frameStrideSamples", 320)
            put("fftSize", 512)
            put("numMelFilters", 40)
            put("numMfcc", 10)
            put("numFrames", 49)
            put("labels", JSONArray(listOf("background", "unknown", "jarvis")))
            put("datasetVersion", 1)
            put("trainedSampleCount", 50)
            put("trainedAt", "2026-09-29T00:00:00Z")
            put("modelFile", WakeWordModelMetadata.MODEL_NAME)
        }
        WakeWordModelMetadata.fromJson(json.toString())
    }
}
