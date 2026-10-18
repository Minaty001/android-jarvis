package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import com.jarvis.camera.CameraLensReader
import com.jarvis.camera.CameraPerceptionEngine
import com.jarvis.camera.LensReaderMode
import com.jarvis.camera.TfLiteVisionDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CameraLensReaderTest {

    private class MockVisionLlm(private val response: String) : LlmClient {
        var lastPrompt: String? = null
        override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> =
            Result.success(response)

        override suspend fun chatVision(
            prompt: String,
            base64ImageUrl: String?,
            uiContext: String?,
            onToken: (String) -> Unit
        ): Result<String> {
            lastPrompt = prompt
            return Result.success(response)
        }

        override fun cancel() {}
    }

    @Test
    fun testResolveMode() {
        assertEquals(LensReaderMode.PRESCRIPTION, CameraLensReader.resolveMode("read prescription", null))
        assertEquals(LensReaderMode.PRESCRIPTION, CameraLensReader.resolveMode("dawa padho", null))
        assertEquals(LensReaderMode.DOCUMENT, CameraLensReader.resolveMode("read this document", null))
        assertEquals(LensReaderMode.NUTRITION, CameraLensReader.resolveMode("check calories", null))
        assertEquals(LensReaderMode.BARCODE, CameraLensReader.resolveMode("scan barcode", null))
        assertEquals(LensReaderMode.GENERAL, CameraLensReader.resolveMode("what is this", null))
    }

    @Test
    fun testPromptContainsPrescriptionDisclaimer() {
        val prompt = CameraLensReader.buildSystemPrompt(
            mode = LensReaderMode.PRESCRIPTION,
            query = "read prescription",
            facing = "back",
            sceneSummary = "Medicine bottle",
            lighting = "Good"
        )
        assertTrue(prompt.contains("pharmacist", ignoreCase = true) || prompt.contains("consult", ignoreCase = true))
        assertTrue(prompt.contains("dosage", ignoreCase = true))
    }

    @Test
    fun testOfflinePrescriptionAnalysisSafe() {
        val analysis = CameraLensReader.analyzeOffline(
            mode = LensReaderMode.PRESCRIPTION,
            scene = com.jarvis.camera.SceneAnalysis(
                primaryCategory = "Medicine Container",
                detections = emptyList(),
                brightness = 0.5f,
                lightingCondition = "Normal Lighting",
                dominantColors = listOf("White"),
                textDensityHint = "High",
                summary = "Medicine bottle"
            )
        )
        assertTrue(analysis.contains("offline", ignoreCase = true))
        assertTrue(analysis.contains("consult", ignoreCase = true) || analysis.contains("safety", ignoreCase = true))
    }

    @Test
    fun testCameraVisionToolExecutionPrescriptionMode() = runBlocking {
        val mockLlm = MockVisionLlm("Amoxicillin 500mg, take 1 capsule 3 times a day for 7 days. Consult your physician.")
        val engine = CameraPerceptionEngine()
        val detector = TfLiteVisionDetector()
        val tool = CameraVisionTool(perceptionEngine = engine, detector = detector, llmClient = mockLlm)

        val result = tool.execute(mapOf("query" to "read prescription", "mode" to "prescription"))
        assertTrue(result is ToolResult.Success)
        val msg = (result as ToolResult.Success).message
        assertTrue(msg.contains("Amoxicillin 500mg"))
        assertNotNull(mockLlm.lastPrompt)
        assertTrue(mockLlm.lastPrompt!!.contains("Medical", ignoreCase = true) || mockLlm.lastPrompt!!.contains("Prescription", ignoreCase = true))
    }

    @Test
    fun testCameraVisionToolExecutionOfflinePrescriptionFallback() = runBlocking {
        val engine = CameraPerceptionEngine()
        val detector = TfLiteVisionDetector()
        val tool = CameraVisionTool(perceptionEngine = engine, detector = detector, llmClient = null)

        val result = tool.execute(mapOf("mode" to "prescription"))
        assertTrue(result is ToolResult.Success)
        val msg = (result as ToolResult.Success).message
        assertTrue(msg.contains("offline", ignoreCase = true))
    }

    @Test
    fun testIntentResolverLensReaderFastPaths() {
        val p1 = IntentResolver.resolve("read prescription")
        assertNotNull(p1)
        assertEquals(AssistantIntent.CAMERA_VISION, p1?.intent)
        assertEquals("prescription", p1?.params?.get("mode"))

        val p2 = IntentResolver.resolve("dawa padho")
        assertNotNull(p2)
        assertEquals(AssistantIntent.CAMERA_VISION, p2?.intent)
        assertEquals("prescription", p2?.params?.get("mode"))

        val d1 = IntentResolver.resolve("read this document")
        assertNotNull(d1)
        assertEquals(AssistantIntent.CAMERA_VISION, d1?.intent)
        assertEquals("document", d1?.params?.get("mode"))

        val n1 = IntentResolver.resolve("check calories")
        assertNotNull(n1)
        assertEquals(AssistantIntent.CAMERA_VISION, n1?.intent)
        assertEquals("nutrition", n1?.params?.get("mode"))

        val b1 = IntentResolver.resolve("scan barcode")
        assertNotNull(b1)
        assertEquals(AssistantIntent.CAMERA_VISION, b1?.intent)
        assertEquals("barcode", b1?.params?.get("mode"))
    }
}
