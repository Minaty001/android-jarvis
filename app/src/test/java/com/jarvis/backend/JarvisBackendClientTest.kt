package com.jarvis.backend

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisBackendClientTest {

    @Test
    fun testParseServerConnectedMessage() {
        val json = """
            {
                "type": "connected",
                "server_version": "1.0",
                "active_model": "llama3-70b-8192"
            }
        """.trimIndent()

        val parsed = BackendParser.parseServerMessage(json)
        assertTrue(parsed is ServerEvent.Connected)
        val event = parsed as ServerEvent.Connected
        assertEquals("1.0", event.serverVersion)
        assertEquals("llama3-70b-8192", event.activeModel)
    }

    @Test
    fun testParseTokenStreamMessage() {
        val json = """
            {
                "type": "token",
                "token": "Hello "
            }
        """.trimIndent()

        val parsed = BackendParser.parseServerMessage(json)
        assertTrue(parsed is ServerEvent.Token)
        assertEquals("Hello ", (parsed as ServerEvent.Token).token)
    }

    @Test
    fun testParseActionRequestMessage() {
        val json = """
            {
                "type": "action_request",
                "action_id": "act_123",
                "action": "FLASHLIGHT",
                "params": {
                    "state": "ON"
                },
                "speech_preamble": "Turning flashlight on."
            }
        """.trimIndent()

        val parsed = BackendParser.parseServerMessage(json)
        assertTrue(parsed is ServerEvent.ActionRequest)
        val act = parsed as ServerEvent.ActionRequest
        assertEquals("act_123", act.actionId)
        assertEquals("FLASHLIGHT", act.action)
        assertEquals("ON", act.params["state"])
        assertEquals("Turning flashlight on.", act.speechPreamble)
    }

    @Test
    fun testBuildQueryMessagePayload() {
        val payload = BackendParser.buildQueryMessage(
            session_id = "test_sess",
            utterance = "Turn on flashlight",
            battery = 85,
            isCharging = false,
            wifiConnected = true
        )

        val json = JSONObject(payload)
        assertEquals("query", json.getString("type"))
        assertEquals("test_sess", json.getString("session_id"))
        assertEquals("Turn on flashlight", json.getString("utterance"))
        val state = json.getJSONObject("device_state")
        assertEquals(85, state.getInt("battery"))
        assertEquals(false, state.getBoolean("is_charging"))
        assertEquals(true, state.getBoolean("wifi_connected"))
    }

    @Test
    fun testBuildActionResultPayload() {
        val payload = BackendParser.buildActionResultMessage(
            actionId = "act_456",
            success = true,
            output = "Flashlight turned on"
        )

        val json = JSONObject(payload)
        assertEquals("action_result", json.getString("type"))
        assertEquals("act_456", json.getString("action_id"))
        assertEquals(true, json.getBoolean("success"))
        assertEquals("Flashlight turned on", json.getString("output"))
    }
}
