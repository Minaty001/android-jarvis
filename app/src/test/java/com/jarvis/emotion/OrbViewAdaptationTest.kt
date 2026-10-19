package com.jarvis.emotion

import com.jarvis.ui.model.AppSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbViewAdaptationTest {

    @Test
    fun testAllStatesUiEmotionProfile() {
        val states = AssistantState.values()
        for (state in states) {
            val profile = UiEmotionProfile.forState(state)
            assertEquals(state, profile.state)
            assertTrue(profile.primaryColor != 0)
            assertTrue(profile.secondaryColor != 0)
            assertTrue(profile.pulseDurationMs > 0L)
            assertTrue(profile.accessibleLabel.isNotBlank())
        }

        // Specifically test PANIC profile: urgent red with fastest pulse (600ms)
        val panicProfile = UiEmotionProfile.forState(AssistantState.PANIC)
        assertEquals(600L, panicProfile.pulseDurationMs)
        assertEquals(0xFFFF334B.toInt(), panicProfile.primaryColor)
        assertTrue(panicProfile.accessibleLabel.contains("Emergency") || panicProfile.accessibleLabel.contains("Alert"))

        // Specifically test HAPPY profile: verified green (1400ms)
        val happyProfile = UiEmotionProfile.forState(AssistantState.HAPPY)
        assertEquals(1400L, happyProfile.pulseDurationMs)
        assertEquals(0xFF4EFAAE.toInt(), happyProfile.primaryColor)

        // Specifically test SAD profile: slow pulse (3200ms)
        val sadProfile = UiEmotionProfile.forState(AssistantState.SAD)
        assertEquals(3200L, sadProfile.pulseDurationMs)
    }

    @Test
    fun testAppSettingsEmotionPreferencesDefaultValues() {
        val settings = AppSettings()
        assertTrue(settings.emotionalPresentationEnabled)
        assertTrue(settings.humorEnabled)
        assertTrue(settings.voiceExpressionEnabled)
        assertTrue(settings.uiEmotionAnimationsEnabled)
        assertEquals(0.65f, settings.sentimentConfidenceThreshold, 0.001f)
        assertFalse(settings.reducedMotionEnabled)
    }
}
