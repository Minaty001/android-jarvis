package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessControlTest {

    @Test
    fun testIntentResolverBrightnessAdjustments() {
        val brightnessCases = listOf(
            "set brightness to 70%" to "70",
            "screen brightness to 30%" to "30",
            "brightness 50%" to "50",
            "brightness 80" to "80"
        )
        for ((utterance, expectedPercent) in brightnessCases) {
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals(AssistantIntent.DEVICE_SETTINGS, resolved?.intent)
            assertEquals("set_brightness", resolved?.params?.get("action"))
            assertEquals(expectedPercent, resolved?.params?.get("brightness_percent"))
        }

        val dimResolved = IntentResolver.resolve("dim screen")
        assertNotNull(dimResolved)
        assertEquals(AssistantIntent.DEVICE_SETTINGS, dimResolved?.intent)
        assertEquals("set_brightness", dimResolved?.params?.get("action"))
        assertEquals("20", dimResolved?.params?.get("brightness_percent"))

        val maxResolved = IntentResolver.resolve("full brightness")
        assertNotNull(maxResolved)
        assertEquals(AssistantIntent.DEVICE_SETTINGS, maxResolved?.intent)
        assertEquals("set_brightness", maxResolved?.params?.get("action"))
        assertEquals("100", maxResolved?.params?.get("brightness_percent"))
    }

    @Test
    fun testIntentResolverStreamVolumes() {
        val streamCases = listOf(
            "alarm volume to 80%" to Pair("alarm", "80"),
            "set alarm volume to 80" to Pair("alarm", "80"),
            "ringtone volume to 50%" to Pair("ring", "50"),
            "notification volume to 60%" to Pair("notification", "60"),
            "media volume to 40%" to Pair("music", "40")
        )
        for ((utterance, expected) in streamCases) {
            val (stream, percent) = expected
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals(AssistantIntent.MEDIA_VOLUME, resolved?.intent)
            assertEquals("set_volume", resolved?.params?.get("action"))
            assertEquals(stream, resolved?.params?.get("stream"))
            assertEquals(percent, resolved?.params?.get("volume_percent"))
        }
    }

    @Test
    fun testDeviceSettingsToolBrightnessExecution() = runBlocking {
        val tool = DeviceSettingsTool(context = null)
        val result = tool.execute(mapOf("action" to "set_brightness", "brightness_percent" to "70"))
        assertTrue("Execution should succeed in simulation mode", result is ToolResult.Success)
    }

    @Test
    fun testDeviceSettingsToolStreamVolumeExecution() = runBlocking {
        val tool = DeviceSettingsTool(context = null)
        val result = tool.execute(mapOf("action" to "set_volume", "stream" to "alarm", "volume_percent" to "80"))
        assertTrue("Execution without AudioManager should fail cleanly", result is ToolResult.Failed)
    }
}
