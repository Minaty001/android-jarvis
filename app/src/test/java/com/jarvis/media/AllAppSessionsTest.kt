package com.jarvis.media

import com.jarvis.tools.SpotifyControlTool
import com.jarvis.tools.WhatsAppTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AllAppSessionsTest {

    @Before
    fun setUp() {
        AppSessionRegistry.clear()
    }

    @Test
    fun testWhatsAppSessionManager() {
        WhatsAppSessionManager.recordChat("Rahul Sharma", message = "Hello Rahul")
        assertEquals("Rahul Sharma", WhatsAppSessionManager.getCurrentChat()?.contactName)
        assertEquals("Hello Rahul", WhatsAppSessionManager.getCurrentChat()?.lastMessage)

        WhatsAppSessionManager.recordChat("Priya Singh", message = "Meeting at 5 PM")
        assertEquals("Priya Singh", WhatsAppSessionManager.getCurrentChat()?.contactName)
        assertEquals(1, WhatsAppSessionManager.getHistory().size)

        val prev = WhatsAppSessionManager.getPreviousChat()
        assertNotNull(prev)
        assertEquals("Rahul Sharma", prev?.contactName)
        assertEquals("Rahul Sharma", WhatsAppSessionManager.getCurrentChat()?.contactName)
    }

    @Test
    fun testSpotifySessionManager() {
        SpotifySessionManager.recordTrackPlay("Starboy", artist = "The Weeknd")
        assertEquals("Starboy", SpotifySessionManager.getCurrentTrack()?.query)
        assertTrue(SpotifySessionManager.isPlaying())

        SpotifySessionManager.setPlaybackState(false)
        assertEquals(false, SpotifySessionManager.isPlaying())

        SpotifySessionManager.recordTrackPlay("Blinding Lights", artist = "The Weeknd")
        assertEquals("Blinding Lights", SpotifySessionManager.getCurrentTrack()?.query)
        assertEquals(1, SpotifySessionManager.getHistory().size)

        val prev = SpotifySessionManager.playPreviousTrack()
        assertNotNull(prev)
        assertEquals("Starboy", prev?.query)
    }

    @Test
    fun testAiAssistantSessionManager() {
        AiAssistantSessionManager.recordPrompt("ChatGPT", "Explain quantum computing")
        assertEquals("ChatGPT", AiAssistantSessionManager.getCurrentSession()?.appName)
        assertEquals("Explain quantum computing", AiAssistantSessionManager.getCurrentSession()?.promptQuery)

        AiAssistantSessionManager.recordPrompt("Claude", "Write a Kotlin data class")
        assertEquals("Claude", AiAssistantSessionManager.getCurrentSession()?.appName)
        assertEquals(1, AiAssistantSessionManager.getHistory().size)

        val prev = AiAssistantSessionManager.getPreviousSession()
        assertNotNull(prev)
        assertEquals("Explain quantum computing", prev?.promptQuery)
    }

    @Test
    fun testBrowserSessionManager() {
        BrowserSessionManager.recordNavigation("https://github.com", pageTitle = "GitHub")
        assertEquals("https://github.com", BrowserSessionManager.getCurrentTab()?.urlOrQuery)

        BrowserSessionManager.recordNavigation("kotlin tutorial", pageTitle = "Kotlin Tutorial")
        assertEquals("kotlin tutorial", BrowserSessionManager.getCurrentTab()?.urlOrQuery)
        assertEquals(1, BrowserSessionManager.getHistory().size)

        val prev = BrowserSessionManager.getPreviousTab()
        assertNotNull(prev)
        assertEquals("https://github.com", prev?.urlOrQuery)
    }

    @Test
    fun testUniversalAppSessionRegistry() {
        AppSessionRegistry.recordAppLaunch("com.whatsapp", "Opening chat with Rahul")
        assertEquals("WhatsApp", AppSessionRegistry.getActiveApp())

        AppSessionRegistry.recordAppLaunch("com.google.android.youtube", "Playing video")
        assertEquals("YouTube", AppSessionRegistry.getActiveApp())
        assertEquals(2, AppSessionRegistry.getTimeline().size)

        val summaryYT = AppSessionRegistry.getAppSessionSummary("YouTube")
        assertTrue(summaryYT.contains("App Session Summary"))

        val summaryWA = AppSessionRegistry.getAppSessionSummary("WhatsApp")
        assertTrue(summaryWA.contains("App Session Summary"))

        val summaryAll = AppSessionRegistry.getAppSessionSummary(null)
        assertTrue(summaryAll.contains("Universal App Session Overview"))
        assertTrue(summaryAll.contains("YouTube"))
    }

    @Test
    fun testWhatsAppToolSessionActions() = runBlocking {
        val tool = WhatsAppTool(context = null)

        val sendRes = tool.execute(mapOf("action" to "send_message", "recipient" to "Amit", "message" to "Hey Amit"))
        assertTrue(sendRes.success)
        assertEquals("Amit", WhatsAppSessionManager.getCurrentChat()?.contactName)

        val statusRes = tool.execute(mapOf("action" to "current_chat"))
        assertTrue(statusRes.success)
        assertTrue(statusRes.message.contains("Amit"))

        val historyRes = tool.execute(mapOf("action" to "chat_history"))
        assertTrue(historyRes.success)
    }

    @Test
    fun testSpotifyControlToolSessionActions() = runBlocking {
        val tool = SpotifyControlTool(context = null)

        tool.execute(mapOf("query" to "Interstellar Theme"))
        assertEquals("Interstellar Theme", SpotifySessionManager.getCurrentTrack()?.query)

        val statusRes = tool.execute(mapOf("action" to "current"))
        assertTrue(statusRes.success)
        assertTrue(statusRes.message.contains("Interstellar Theme"))

        val pauseRes = tool.execute(mapOf("action" to "pause"))
        assertTrue(pauseRes.success)
        assertEquals(false, SpotifySessionManager.isPlaying())

        val historyRes = tool.execute(mapOf("action" to "history"))
        assertTrue(historyRes.success)
    }
}
