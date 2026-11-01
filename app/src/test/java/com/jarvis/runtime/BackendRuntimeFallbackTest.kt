package com.jarvis.runtime

import com.jarvis.ai.Message
import com.jarvis.backend.BackendLlmClient
import com.jarvis.backend.JarvisBackendClient
import com.jarvis.ui.model.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackendRuntimeFallbackTest {

    @Test
    fun testAppSettingsBackendDefaults() {
        val settings = AppSettings()
        assertFalse(settings.backendServerEnabled)
        assertTrue(settings.backendServerUrl.isNotBlank())
        assertTrue(settings.backendFallbackToDirect)
    }

    @Test
    fun testBackendLlmClientFailsGracefullyWhenDisconnected() = runBlocking {
        val client = JarvisBackendClient(serverUrl = "ws://127.0.0.1:9999/ws/jarvis")
        val llm = BackendLlmClient(client)

        assertFalse(client.isConnected)
        val result = llm.chat(listOf(Message("user", "Hello")))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not connected") == true)
    }
}
