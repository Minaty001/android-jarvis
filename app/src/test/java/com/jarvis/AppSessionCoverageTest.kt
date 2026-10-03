package com.jarvis

import com.jarvis.foundation.worldstate.ForegroundAppSession
import com.jarvis.tools.AppRegistry
import org.junit.Assert.*
import org.junit.Test

class AppSessionCoverageTest {

    @Test
    fun testAllRegisteredAppsHaveAppSessions() {
        val packages = AppRegistry.ALLOWED_PACKAGES.values.toSet()
        assertTrue("AppRegistry must have registered packages", packages.isNotEmpty())

        packages.forEach { pkg ->
            val sessionName = ForegroundAppSession.fromPackage(pkg)
            assertNotNull("Package '$pkg' should resolve to an active app session", sessionName)
            assertTrue("Session name for '$pkg' must not be blank", sessionName!!.isNotBlank())
            if (sessionName != "Spotify") {
                assertTrue("App session '$sessionName' for package '$pkg' (in sharedUiApps: ${sessionName in ForegroundAppSession.sharedUiApps}) should support shared UI", ForegroundAppSession.supportsSharedUi(sessionName))
            }
        }
    }

    @Test
    fun testSpecificAppSessionMappings() {
        assertEquals("YouTube", ForegroundAppSession.fromPackage("com.google.android.youtube"))
        assertEquals("WhatsApp", ForegroundAppSession.fromPackage("com.whatsapp"))
        assertEquals("ChatGPT", ForegroundAppSession.fromPackage("com.openai.chatgpt"))
        assertEquals("Play Store", ForegroundAppSession.fromPackage("com.android.vending"))
        assertEquals("Settings", ForegroundAppSession.fromPackage("com.android.settings"))
        assertEquals("Google Keep", ForegroundAppSession.fromPackage("com.google.android.keep"))
        assertEquals("Samsung Notes", ForegroundAppSession.fromPackage("com.samsung.android.app.notes"))
        assertEquals("PhonePe", ForegroundAppSession.fromPackage("com.phonepe.app"))
        assertEquals("Kotak 811", ForegroundAppSession.fromPackage("com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge"))
        assertEquals("MobiKwik", ForegroundAppSession.fromPackage("com.mobikwik_new"))
        assertEquals("Gallery", ForegroundAppSession.fromPackage("com.sec.android.gallery3d"))
    }

    @Test
    fun testContextualSearchQueryAcrossAppSessions() {
        val session = ForegroundAppSession.fromPackage("com.android.vending")
        assertEquals("Play Store", session)

        val q1 = ForegroundAppSession.contextualSearchQuery("search for Khan Academy", session)
        assertEquals("Khan Academy", q1)

        val q2 = ForegroundAppSession.contextualSearchQuery("search calculus in this app", "ChatGPT")
        assertEquals("calculus", q2)

        val q3 = ForegroundAppSession.contextualSearchQuery("search for python tutorial", "YouTube")
        assertEquals("python tutorial", q3)
    }
}
