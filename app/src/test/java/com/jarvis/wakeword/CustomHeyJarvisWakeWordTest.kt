package com.jarvis.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class CustomHeyJarvisWakeWordTest {

    private val assetsDir = File("src/main/assets")
    private val expectedSha256 = "c18d024b331e663989a63f799cd429bbade28404d4a291eeebb7f8560f807424"

    @Test
    fun testCustomModelAssetExistsAndChecksumMatchesModelCard() {
        val modelFile = File(assetsDir, "hey_jarvis_custom.onnx")
        assertTrue("hey_jarvis_custom.onnx must exist in assets", modelFile.exists())
        assertEquals("Model size must match model card exactly", 214814L, modelFile.length())

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(modelFile.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("Model SHA-256 must match model card checksum", expectedSha256, hash)

        val cardFile = File(assetsDir, "model_card.json")
        assertTrue("model_card.json must exist in assets", cardFile.exists())
        val card = JSONObject(cardFile.readText())
        val params = card.getJSONObject("recommended_inference_parameters")
        assertEquals(0.55, params.getDouble("detection_threshold"), 1e-4)
        assertEquals(2.0, params.getDouble("debounce_cooldown_seconds"), 1e-4)
    }

    @Test
    fun testOpenWakeWordModelsManifestContainsCustomHeyJarvis() {
        val manifestFile = File(assetsDir, "openwakeword_models.json")
        assertTrue("openwakeword_models.json must exist", manifestFile.exists())

        val manifest = JSONObject(manifestFile.readText())
        val models = manifest.getJSONArray("models")
        var foundHeyJarvis = false
        var foundJarvis = false

        for (i in 0 until models.length()) {
            val item = models.getJSONObject(i)
            when (item.getString("phrase")) {
                "Hey Jarvis" -> {
                    foundHeyJarvis = true
                    assertEquals("hey_jarvis_custom.onnx", item.getString("asset"))
                    assertEquals(0.55, item.getDouble("threshold"), 1e-4)
                    assertEquals(expectedSha256, item.getString("sha256"))
                }
                "Jarvis" -> {
                    foundJarvis = true
                    assertEquals("jarvis.tflite", item.getString("asset"))
                    assertEquals(0.80, item.getDouble("threshold"), 1e-4)
                }
            }
        }

        assertTrue("Manifest must configure Hey Jarvis head", foundHeyJarvis)
        assertTrue("Manifest must configure Jarvis head", foundJarvis)
    }

    @Test
    fun testOnnxSessionModelLoadingAndInference() {
        val modelFile = File(assetsDir, "hey_jarvis_custom.onnx")
        assertTrue("hey_jarvis_custom.onnx must exist", modelFile.exists())

        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())

        try {
            assertEquals("Model must have 1 input", 1L, session.numInputs)
            assertEquals("Model must have 1 output", 1L, session.numOutputs)

            val inputName = session.inputNames.iterator().next()
            assertEquals("x", inputName)

            val outputName = session.outputNames.iterator().next()
            assertEquals("sigmoid", outputName)

            // Feed 16 frames of 96-dim speech embeddings = 1536 float32 values
            val buffer = ByteBuffer.allocateDirect(1536 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()

            for (i in 0 until 1536) {
                buffer.put(0.1f * (i % 10))
            }
            buffer.rewind()

            val tensor = OnnxTensor.createTensor(env, buffer, longArrayOf(1, 16, 96))
            tensor.use { t ->
                val result = session.run(mapOf(inputName to t))
                result.use { r ->
                    val outTensor = r.get(0) as OnnxTensor
                    val score = outTensor.floatBuffer.get(0)
                    assertTrue("Score must be finite probability [0, 1]", score in 0f..1f)
                }
            }
        } finally {
            session.close()
            env.close()
        }
    }

    @Test
    fun testSampleAudioResourcesExist() {
        val testResDir = File("src/test/resources")
        val posWav = File(testResDir, "hey_jarvis_pos.wav")
        val negWav = File(testResDir, "hey_travis_neg.wav")

        assertTrue("hey_jarvis_pos.wav must exist in test resources", posWav.exists())
        assertTrue("hey_travis_neg.wav must exist in test resources", negWav.exists())
        assertTrue("Positive WAV file must be non-empty", posWav.length() > 1000L)
        assertTrue("Negative WAV file must be non-empty", negWav.length() > 1000L)
    }
}
