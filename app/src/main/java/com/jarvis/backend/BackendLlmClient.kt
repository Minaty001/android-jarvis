package com.jarvis.backend

import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackendLlmClient(
    private val backendClient: JarvisBackendClient
) : LlmClient {

    companion object {
        private const val TAG = "BackendLlmClient"
    }

    override suspend fun chat(
        messages: List<Message>,
        onToken: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!backendClient.isConnected) {
            return@withContext Result.failure(IllegalStateException("Backend server is not connected"))
        }

        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content
            ?: messages.lastOrNull()?.content
            ?: return@withContext Result.failure(IllegalArgumentException("No user prompt found in messages"))

        // Register token forwarding
        val previousTokenCallback = backendClient.onTokenReceived
        backendClient.onTokenReceived = { token ->
            onToken(token)
            previousTokenCallback?.invoke(token)
        }

        try {
            val res = backendClient.sendQuery(utterance = lastUserMessage)
            res
        } finally {
            backendClient.onTokenReceived = previousTokenCallback
        }
    }

    override suspend fun chatVision(
        prompt: String,
        base64ImageUrl: String?,
        uiContext: String?,
        onToken: (String) -> Unit
    ): Result<String> {
        val composite = buildString {
            append(prompt)
            if (!uiContext.isNullOrBlank()) {
                append("\n\nUI Context:\n$uiContext")
            }
            if (!base64ImageUrl.isNullOrBlank()) {
                append("\n[Attached Image Data: ${base64ImageUrl.take(30)}...]")
            }
        }
        return chat(listOf(Message("user", composite)), onToken)
    }

    override fun cancel() {
        // No-op or disconnect
    }

    override fun close() {
        backendClient.disconnect()
    }
}
