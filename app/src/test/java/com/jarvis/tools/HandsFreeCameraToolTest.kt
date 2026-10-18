package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeCameraToolTest {

    @Test
    fun testIntentResolverTakePhoto() {
        val utterances = listOf("take a picture", "take a photo", "capture photo", "photo khicho")
        for (u in utterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be HANDS_FREE_CAMERA for '$u'", AssistantIntent.HANDS_FREE_CAMERA, resolved?.intent)
            assertEquals("Action should be take_photo for '$u'", "take_photo", resolved?.params?.get("action"))
            assertEquals("Facing should be back for '$u'", "back", resolved?.params?.get("facing"))
        }
    }

    @Test
    fun testIntentResolverTakeSelfie() {
        val utterances = listOf("take a selfie", "click selfie", "selfie lo", "take selfie")
        for (u in utterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be HANDS_FREE_CAMERA for '$u'", AssistantIntent.HANDS_FREE_CAMERA, resolved?.intent)
            assertEquals("Action should be take_photo for '$u'", "take_photo", resolved?.params?.get("action"))
            assertEquals("Facing should be front for '$u'", "front", resolved?.params?.get("facing"))
        }
    }

    @Test
    fun testIntentResolverLaunchCameraAndVideo() {
        val cameraResolved = IntentResolver.resolve("open camera")
        assertNotNull(cameraResolved)
        assertEquals(AssistantIntent.HANDS_FREE_CAMERA, cameraResolved?.intent)
        assertEquals("launch_camera", cameraResolved?.params?.get("action"))
        assertEquals("still", cameraResolved?.params?.get("mode"))

        val videoResolved = IntentResolver.resolve("record video")
        assertNotNull(videoResolved)
        assertEquals(AssistantIntent.HANDS_FREE_CAMERA, videoResolved?.intent)
        assertEquals("launch_camera", videoResolved?.params?.get("action"))
        assertEquals("video", videoResolved?.params?.get("mode"))

        val shutterResolved = IntentResolver.resolve("press shutter")
        assertNotNull(shutterResolved)
        assertEquals(AssistantIntent.HANDS_FREE_CAMERA, shutterResolved?.intent)
        assertEquals("shutter", shutterResolved?.params?.get("action"))
    }

    @Test
    fun testHandsFreeCameraToolExecutionWithoutContext() = runBlocking {
        val tool = HandsFreeCameraTool(context = null)
        val photoResult = tool.execute(mapOf("action" to "take_photo", "facing" to "back"))
        assertTrue("Execution should succeed or fallback gracefully", photoResult is ToolResult.Success)
        assertTrue(photoResult.message.contains("Photo", ignoreCase = true) || photoResult.message.contains("captured", ignoreCase = true))

        val shutterResult = tool.execute(mapOf("action" to "shutter"))
        assertTrue(shutterResult is ToolResult.Success)
    }
}
