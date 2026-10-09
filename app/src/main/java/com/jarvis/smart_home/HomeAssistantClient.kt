package com.jarvis.smart_home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class EntityState(val state: String, val attributes: Map<String, Any>)

class HomeAssistantClient(
    private val baseUrl: String = "http://homeassistant.local:8123",
    private val bearerToken: String = "" // Should be injected securely
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun callService(
        domain: String, 
        service: String, 
        entityId: String, 
        payload: Map<String, Any>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/services/$domain/$service"
            
            val jsonPayload = JSONObject()
            jsonPayload.put("entity_id", entityId)
            payload?.forEach { (k, v) -> jsonPayload.put(k, v) }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getEntityState(entityId: String): EntityState? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/states/$entityId"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string() ?: return@use null
                val json = JSONObject(bodyStr)
                
                val state = json.optString("state", "unknown")
                val attrObj = json.optJSONObject("attributes")
                val attributes = mutableMapOf<String, Any>()
                attrObj?.keys()?.forEach { key ->
                    attributes[key] = attrObj.get(key)
                }
                
                EntityState(state, attributes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
