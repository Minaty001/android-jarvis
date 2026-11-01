package com.jarvis.backend

import org.json.JSONObject

sealed class ServerEvent {
    data class Connected(val serverVersion: String, val activeModel: String) : ServerEvent()
    data class Token(val token: String) : ServerEvent()
    data class ActionRequest(
        val actionId: String,
        val action: String,
        val params: Map<String, String>,
        val speechPreamble: String? = null
    ) : ServerEvent()
    data class Completed(val spokenResponse: String, val success: Boolean) : ServerEvent()
    data class Pong(val timestamp: Long) : ServerEvent()
    data class Error(val message: String) : ServerEvent()
    data class Unknown(val raw: String) : ServerEvent()
}

object BackendParser {

    fun parseServerMessage(jsonString: String): ServerEvent {
        return try {
            val json = JSONObject(jsonString)
            when (json.optString("type")) {
                "connected" -> ServerEvent.Connected(
                    serverVersion = json.optString("server_version", "1.0"),
                    activeModel = json.optString("active_model", "default")
                )
                "token" -> ServerEvent.Token(
                    token = json.optString("token", "")
                )
                "action_request" -> {
                    val paramsMap = mutableMapOf<String, String>()
                    val paramsObj = json.optJSONObject("params")
                    if (paramsObj != null) {
                        val keys = paramsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            paramsMap[k] = paramsObj.optString(k, "")
                        }
                    }
                    ServerEvent.ActionRequest(
                        actionId = json.optString("action_id", ""),
                        action = json.optString("action", ""),
                        params = paramsMap,
                        speechPreamble = if (json.has("speech_preamble")) json.optString("speech_preamble") else null
                    )
                }
                "completed" -> ServerEvent.Completed(
                    spokenResponse = json.optString("spoken_response", ""),
                    success = json.optBoolean("success", true)
                )
                "pong" -> ServerEvent.Pong(
                    timestamp = json.optLong("timestamp", 0L)
                )
                "error" -> ServerEvent.Error(
                    message = json.optString("message", "Unknown backend error")
                )
                else -> ServerEvent.Unknown(jsonString)
            }
        } catch (e: Exception) {
            ServerEvent.Error(e.message ?: "Failed to parse JSON")
        }
    }

    fun buildQueryMessage(
        session_id: String,
        utterance: String,
        battery: Int = 100,
        isCharging: Boolean = false,
        wifiConnected: Boolean = true,
        apiKey: String? = null
    ): String {
        val root = JSONObject()
        root.put("type", "query")
        root.put("session_id", session_id)
        root.put("utterance", utterance)

        val state = JSONObject()
        state.put("battery", battery)
        state.put("is_charging", isCharging)
        state.put("wifi_connected", wifiConnected)
        if (!apiKey.isNullOrBlank()) {
            state.put("api_key", apiKey)
        }
        root.put("device_state", state)

        return root.toString()
    }

    fun buildActionResultMessage(
        actionId: String,
        success: Boolean,
        output: String,
        errorMessage: String? = null
    ): String {
        val root = JSONObject()
        root.put("type", "action_result")
        root.put("action_id", actionId)
        root.put("success", success)
        root.put("output", output)
        if (errorMessage != null) {
            root.put("error_message", errorMessage)
        }
        return root.toString()
    }

    fun buildPingMessage(timestamp: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("type", "ping")
        root.put("timestamp", timestamp)
        return root.toString()
    }
}
