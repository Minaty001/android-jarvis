package com.jarvis.tools

import com.jarvis.accessibility.ScreenCaptureResult
import com.jarvis.accessibility.ScreenElement
import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScreenTranslationTest {

    private val sampleUiText = """
        Foreground App: com.android.chrome (Chrome)
        Visible UI Elements (5 items):
        1. [Welcome to the National Museum of History] (clickable=false, editable=false)
        2. [The museum is open daily from 9 AM to 6 PM.] (clickable=false, editable=false)
        3. [Tickets must be booked online in advance.] (clickable=false, editable=false)
        4. [Guided tours are available every Saturday morning.] (clickable=false, editable=false)
        5. [Enjoy your visit.] (clickable=false, editable=false)
    """.trimIndent()

    @Test
    fun testScreenVisionToolTranslatesScreenContent() = runBlocking {
        var translatedInput: String? = null
        var requestedTargetLang: String? = null

        val mockTranslator = object : Tool {
            override val name: String = "TRANSLATOR"
            override suspend fun execute(params: Map<String, String>): ToolResult {
                translatedInput = params["text"]
                requestedTargetLang = params["target_lang"]
                return ToolResult.Success(
                    message = "In Hindi: \"राष्ट्रीय इतिहास संग्रहालय में आपका स्वागत है।\"",
                    data = mapOf(
                        "original" to (params["text"] ?: ""),
                        "translation" to "राष्ट्रीय इतिहास संग्रहालय में आपका स्वागत है।",
                        "target_lang" to (params["target_lang"] ?: "hi")
                    )
                )
            }
        }

        val capture = ScreenCaptureResult(
            base64Image = null,
            uiTextTree = sampleUiText,
            activePackage = "com.android.chrome",
            activeActivity = "com.google.android.apps.chrome.Main",
            isVisualAvailable = false,
            elements = listOf(
                ScreenElement(1, "Welcome to the National Museum of History", null, null, false, false, android.graphics.Rect(), emptyList())
            )
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
        assertTrue(msg.contains("Hindi", ignoreCase = true))
        assertTrue(msg.contains("राष्ट्रीय इतिहास संग्रहालय"))
        assertEquals("hi", requestedTargetLang)
        assertNotNull(translatedInput)
        assertTrue(translatedInput!!.contains("Welcome to the National Museum"))
    }

    @Test
    fun testScreenTranslationCapsPayloadForLowMemory() = runBlocking {
        // Generate massive UI tree exceeding 800 characters
        val massiveTree = buildString {
            appendLine("Foreground App: com.example.article")
            appendLine("Visible UI Elements (50 items):")
            for (i in 1..50) {
                appendLine("$i. [Paragraph number $i: Android operating system on low-end MediaTek Helio P22 and Samsung Exynos 9611 devices requires strict memory constraints.] (clickable=false)")
            }
        }

        var translatedTextPayload: String? = null
        val mockTranslator = object : Tool {
            override val name: String = "TRANSLATOR"
            override suspend fun execute(params: Map<String, String>): ToolResult {
                translatedTextPayload = params["text"]
                return ToolResult.Success("Translated", mapOf("translation" to "Translated"))
            }
        }

        val tool = ScreenVisionTool(
            context = null,
            llmClient = null,
            captureScreen = {
                ScreenCaptureResult(null, massiveTree, "com.example.article", null, false,
                    elements = listOf(ScreenElement(1, "Article", null, null, false, false, android.graphics.Rect(), emptyList())))
            },
            translatorTool = mockTranslator
        )

        tool.execute(mapOf("mode" to "translate", "target_lang" to "es"))

        assertNotNull(translatedTextPayload)
        // Ensure capped at <= 800 chars to avoid memory/network spikes on 3GB RAM devices
        assertTrue("Payload length was ${translatedTextPayload!!.length}", translatedTextPayload!!.length <= 800)
    }

    @Test
    fun testIntentResolverScreenTranslateFastPath() {
        val intent1 = IntentResolver.resolve("translate screen to hindi")
        assertNotNull(intent1)
        assertEquals(AssistantIntent.SCREEN_VISION, intent1?.intent)
        assertEquals("translate", intent1?.params?.get("mode"))
        assertEquals("hi", intent1?.params?.get("target_lang"))

        val intent2 = IntentResolver.resolve("translate this page to english")
        assertNotNull(intent2)
        assertEquals(AssistantIntent.SCREEN_VISION, intent2?.intent)
        assertEquals("translate", intent2?.params?.get("mode"))
        assertEquals("en", intent2?.params?.get("target_lang"))

        val intent3 = IntentResolver.resolve("screen translate karo")
        assertNotNull(intent3)
        assertEquals(AssistantIntent.SCREEN_VISION, intent3?.intent)
        assertEquals("translate", intent3?.params?.get("mode"))

        val intent4 = IntentResolver.resolve("what does this screen say in spanish")
        assertNotNull(intent4)
        assertEquals(AssistantIntent.SCREEN_VISION, intent4?.intent)
        assertEquals("translate", intent4?.params?.get("mode"))
        assertEquals("es", intent4?.params?.get("target_lang"))
    }
}
