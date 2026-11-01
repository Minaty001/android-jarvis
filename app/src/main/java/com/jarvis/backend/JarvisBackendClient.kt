package com.jarvis.backend

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JarvisBackendClient(
    serverUrl: String,
    private var authToken: String = "",
    private val deviceId: String = "android_jarvis_${(android.os.Build.MODEL ?: "device").replace(" ", "_")}",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var serverUrl: String = normalizeServerUrl(serverUrl)

    companion object {
        private const val TAG = "JarvisBackendClient"
        private const val PING_INTERVAL_MS = 15_000L

        /**
         * Normalizes any input URL (e.g. "https://android-jarvis.onrender.com",
         * "http://192.168.1.50:8000", "wss://...") into a valid WebSocket URL
         * ending with "/ws/jarvis".
         */
        fun normalizeServerUrl(rawUrl: String): String {
            var url = rawUrl.trim()
            if (url.isBlank()) return url

            if (url.startsWith("http://", ignoreCase = true)) {
                url = "ws://" + url.substring(7)
            } else if (url.startsWith("https://", ignoreCase = true)) {
                url = "wss://" + url.substring(8)
            } else if (!url.startsWith("ws://", ignoreCase = true) && !url.startsWith("wss://", ignoreCase = true)) {
                url = if (url.contains("onrender.com", ignoreCase = true) || url.startsWith("//")) {
                    "wss://${url.trimStart('/')}"
                } else {
                    "ws://$url"
                }
            }

            val base = if (url.contains("?")) url.substringBefore("?") else url
            val query = if (url.contains("?")) "?" + url.substringAfter("?") else ""

            val normalizedBase = if (!base.endsWith("/ws/jarvis")) {
                base.trimEnd('/') + "/ws/jarvis"
            } else {
                base
            }

            return normalizedBase + query
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive for WebSocket
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnectedFlag = AtomicBoolean(false)
    val isConnected: Boolean get() = isConnectedFlag.get()

    var onTokenReceived: ((String) -> Unit)? = null
    var onActionReceived: (suspend (ServerEvent.ActionRequest) -> Unit)? = null
    var onStateChanged: ((Boolean) -> Unit)? = null

    private var activeQueryCompleter: CompletableDeferred<String>? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private val sendMutex = Mutex()

    fun updateConfig(url: String, token: String) {
        val normalized = normalizeServerUrl(url)
        val changed = this.serverUrl != normalized || this.authToken != token
        this.serverUrl = normalized
        this.authToken = token
        if (changed && isConnected) {
            disconnect()
            connect()
        }
    }

    suspend fun autoPairIfAvailable(): String? = withContext(Dispatchers.IO) {
        val httpUrl = serverUrl.replace("wss://", "https://").replace("ws://", "http://").substringBefore("/ws")
        try {
            val req = Request.Builder().url("$httpUrl/api/pairing-info").build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = org.json.JSONObject(body)
                val pin = json.optString("pairing_pin", "")
                if (pin.isNotBlank()) {
                    authToken = pin
                    return@withContext pin
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun testConnection(timeoutMs: Long = 4000L): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isBlank()) return@withContext false
        if (authToken.isBlank()) {
            autoPairIfAvailable()
        }
        val completer = CompletableDeferred<Boolean>()
        val tempClient = JarvisBackendClient(
            serverUrl = normalized,
            authToken = authToken,
            deviceId = "test_${deviceId}"
        )
        tempClient.onStateChanged = { connected ->
            if (connected && !completer.isCompleted) {
                completer.complete(true)
            }
        }
        tempClient.connect()
        try {
            withTimeout(timeoutMs) {
                completer.await()
            }
        } catch (e: Exception) {
            false
        } finally {
            tempClient.disconnect()
        }
    }

    fun connect() {
        if (serverUrl.isBlank()) {
            Log.w(TAG, "Cannot connect: serverUrl is empty")
            return
        }

        disconnect()

        val fullUrl = buildString {
            append(serverUrl)
            val separator = if (serverUrl.contains("?")) "&" else "?"
            append("${separator}device_id=$deviceId")
            if (authToken.isNotBlank()) {
                append("&token=$authToken")
            }
        }

        Log.i(TAG, "Connecting to JARVIS backend server at $fullUrl")

        val request = Request.Builder()
            .url(fullUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connection opened successfully.")
                isConnectedFlag.set(true)
                onStateChanged?.invoke(true)
                startHeartbeat()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code / $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                onDisconnected()
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        onDisconnected()
    }

    private fun onDisconnected() {
        isConnectedFlag.set(false)
        onStateChanged?.invoke(false)
        activeQueryCompleter?.completeExceptionally(IllegalStateException("Backend disconnected"))
        activeQueryCompleter = null
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(3000L)
            if (!isConnected) {
                Log.i(TAG, "Attempting automatic reconnection to backend...")
                connect()
            }
        }
    }

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && isConnected) {
                delay(PING_INTERVAL_MS)
                sendRaw(BackendParser.buildPingMessage())
            }
        }
    }

    private suspend fun handleIncomingMessage(text: String) {
        val event = BackendParser.parseServerMessage(text)
        when (event) {
            is ServerEvent.Connected -> {
                Log.i(TAG, "Server Handshake OK. Version: ${event.serverVersion}, Model: ${event.activeModel}")
            }
            is ServerEvent.Token -> {
                onTokenReceived?.invoke(event.token)
            }
            is ServerEvent.ActionRequest -> {
                Log.i(TAG, "Server requested device action: ${event.action}")
                onActionReceived?.invoke(event)
            }
            is ServerEvent.Completed -> {
                Log.i(TAG, "Server query completed: '${event.spokenResponse}'")
                activeQueryCompleter?.complete(event.spokenResponse)
                activeQueryCompleter = null
            }
            is ServerEvent.Pong -> {
                // Heartbeat acknowledged
            }
            is ServerEvent.Error -> {
                Log.e(TAG, "Server reported error: ${event.message}")
                activeQueryCompleter?.completeExceptionally(IllegalStateException(event.message))
                activeQueryCompleter = null
            }
            is ServerEvent.Unknown -> {
                Log.d(TAG, "Unknown message received: ${event.raw}")
            }
        }
    }

    suspend fun sendQuery(
        utterance: String,
        sessionId: String = "session_${System.currentTimeMillis()}",
        battery: Int = 100,
        isCharging: Boolean = false,
        wifiConnected: Boolean = true,
        apiKey: String? = null,
        timeoutMs: Long = 20_000L
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConnected) {
            return@withContext Result.failure(IllegalStateException("Backend server is not connected"))
        }

        val completer = CompletableDeferred<String>()
        activeQueryCompleter = completer

        val payload = BackendParser.buildQueryMessage(
            session_id = sessionId,
            utterance = utterance,
            battery = battery,
            isCharging = isCharging,
            wifiConnected = wifiConnected,
            apiKey = apiKey
        )

        val sent = sendRaw(payload)
        if (!sent) {
            activeQueryCompleter = null
            return@withContext Result.failure(IllegalStateException("Failed to transmit query to backend"))
        }

        try {
            withTimeout(timeoutMs) {
                val response = completer.await()
                Result.success(response)
            }
        } catch (e: TimeoutCancellationException) {
            activeQueryCompleter = null
            Result.failure(IllegalStateException("Backend query timed out after ${timeoutMs}ms"))
        } catch (e: Exception) {
            activeQueryCompleter = null
            Result.failure(e)
        }
    }

    suspend fun sendActionResult(
        actionId: String,
        success: Boolean,
        output: String,
        errorMessage: String? = null
    ): Boolean {
        val payload = BackendParser.buildActionResultMessage(
            actionId = actionId,
            success = success,
            output = output,
            errorMessage = errorMessage
        )
        return sendRaw(payload)
    }

    private suspend fun sendRaw(json: String): Boolean = sendMutex.withLock {
        val ws = webSocket ?: return false
        return ws.send(json)
    }
}
