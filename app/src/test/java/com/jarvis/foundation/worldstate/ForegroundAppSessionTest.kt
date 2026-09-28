package com.jarvis.foundation.worldstate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppSessionTest {
    @Test
    fun mapsEachObservedForegroundPackageWithoutRetainingThePreviousApp() {
        val foregroundPackages = listOf(
            "com.whatsapp",
            "com.instagram.android",
            "com.android.chrome",
            "org.telegram.messenger",
            "com.google.android.youtube",
            "com.whatsapp.w4b",
            "com.jarvis"
        )

        assertEquals(
            listOf("WhatsApp", "Instagram", "Chrome", "Telegram", "YouTube", "WhatsApp", "jarvis"),
            foregroundPackages.map(ForegroundAppSession::fromPackage)
        )
        assertNull(ForegroundAppSession.fromPackage(null))
        assertEquals("Spotify", ForegroundAppSession.fromPackage("com.spotify.music"))
        assertFalse(ForegroundAppSession.supportsSharedUi(ForegroundAppSession.fromPackage("com.jarvis")))
    }

    @Test
    fun contextualSearchUsesOnlySupportedForegroundApps() {
        assertTrue(ForegroundAppSession.supportsSharedUi("Telegram"))
        assertEquals("hiking boots", ForegroundAppSession.contextualSearchQuery("search for hiking boots in this app", "Instagram"))
        assertEquals("web development", ForegroundAppSession.contextualSearchQuery("search for web development", "Chrome"))
        assertEquals("notes", ForegroundAppSession.contextualSearchQuery("search for notes", "YouTube"))
        assertNull(ForegroundAppSession.contextualSearchQuery("search web for hiking boots", "Chrome"))
        assertNull(ForegroundAppSession.contextualSearchQuery("search in notes for hiking boots", "Chrome"))
        assertFalse(ForegroundAppSession.supportsSharedUi("Spotify"))
        assertNull(ForegroundAppSession.contextualSearchQuery("search hiking boots", "Spotify"))
    }
}
