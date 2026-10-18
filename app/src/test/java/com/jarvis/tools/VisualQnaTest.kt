package com.jarvis.tools

import android.graphics.Rect
import com.jarvis.accessibility.ScreenCaptureResult
import com.jarvis.accessibility.ScreenElement
import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.vision.LinkSafetyStatus
import com.jarvis.vision.PaymentStatus
import com.jarvis.vision.VisualQnaEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VisualQnaTest {

    @Test
    fun testPaymentStatusSuccessDetection() {
        val successUi = """
            Google Pay
            Payment Successful!
            Paid ₹1,250.00 to Grocery Store
            Transaction ID: TXN9812401928
        """.trimIndent()

        val result = VisualQnaEngine.checkPaymentStatus(successUi, emptyList(), "com.google.android.apps.nbu.paisa.user")
        assertEquals(PaymentStatus.SUCCESS, result.status)
        assertTrue(result.explanation.contains("successful", ignoreCase = true))
        assertEquals("TXN9812401928", result.transactionId)
        assertTrue(result.amount!!.contains("1,250"))
    }

    @Test
    fun testPaymentStatusFailedPrioritizedOverPaymentWord() {
        // Must NOT falsely report success just because "payment" is present
        val failedUi = """
            Payment Failed
            Transaction declined by bank. Please try again with UPI or NetBanking.
            Error Code: ERR_DECLINED_504
        """.trimIndent()

        val result = VisualQnaEngine.checkPaymentStatus(failedUi, emptyList(), "com.phonepe.app")
        assertEquals(PaymentStatus.FAILED, result.status)
        assertTrue(result.explanation.contains("failed", ignoreCase = true) || result.explanation.contains("declined", ignoreCase = true))
    }

    @Test
    fun testPaymentStatusPending() {
        val pendingUi = """
            Processing Payment
            Please wait while we confirm your transaction with your bank. Do not press back.
        """.trimIndent()

        val result = VisualQnaEngine.checkPaymentStatus(pendingUi, emptyList(), "com.amazon.mShop.android.shopping")
        assertEquals(PaymentStatus.PENDING, result.status)
        assertTrue(result.explanation.contains("processing", ignoreCase = true) || result.explanation.contains("pending", ignoreCase = true))
    }

    @Test
    fun testLinkSafetyChecks() {
        val unsafeUi = "Browser URL: http://insecure-bank-login.com/login. Warning: Not Secure."
        val unsafeResult = VisualQnaEngine.checkLinkSafety(unsafeUi, emptyList())
        assertEquals(LinkSafetyStatus.UNSAFE, unsafeResult.status)
        assertTrue(unsafeResult.reason.contains("unencrypted", ignoreCase = true) || unsafeResult.reason.contains("not secure", ignoreCase = true))

        val secureUi = "URL: https://accounts.google.com/signin. Connection is secure."
        val secureResult = VisualQnaEngine.checkLinkSafety(secureUi, emptyList())
        assertEquals(LinkSafetyStatus.SECURE, secureResult.status)
        assertTrue(secureResult.reason.contains("secure", ignoreCase = true) || secureResult.reason.contains("encrypted", ignoreCase = true))
    }

    @Test
    fun testFindButtonLocatesElement() {
        val elements = listOf(
            ScreenElement(1, "Shopping Cart (3 items)", null, "cart_title", false, false, Rect(0, 0, 1080, 100), emptyList()),
            ScreenElement(2, "Subtotal: $45.00", null, "subtotal", false, false, Rect(0, 100, 1080, 200), emptyList()),
            ScreenElement(3, "Proceed to Checkout", null, "btn_checkout", true, false, Rect(50, 500, 1030, 650), emptyList())
        )

        val found = VisualQnaEngine.findButton("checkout", elements)
        assertTrue(found.found)
        assertEquals(3, found.index)
        assertTrue(found.isClickable)
        assertTrue(found.label!!.contains("Checkout"))

        val notFound = VisualQnaEngine.findButton("delete account", elements)
        assertFalse(notFound.found)
    }

    @Test
    fun testScreenVisionToolQnaExecution() = runBlocking {
        val capture = ScreenCaptureResult(
            base64Image = null,
            uiTextTree = "Payment Successful to Merchant XYZ. Paid $49.99.",
            activePackage = "com.google.android.apps.nbu.paisa.user",
            activeActivity = null,
            isVisualAvailable = false,
            elements = listOf(
                ScreenElement(1, "Payment Successful", null, null, false, false, Rect(), emptyList()),
                ScreenElement(2, "Done", null, "btn_done", true, false, Rect(100, 100, 200, 200), emptyList())
            )
        )

        val tool = ScreenVisionTool(
            context = null,
            llmClient = null,
            captureScreen = { capture }
        )

        val paymentRes = tool.execute(mapOf("mode" to "payment_check"))
        assertTrue(paymentRes is ToolResult.Success)
        assertTrue((paymentRes as ToolResult.Success).message.contains("successful", ignoreCase = true))

        val buttonRes = tool.execute(mapOf("mode" to "find_button", "target" to "done"))
        assertTrue(buttonRes is ToolResult.Success)
        assertTrue((buttonRes as ToolResult.Success).message.contains("Done"))
    }

    @Test
    fun testIntentResolverVisualQnaFastPaths() {
        val i1 = IntentResolver.resolve("did the payment go through")
        assertNotNull(i1)
        assertEquals(AssistantIntent.SCREEN_VISION, i1?.intent)
        assertEquals("payment_check", i1?.params?.get("mode"))

        val i2 = IntentResolver.resolve("payment ho gaya kya")
        assertNotNull(i2)
        assertEquals(AssistantIntent.SCREEN_VISION, i2?.intent)
        assertEquals("payment_check", i2?.params?.get("mode"))

        val i3 = IntentResolver.resolve("is this link safe")
        assertNotNull(i3)
        assertEquals(AssistantIntent.SCREEN_VISION, i3?.intent)
        assertEquals("link_check", i3?.params?.get("mode"))

        val i4 = IntentResolver.resolve("find the checkout button")
        assertNotNull(i4)
        assertEquals(AssistantIntent.SCREEN_VISION, i4?.intent)
        assertEquals("find_button", i4?.params?.get("mode"))
        assertEquals("checkout", i4?.params?.get("target"))

        val i5 = IntentResolver.resolve("where is the submit button")
        assertNotNull(i5)
        assertEquals(AssistantIntent.SCREEN_VISION, i5?.intent)
        assertEquals("find_button", i5?.params?.get("mode"))
        assertEquals("submit", i5?.params?.get("target"))
    }
}
