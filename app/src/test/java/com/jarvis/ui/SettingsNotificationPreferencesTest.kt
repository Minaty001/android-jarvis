package com.jarvis.ui

import com.jarvis.ui.model.AppSettings
import org.junit.Assert.*
import org.junit.Test

class SettingsNotificationPreferencesTest {

    @Test
    fun testDefaultSettingsHaveChannelsEnabled() {
        val settings = AppSettings()
        assertTrue(settings.notificationsEnabled)
        assertTrue(settings.reminderNotificationsEnabled)
        assertTrue(settings.proactiveAlertsEnabled)
        assertTrue(settings.dailyBriefingNotificationsEnabled)
    }

    @Test
    fun testSettingsAllowCustomChannelToggles() {
        val settings = AppSettings(
            notificationsEnabled = true,
            reminderNotificationsEnabled = true,
            proactiveAlertsEnabled = false,
            dailyBriefingNotificationsEnabled = false
        )
        assertTrue(settings.reminderNotificationsEnabled)
        assertFalse(settings.proactiveAlertsEnabled)
        assertFalse(settings.dailyBriefingNotificationsEnabled)
    }

    @Test
    fun testJsonRoundTripForNotificationPreferences() {
        val custom = AppSettings(
            notificationsEnabled = false,
            reminderNotificationsEnabled = false,
            proactiveAlertsEnabled = false,
            dailyBriefingNotificationsEnabled = false
        )

        val json = org.json.JSONObject().apply {
            put("notificationsEnabled", custom.notificationsEnabled)
            put("reminderNotificationsEnabled", custom.reminderNotificationsEnabled)
            put("proactiveAlertsEnabled", custom.proactiveAlertsEnabled)
            put("dailyBriefingNotificationsEnabled", custom.dailyBriefingNotificationsEnabled)
        }

        val d = AppSettings()
        val restored = AppSettings(
            notificationsEnabled = json.optBoolean("notificationsEnabled", d.notificationsEnabled),
            reminderNotificationsEnabled = json.optBoolean("reminderNotificationsEnabled", d.reminderNotificationsEnabled),
            proactiveAlertsEnabled = json.optBoolean("proactiveAlertsEnabled", d.proactiveAlertsEnabled),
            dailyBriefingNotificationsEnabled = json.optBoolean("dailyBriefingNotificationsEnabled", d.dailyBriefingNotificationsEnabled)
        )

        assertFalse(restored.notificationsEnabled)
        assertFalse(restored.reminderNotificationsEnabled)
        assertFalse(restored.proactiveAlertsEnabled)
        assertFalse(restored.dailyBriefingNotificationsEnabled)
    }
}
