package com.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HandsFreeNavigationIntentTest {

    @Test
    fun testGlobalNavigationIntents() {
        val backIntent = IntentResolver.resolve("go back")
        assertNotNull(backIntent)
        assertEquals(AssistantIntent.UI_GLOBAL_ACTION, backIntent?.intent)
        assertEquals("back", backIntent?.params?.get("action"))
        assertEquals("UI_GLOBAL", backIntent?.directPlan?.actions?.firstOrNull()?.type)

        val homeIntent = IntentResolver.resolve("go home")
        assertNotNull(homeIntent)
        assertEquals(AssistantIntent.UI_GLOBAL_ACTION, homeIntent?.intent)
        assertEquals("home", homeIntent?.params?.get("action"))

        val recentsIntent = IntentResolver.resolve("recent apps")
        assertNotNull(recentsIntent)
        assertEquals(AssistantIntent.UI_GLOBAL_ACTION, recentsIntent?.intent)
        assertEquals("recents", recentsIntent?.params?.get("action"))

        val notifsIntent = IntentResolver.resolve("notifications")
        assertNotNull(notifsIntent)
        assertEquals(AssistantIntent.UI_GLOBAL_ACTION, notifsIntent?.intent)
        assertEquals("notifications", notifsIntent?.params?.get("action"))

        val quickSettingsIntent = IntentResolver.resolve("quick settings")
        assertNotNull(quickSettingsIntent)
        assertEquals(AssistantIntent.UI_GLOBAL_ACTION, quickSettingsIntent?.intent)
        assertEquals("quick_settings", quickSettingsIntent?.params?.get("action"))

        val screenshotIntent = IntentResolver.resolve("take screenshot")
        assertNotNull(screenshotIntent)
        assertEquals(AssistantIntent.UI_GLOBAL_ACTION, screenshotIntent?.intent)
        assertEquals("screenshot", screenshotIntent?.params?.get("action"))
    }

    @Test
    fun testScrollingIntents() {
        val scrollDown = IntentResolver.resolve("scroll down")
        assertNotNull(scrollDown)
        assertEquals(AssistantIntent.UI_SCROLL_ACTION, scrollDown?.intent)
        assertEquals("down", scrollDown?.params?.get("direction"))

        val scrollUp = IntentResolver.resolve("scroll up")
        assertNotNull(scrollUp)
        assertEquals(AssistantIntent.UI_SCROLL_ACTION, scrollUp?.intent)
        assertEquals("up", scrollUp?.params?.get("direction"))
    }

    @Test
    fun testVoiceAccessOverlayIntents() {
        val showNumbers = IntentResolver.resolve("show numbers")
        assertNotNull(showNumbers)
        assertEquals(AssistantIntent.VOICE_ACCESS_OVERLAY, showNumbers?.intent)
        assertEquals("show", showNumbers?.params?.get("action"))

        val hideNumbers = IntentResolver.resolve("hide numbers")
        assertNotNull(hideNumbers)
        assertEquals(AssistantIntent.VOICE_ACCESS_OVERLAY, hideNumbers?.intent)
        assertEquals("hide", hideNumbers?.params?.get("action"))

        val tap3 = IntentResolver.resolve("tap 3")
        assertNotNull(tap3)
        assertEquals(AssistantIntent.VOICE_ACCESS_OVERLAY, tap3?.intent)
        assertEquals("click", tap3?.params?.get("action"))
        assertEquals("3", tap3?.params?.get("number"))

        val click12 = IntentResolver.resolve("click 12")
        assertNotNull(click12)
        assertEquals(AssistantIntent.VOICE_ACCESS_OVERLAY, click12?.intent)
        assertEquals("click", click12?.params?.get("action"))
        assertEquals("12", click12?.params?.get("number"))
    }

    @Test
    fun testScreenReaderIntent() {
        val readScreen = IntentResolver.resolve("read screen")
        assertNotNull(readScreen)
        assertEquals(AssistantIntent.SCREEN_READER, readScreen?.intent)
        assertEquals("read", readScreen?.params?.get("action"))

        val readArticle = IntentResolver.resolve("read article")
        assertNotNull(readArticle)
        assertEquals(AssistantIntent.SCREEN_READER, readArticle?.intent)
        assertEquals("read", readArticle?.params?.get("action"))
    }
}
