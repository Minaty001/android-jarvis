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
        assertTrue(settings.backendServerEnabled)
        assertEquals("https://android-jarvis.onrender.com", settings.backendServerUrl)
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

    @Test
    fun testConversationalGreetingsFastPath() {
        // Greetings
        val resHello = com.jarvis.ai.IntentResolver.resolve("Hello")
        assertNotNull(resHello)
        assertEquals("Hello sir! How can I assist you today?", resHello?.directPlan?.response)
        assertEquals("SPEAK", resHello?.directPlan?.actions?.firstOrNull()?.type)

        val resHi = com.jarvis.ai.IntentResolver.resolve("hi jarvis")
        assertNotNull(resHi)
        assertEquals("SPEAK", resHi?.directPlan?.actions?.firstOrNull()?.type)

        // Status
        val resStatus = com.jarvis.ai.IntentResolver.resolve("how are you?")
        assertNotNull(resStatus)
        assertEquals("I am fully operational and ready to assist you, sir.", resStatus?.directPlan?.response)

        // Identity
        val resIdentity = com.jarvis.ai.IntentResolver.resolve("who are you")
        assertNotNull(resIdentity)
        assertEquals("I am J.A.R.V.I.S., your personal AI assistant.", resIdentity?.directPlan?.response)

        // Gratitude
        val resThanks = com.jarvis.ai.IntentResolver.resolve("thank you")
        assertNotNull(resThanks)
        assertEquals("You're welcome, sir. Always at your service.", resThanks?.directPlan?.response)
    }

    @Test
    fun testAgentKernelFallbackRulesGreetings() {
        val decision = com.jarvis.agent.AgentKernel.matchFallbackRules("hello")
        assertEquals(com.jarvis.agent.DecisionType.ACT, decision.type)
        assertEquals("SPEAK", decision.action?.type)
        assertEquals("Hello sir! How can I assist you today?", decision.action?.params?.get("text"))
    }
}
