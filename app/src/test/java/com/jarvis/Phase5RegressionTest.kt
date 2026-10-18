package com.jarvis

import android.graphics.Bitmap
import android.graphics.Rect
import com.jarvis.accessibility.ScreenCaptureResult
import com.jarvis.accessibility.ScreenElement
import com.jarvis.accessibility.ScreenVisionManager
import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.camera.CameraLensReader
import com.jarvis.camera.LensReaderMode
import com.jarvis.camera.SceneAnalysis
import com.jarvis.tools.ScreenVisionTool
import com.jarvis.tools.Tool
import com.jarvis.tools.ToolResult
import com.jarvis.vision.LinkSafetyStatus
import com.jarvis.vision.PaymentStatus
import com.jarvis.vision.ScreenOcrParser
import com.jarvis.vision.VisualQnaEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase5RegressionTest {

    @Test
    fun testPhase5OcrStructuredExtraction() {
        val invoiceText = """
            Amazon India Checkout
            Order ID: #ORD-98214-IN
            Tracking: TRACK-99120-DEL
            Subtotal: ₹1,199.00
            Delivery: ₹50.00
            Grand Total: ₹1,249.00
            Delivery OTP: 738192
            Support: help@amazon.in, +91-98765-43210
        """.trimIndent()

        val parsed = ScreenOcrParser.parse(invoiceText)
        assertEquals("ORD-98214-IN", parsed.orderIds.first())
        assertEquals("TRACK-99120-DEL", parsed.trackingNumbers.first())
        assertTrue(parsed.totals.any { it.contains("1,249.00") })
        assertEquals("738192", parsed.verificationCodes.first())
        assertEquals("help@amazon.in", parsed.emails.first())
        assertTrue(parsed.phoneNumbers.any { it.contains("98765-43210") })

        val totalTarget = ScreenOcrParser.extractTarget(invoiceText, "total")
        assertTrue(totalTarget!!.contains("1,249.00"))

        val otpTarget = ScreenOcrParser.extractTarget(invoiceText, "otp")
        assertEquals("738192", otpTarget)
    }

    @Test
    fun testPhase5ScreenTranslationWithPayloadBounds() = runBlocking {
        var receivedPayload: String? = null
        val mockTranslator = object : Tool {
            override val name: String = "TRANSLATOR"
            override suspend fun execute(params: Map<String, String>): ToolResult {
                receivedPayload = params["text"]
                return ToolResult.Success(
                    message = "In Hindi: \"सफलतापूर्वक अनुवादित\"",
                    data = mapOf("translation" to "सफलतापूर्वक अनुवादित", "target_lang" to "hi")
                )
            }
        }

        val capture = ScreenCaptureResult(
            base64Image = null,
            uiTextTree = "1. [Android 10 on LG K42 with 3GB RAM]\n2. [Requires strict memory and payload constraints.]",
            activePackage = "com.android.chrome",
            activeActivity = null,
            isVisualAvailable = false,
            elements = listOf(ScreenElement(1, "Test", null, null, false, false, Rect(), emptyList()))
        )

        val tool = ScreenVisionTool(
            context = null,
            llmClient = null,
            captureScreen = { capture },
            translatorTool = mockTranslator
        )

        val result = tool.execute(mapOf("mode" to "translate", "target_lang" to "hi"))
        assertTrue(result is ToolResult.Success)
        val msg = (result as ToolResult.Success).message
        assertTrue(msg.contains("सफलतापूर्वक अनुवादित"))
        assertNotNull(receivedPayload)
        assertTrue(receivedPayload!!.length <= 800)
    }

    @Test
    fun testPhase5CameraLensReaderModes() {
        val prescriptionAnalysis = CameraLensReader.analyzeOffline(
            mode = LensReaderMode.PRESCRIPTION,
            scene = SceneAnalysis("Medicine", emptyList(), 0.6f, "Normal", listOf("White"), "Dense", "Medicine bottle")
        )
        assertTrue(prescriptionAnalysis.contains("offline", ignoreCase = true))
        assertTrue(prescriptionAnalysis.contains("doctor", ignoreCase = true) || prescriptionAnalysis.contains("consult", ignoreCase = true))

        val documentPrompt = CameraLensReader.buildSystemPrompt(
            mode = LensReaderMode.DOCUMENT,
            query = "read this document",
            facing = "back",
            sceneSummary = "Text page",
            lighting = "Good"
        )
        assertTrue(documentPrompt.contains("Optical Document Reader", ignoreCase = true))

        val nutritionPrompt = CameraLensReader.buildSystemPrompt(
            mode = LensReaderMode.NUTRITION,
            query = "check calories",
            facing = "back",
            sceneSummary = "Cereal box",
            lighting = "Bright"
        )
        assertTrue(nutritionPrompt.contains("Nutrition", ignoreCase = true))
    }

    @Test
    fun testPhase5VisualQnaPaymentStatusPrioritization() {
        // Critical Review Focus #5: Failed payment must not report success just because "payment" is present
        val failedUi = "Order Status: Payment Failed due to bank server timeout. Please try again."
        val result = VisualQnaEngine.checkPaymentStatus(failedUi, emptyList(), "com.phonepe.app")
        assertEquals(PaymentStatus.FAILED, result.status)

        val successUi = "Order Confirmed! Payment Successful of $29.99. Transaction ID: TXN_8819201"
        val successResult = VisualQnaEngine.checkPaymentStatus(successUi, emptyList(), "com.amazon.mShop.android.shopping")
        assertEquals(PaymentStatus.SUCCESS, successResult.status)
        assertEquals("TXN_8819201", successResult.transactionId)

        val linkResult = VisualQnaEngine.checkLinkSafety("http://insecure-site.com/login", emptyList())
        assertEquals(LinkSafetyStatus.UNSAFE, linkResult.status)
    }

    @Test
    fun testPhase5Android10Api29NullBitmapSafety() = runBlocking {
        // LG K42 on Android 10 API 29 has no AccessibilityService.takeScreenshot()
        // Must never throw NullPointerException or unsupported API exception
        val api29Capture = ScreenCaptureResult(
            base64Image = null, // null screenshot bitmap
            uiTextTree = "Foreground App: com.android.settings\n1. [Wi-Fi] (clickable=true)\n2. [Bluetooth] (clickable=true)",
            activePackage = "com.android.settings",
            activeActivity = null,
            isVisualAvailable = false,
            elements = listOf(
                ScreenElement(1, "Wi-Fi", null, null, true, false, Rect(0, 0, 1080, 150), emptyList()),
                ScreenElement(2, "Bluetooth", null, null, true, false, Rect(0, 150, 1080, 300), emptyList())
            )
        )

        val tool = ScreenVisionTool(
            context = null,
            llmClient = null,
            captureScreen = { api29Capture }
        )

        val result = tool.execute(mapOf("query" to "what is on my screen"))
        assertTrue(result is ToolResult.Success)
        val msg = (result as ToolResult.Success).message
        assertTrue(msg.contains("Settings", ignoreCase = true))
        assertTrue(msg.contains("Wi-Fi"))
    }

    @Test
    fun testPhase5BitmapDownscalingSafety() {
        // High resolution (e.g. 2160 x 4800)
        val (w1, h1) = ScreenVisionManager.calculateScaledDimensions(2160, 4800, maxDim = 1024)
        assertTrue(w1 <= 1024)
        assertTrue(h1 <= 1024)
        assertEquals(1024, h1)

        // Samsung Galaxy A51 resolution (1080 x 2400)
        val (wA51, hA51) = ScreenVisionManager.calculateScaledDimensions(1080, 2400, maxDim = 1024)
        assertTrue(wA51 <= 1024)
        assertEquals(1024, hA51)

        // LG K42 resolution (720 x 1600)
        val (wK42, hK42) = ScreenVisionManager.calculateScaledDimensions(720, 1600, maxDim = 1024)
        assertTrue(wK42 <= 1024)
        assertEquals(1024, hK42)

        // Smaller than maxDim (e.g. 800 x 600) remains untouched
        val (wSmall, hSmall) = ScreenVisionManager.calculateScaledDimensions(800, 600, maxDim = 1024)
        assertEquals(800, wSmall)
        assertEquals(600, hSmall)
    }

    @Test
    fun testPhase5IntentResolverBilingualCoverage() {
        // OCR / Extraction
        val i1 = IntentResolver.resolve("did the payment go through")
        assertEquals(AssistantIntent.SCREEN_VISION, i1?.intent)
        assertEquals("payment_check", i1?.params?.get("mode"))

        val i2 = IntentResolver.resolve("payment ho gaya kya")
        assertEquals(AssistantIntent.SCREEN_VISION, i2?.intent)
        assertEquals("payment_check", i2?.params?.get("mode"))

        // Translation
        val i3 = IntentResolver.resolve("translate screen to hindi")
        assertEquals(AssistantIntent.SCREEN_VISION, i3?.intent)
        assertEquals("translate", i3?.params?.get("mode"))
        assertEquals("hi", i3?.params?.get("target_lang"))

        val i4 = IntentResolver.resolve("screen translate karo")
        assertEquals(AssistantIntent.SCREEN_VISION, i4?.intent)
        assertEquals("translate", i4?.params?.get("mode"))

        // Camera Lens Reader
        val i5 = IntentResolver.resolve("read prescription")
        assertEquals(AssistantIntent.CAMERA_VISION, i5?.intent)
        assertEquals("prescription", i5?.params?.get("mode"))

        val i6 = IntentResolver.resolve("dawa padho")
        assertEquals(AssistantIntent.CAMERA_VISION, i6?.intent)
        assertEquals("prescription", i6?.params?.get("mode"))

        val i7 = IntentResolver.resolve("read this document")
        assertEquals(AssistantIntent.CAMERA_VISION, i7?.intent)
        assertEquals("document", i7?.params?.get("mode"))

        val i8 = IntentResolver.resolve("check calories")
        assertEquals(AssistantIntent.CAMERA_VISION, i8?.intent)
        assertEquals("nutrition", i8?.params?.get("mode"))

        val i9 = IntentResolver.resolve("scan barcode")
        assertEquals(AssistantIntent.CAMERA_VISION, i9?.intent)
        assertEquals("barcode", i9?.params?.get("mode"))

        // Button Locator
        val i10 = IntentResolver.resolve("find the checkout button")
        assertEquals(AssistantIntent.SCREEN_VISION, i10?.intent)
        assertEquals("find_button", i10?.params?.get("mode"))
        assertEquals("checkout", i10?.params?.get("target"))

        // Link Safety
        val i11 = IntentResolver.resolve("is this link safe")
        assertEquals(AssistantIntent.SCREEN_VISION, i11?.intent)
        assertEquals("link_check", i11?.params?.get("mode"))
    }
}
