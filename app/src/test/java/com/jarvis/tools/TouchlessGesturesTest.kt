package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TouchlessGesturesTest {

    @Test
    fun testIntentResolverGestures() {
        val longPress1 = IntentResolver.resolve("long press 3")
        assertNotNull(longPress1)
        assertEquals(AssistantIntent.UI_GESTURE_ACTION, longPress1?.intent)
        assertEquals("long_click", longPress1?.params?.get("action"))
        assertEquals("3", longPress1?.params?.get("target"))

        val swipeLeft = IntentResolver.resolve("swipe left")
        assertNotNull(swipeLeft)
        assertEquals(AssistantIntent.UI_GESTURE_ACTION, swipeLeft?.intent)
        assertEquals("swipe", swipeLeft?.params?.get("action"))
        assertEquals("left", swipeLeft?.params?.get("direction"))

        val swipeRight = IntentResolver.resolve("swipe right")
        assertNotNull(swipeRight)
        assertEquals(AssistantIntent.UI_GESTURE_ACTION, swipeRight?.intent)
        assertEquals("swipe", swipeRight?.params?.get("action"))
        assertEquals("right", swipeRight?.params?.get("direction"))
    }

    @Test
    fun testUiAutomationManagerGesturesWithoutService() {
        val longRes = com.jarvis.accessibility.UiAutomationManager.performGestureAction("long_click", "3")
        assertNotNull(longRes)

        val swipeRes = com.jarvis.accessibility.UiAutomationManager.performGestureAction("swipe", "left")
        assertNotNull(swipeRes)
    }
}
