package com.jarvis.flight

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SimulatorBridge(
    private val bridgeUrl: String = "http://192.168.1.100:8080" // IP of the desktop running the simulator bridge
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun executeSimCommand(intent: ActionIntent): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = "$bridgeUrl/api/simconnect"
            
            val jsonPayload = JSONObject()
            jsonPayload.put("action", intent.action)
            jsonPayload.put("target", intent.target)
            intent.value?.let { jsonPayload.put("value", it) }

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    CommandResult.Success
                } else {
                    CommandResult.Failure("Simulator bridge error: ${response.code}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CommandResult.Failure("Failed to connect to simulator bridge: ${e.message}")
        }
    }
}
