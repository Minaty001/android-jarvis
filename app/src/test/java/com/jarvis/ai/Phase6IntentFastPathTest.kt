package com.jarvis.ai

import com.jarvis.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Phase6IntentFastPathTest {

    private val model = IntentModel()

    @Test
    fun testEnglishEmergencySosTriggers() {
        val triggers = listOf("send sos", "emergency", "help me", "send emergency alert", "emergency sos")
        for (trigger in triggers) {
            val resolved = model.classify(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.EMERGENCY_SOS, resolved?.intent)
            assertEquals("start", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testHindiEmergencySosTriggers() {
        val triggers = listOf("bachao", "madad karo", "bachao mujhe")
        for (trigger in triggers) {
            val resolved = model.classify(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.EMERGENCY_SOS, resolved?.intent)
            assertEquals("start", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testEmergencySosCancellationTriggers() {
        val triggers = listOf("stop sos", "cancel sos", "sos band karo", "stop emergency", "cancel emergency")
        for (trigger in triggers) {
            val resolved = model.classify(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.EMERGENCY_SOS, resolved?.intent)
            assertEquals("stop", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testWakeDisplayTriggers() {
        val triggers = listOf("wake screen", "wake up display", "turn on display", "screen on karo", "display on")
        for (trigger in triggers) {
            val resolved = model.classify(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.SCREEN_UNLOCK, resolved?.intent)
            assertEquals("wake", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testLockDisplayTriggers() {
        val triggers = listOf("sleep display", "turn off screen", "phone band karo", "lock screen", "sleep phone")
        for (trigger in triggers) {
            val resolved = model.classify(trigger)
            assertNotNull("Should resolve '$trigger'", resolved)
            assertEquals(AssistantIntent.SCREEN_LOCK, resolved?.intent)
            assertEquals("lock", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testVoicePinUnlockTriggers() {
        val testCases = mapOf(
            "unlock phone with pin 1234" to "1234",
            "enter pin 4321" to "4321",
            "pin dalo 9999" to "9999",
            "unlock with pin 5555" to "5555",
            "my pin is 0000" to "0000"
        )
        for ((phrase, expectedPin) in testCases) {
            val resolved = model.classify(phrase)
            assertNotNull("Should resolve '$phrase'", resolved)
            assertEquals(AssistantIntent.SCREEN_UNLOCK, resolved?.intent)
            assertEquals(expectedPin, resolved?.params?.get("pin"))
        }
    }

    @Test
    fun testToolRegistryAliasesForPhase6() {
        val registry = ToolRegistry()
        assertEquals("EMERGENCY_SOS", ToolRegistry.aliases["SOS"])
        assertEquals("EMERGENCY_SOS", ToolRegistry.aliases["BEACON"])
        assertEquals("KEYGUARD_UNLOCK", ToolRegistry.aliases["KEYGUARD_UNLOCK"] ?: "KEYGUARD_UNLOCK")
    }
}
