package com.jarvis

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentModel
import com.jarvis.emergency.EmergencySosBeaconEngine
import com.jarvis.foundation.PolicyEngine
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.tools.KeyguardUnlockTool
import com.jarvis.tools.ScreenUnlockTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase6ReviewFixesTest {

    private val intentModel = IntentModel()
    private val policyEngine = PolicyEngine()

    @Before
    fun setUp() {
        KeyguardUnlockTool.resetRateLimit()
        EmergencySosBeaconEngine.stopBeacon()
    }

    @Test
    fun testEmergencySosExemptFromManualModeApproval() {
        // Manual mode: autonomousFullAuto is false
        policyEngine.setAutonomousMode(false)
        val metadata = ToolMetadata(
            name = "EMERGENCY_SOS",
            description = "Emergency SOS",
            parameters = emptyList(),
            riskLevel = RiskLevel.HIGH
        )
        val result = policyEngine.evaluate(metadata, emptyMap())
        assertTrue("Emergency SOS must be allowed", result.allowed)
        assertFalse("Emergency SOS must NEVER require confirmation/approval", result.requiresApproval)
    }

    @Test
    fun testIntentModelCapturesMalformedPinAndPunctuatedTriggers() {
        val wordPin = intentModel.classify("enter pin abc")
        assertNotNull(wordPin)
        assertEquals(AssistantIntent.SCREEN_UNLOCK, wordPin?.intent)
        assertEquals("abc", wordPin?.params?.get("pin"))

        val punctuatedSos = intentModel.classify("Send SOS!")
        assertNotNull(punctuatedSos)
        assertEquals(AssistantIntent.EMERGENCY_SOS, punctuatedSos?.intent)
        assertEquals("start", punctuatedSos?.params?.get("action"))

        val punctuatedCancel = intentModel.classify("Stop SOS!")
        assertNotNull(punctuatedCancel)
        assertEquals(AssistantIntent.EMERGENCY_SOS, punctuatedCancel?.intent)
        assertEquals("stop", punctuatedCancel?.params?.get("action"))
    }

    @Test
    fun testScreenUnlockToolDelegatesToKeyguardUnlockAndNeverLeaksPin() = runBlocking {
        val tool = ScreenUnlockTool(null)
        val res = tool.execute(mapOf("pin" to "9876"))
        assertTrue(res.success)
        assertTrue("Should contain masked PIN", res.message.contains("****"))
        assertFalse("Should not contain plaintext PIN", res.message.contains("9876"))
    }
}
