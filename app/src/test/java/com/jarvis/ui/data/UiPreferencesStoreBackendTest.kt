package com.jarvis.ui.data

import com.jarvis.ui.model.AppSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UiPreferencesStoreBackendTest {

    @Test
    fun testBackendSettingsSerialization() {
        val settings = AppSettings(
            backendServerEnabled = true,
            backendServerUrl = "ws://192.168.1.55:8000/ws/jarvis",
            backendAuthToken = "secret123",
            backendFallbackToDirect = true
        )

        val json = JSONObject()
        json.put("backendServerEnabled", settings.backendServerEnabled)
        json.put("backendServerUrl", settings.backendServerUrl)
        json.put("backendAuthToken", settings.backendAuthToken)
        json.put("backendFallbackToDirect", settings.backendFallbackToDirect)

        assertTrue(json.getBoolean("backendServerEnabled"))
        assertEquals("ws://192.168.1.55:8000/ws/jarvis", json.getString("backendServerUrl"))
        assertEquals("secret123", json.getString("backendAuthToken"))
        assertTrue(json.getBoolean("backendFallbackToDirect"))
    }

    @Test
    fun testDefaultSettingsHasRenderUrl() {
        val settings = AppSettings()
        assertTrue(settings.backendServerEnabled)
        assertEquals("https://android-jarvis.onrender.com", settings.backendServerUrl)
    }
}
