package com.jarvis.tools

import com.jarvis.accessibility.ScreenCaptureResult
import com.jarvis.accessibility.ScreenElement
import com.jarvis.vision.ScreenOcrParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScreenOcrExtractionTest {

    private val sampleUiText = """
        Foreground App: com.amazon.mShop.android.shopping (Amazon)
        Visible UI Elements (8 items):
        1. [Amazon Order Summary] (clickable=false, editable=false)
        2. [Order #ORD-99214-X8] (clickable=false, editable=false)
        3. [Tracking ID: TRACK-88219-US] (clickable=false, editable=false)
        4. [Subtotal: $45.00] (clickable=false, editable=false)
        5. [Shipping: $4.99] (clickable=false, editable=false)
        6. [Grand Total: $49.99] (clickable=false, editable=false)
        7. [Verification Code: 839201 for delivery verification] (clickable=false, editable=false)
        8. [For support contact support@amazon.com or call +1-800-555-0199] (clickable=false, editable=false)
    """.trimIndent()

    @Test
    fun testParseTotalsAndPrices() {
        val data = ScreenOcrParser.parse(sampleUiText)

        assertFalse(data.totals.isEmpty())
        assertTrue(data.totals.any { it.contains("49.99") })

        assertFalse(data.prices.isEmpty())
        assertTrue(data.prices.any { it.contains("45.00") })
        assertTrue(data.prices.any { it.contains("4.99") })
    }

    @Test
    fun testParseIndianRupeePricesAndTotals() {
        val indianInvoice = """
            Flipkart Checkout
            Item Price: ₹1,250.00
            Delivery: Rs. 50
            Total: ₹1,300.00
            OTP: 4920
        """.trimIndent()

        val data = ScreenOcrParser.parse(indianInvoice)
        assertTrue(data.totals.any { it.contains("1,300.00") })
        assertTrue(data.prices.any { it.contains("1,250.00") })
        assertEquals(listOf("4920"), data.verificationCodes)
    }

    @Test
    fun testParseOrderIdsAndTracking() {
        val data = ScreenOcrParser.parse(sampleUiText)

        assertFalse(data.orderIds.isEmpty())
        assertTrue(data.orderIds.any { it.contains("ORD-99214-X8") })

        assertFalse(data.trackingNumbers.isEmpty())
        assertTrue(data.trackingNumbers.any { it.contains("TRACK-88219-US") })
    }

    @Test
    fun testParseContacts() {
        val data = ScreenOcrParser.parse(sampleUiText)

        assertEquals(listOf("support@amazon.com"), data.emails)
        assertTrue(data.phoneNumbers.any { it.contains("800-555-0199") })
    }

    @Test
    fun testExtractTargetEntity() {
        val total = ScreenOcrParser.extractTarget(sampleUiText, "total")
        assertNotNull(total)
        assertTrue(total!!.contains("49.99"))

        val otp = ScreenOcrParser.extractTarget(sampleUiText, "otp")
        assertEquals("839201", otp)

        val order = ScreenOcrParser.extractTarget(sampleUiText, "order")
        assertNotNull(order)
        assertTrue(order!!.contains("ORD-99214-X8"))

        val email = ScreenOcrParser.extractTarget(sampleUiText, "email")
        assertEquals("support@amazon.com", email)
    }

    @Test
    fun testScreenVisionToolExtractModeOffline() = runBlocking {
        val capture = ScreenCaptureResult(
            base64Image = null,
            uiTextTree = sampleUiText,
            activePackage = "com.amazon.mShop.android.shopping",
            activeActivity = "com.amazon.mShop.order.SummaryActivity",
            isVisualAvailable = false,
            elements = listOf(
                ScreenElement(1, "Grand Total: $49.99", null, null, false, false, android.graphics.Rect(), emptyList()),
                ScreenElement(2, "Verification Code: 839201", null, null, false, false, android.graphics.Rect(), emptyList())
            )
        )

        val tool = ScreenVisionTool(
            context = null,
            llmClient = null,
            captureScreen = { capture }
        )

        val result = tool.execute(mapOf("mode" to "extract", "target" to "total"))
        assertTrue(result is ToolResult.Success)
        val msg = (result as ToolResult.Success).message
        assertTrue(msg.contains("49.99"))

        val otpResult = tool.execute(mapOf("query" to "extract otp"))
        assertTrue(otpResult is ToolResult.Success)
        assertTrue((otpResult as ToolResult.Success).message.contains("839201"))
    }
}
