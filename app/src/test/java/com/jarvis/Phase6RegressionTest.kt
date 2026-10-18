package com.jarvis

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentModel
import com.jarvis.emergency.EmergencySosBeaconEngine
import com.jarvis.foundation.LockScreenSecurityPolicy
import com.jarvis.foundation.RiskLevel
import com.jarvis.tools.DisplayWakeEngine
import com.jarvis.tools.EmergencySosTool
import com.jarvis.tools.KeyguardUnlockTool
import com.jarvis.tools.ScreenLockTool
import com.jarvis.tools.ScreenUnlockTool
import com.jarvis.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 Regression Verification Suite
 *
 * Verifies the end-to-end integration and stability of:
 * 1. Hands-Free Display Wake & Lock with 10s auto-release WakeLock battery guard.
 * 2. Touchless Keyguard Dismissal & Voice PIN Entry with rate limiting and masked privacy.
 * 3. Emergency SOS Voice Beacon (Morse code strobe, siren, GPS coordinates with Maps link, emergency SMS & dialer).
 * 4. IntentModel fast-paths and bilingual triggers across English and Hindi.
 * 5. LockScreenSecurityPolicy authorizations on secure keyguards.
 */
class Phase6RegressionTest {

    private val intentModel = IntentModel()

    @Before
    fun setUp() {
        KeyguardUnlockTool.resetRateLimit()
        EmergencySosBeaconEngine.stopBeacon()
    }

    // ---------------------------------------------------------
    // 1. Hands-Free Display Wake & Sleep Engine
    // ---------------------------------------------------------

    @Test
    fun testDisplayWakeSafetyTimeoutClamping() {
        val defaultWake = DisplayWakeEngine.wakeDisplay(null)
        assertTrue(defaultWake.success)
        assertEquals("10000", defaultWake.data["timeout_ms"])

        val excessiveWake = DisplayWakeEngine.wakeDisplay(null, timeoutMs = 60000L)
        assertTrue(excessiveWake.success)
        assertEquals("10000", excessiveWake.data["timeout_ms"])

        val shortWake = DisplayWakeEngine.wakeDisplay(null, timeoutMs = 2000L)
        assertTrue(shortWake.success)
        assertEquals("2000", shortWake.data["timeout_ms"])
    }

    @Test
    fun testScreenLockAndUnlockToolsDelegation() = runBlocking {
        val lockTool = ScreenLockTool(null)
        val lockRes = lockTool.execute(emptyMap())
        assertTrue(lockRes.success)

        val unlockTool = ScreenUnlockTool(null)
        val wakeRes = unlockTool.execute(mapOf("action" to "wake"))
        assertTrue(wakeRes.success)
    }

    // ---------------------------------------------------------
    // 2. Touchless Keyguard Dismissal & Voice PIN Entry
    // ---------------------------------------------------------

    @Test
    fun testKeyguardVoicePinValidationRules() {
        assertEquals("1234", KeyguardUnlockTool.validatePin("1234"))
        assertEquals("7890", KeyguardUnlockTool.validatePin(" 7890 "))
        assertEquals("12345678", KeyguardUnlockTool.validatePin("12345678"))

        assertNull("PIN shorter than 4 digits must fail", KeyguardUnlockTool.validatePin("123"))
        assertNull("PIN longer than 8 digits must fail", KeyguardUnlockTool.validatePin("123456789"))
        assertNull("Alphanumeric string must fail", KeyguardUnlockTool.validatePin("abcd"))
        assertNull("Empty string must fail", KeyguardUnlockTool.validatePin(""))
        assertNull("Null input must fail", KeyguardUnlockTool.validatePin(null))
    }

    @Test
    fun testKeyguardRateLimitingAndPrivacyMasking() = runBlocking {
        val tool = KeyguardUnlockTool(null)

        // Attempt 1: Valid PIN
        val res1 = tool.execute(mapOf("pin" to "4321"))
        assertTrue(res1.success)
        assertTrue(res1.message.contains("****"))
        assertFalse(res1.message.contains("4321"))

        // Attempt 2: Touchless Swipe
        val res2 = tool.execute(emptyMap())
        assertTrue(res2.success)

        // Attempt 3: Valid PIN
        val res3 = tool.execute(mapOf("pin" to "9999"))
        assertTrue(res3.success)

        // Attempt 4: Rate limited!
        val res4 = tool.execute(mapOf("pin" to "1111"))
        assertFalse(res4.success)
        assertTrue(res4.message.contains("Too many unlock attempts"))
    }

    // ---------------------------------------------------------
    // 3. Emergency SOS Voice Beacon Engine
    // ---------------------------------------------------------

    @Test
    fun testMorseSosdistressPatternCompleteness() {
        val timings = EmergencySosBeaconEngine.MORSE_SOS_TIMINGS
        assertEquals(18, timings.size)

        // S: 3 dots (150ms on, 150ms off; letter pause 450ms)
        assertTrue(timings[0].first && timings[0].second == 150L)
        assertFalse(timings[1].first && timings[1].second == 150L)
        assertTrue(timings[2].first && timings[2].second == 150L)
        assertFalse(timings[3].first && timings[3].second == 150L)
        assertTrue(timings[4].first && timings[4].second == 150L)
        assertFalse(timings[5].first && timings[5].second == 450L)

        // O: 3 dashes (450ms on, 150ms off; letter pause 450ms)
        assertTrue(timings[6].first && timings[6].second == 450L)
        assertFalse(timings[7].first && timings[7].second == 150L)
        assertTrue(timings[8].first && timings[8].second == 450L)
        assertFalse(timings[9].first && timings[9].second == 150L)
        assertTrue(timings[10].first && timings[10].second == 450L)
        assertFalse(timings[11].first && timings[11].second == 450L)

        // S: 3 dots (150ms on, 150ms off; word pause 1000ms)
        assertTrue(timings[12].first && timings[12].second == 150L)
        assertFalse(timings[13].first && timings[13].second == 150L)
        assertTrue(timings[14].first && timings[14].second == 150L)
        assertFalse(timings[15].first && timings[15].second == 150L)
        assertTrue(timings[16].first && timings[16].second == 150L)
        assertFalse(timings[17].first && timings[17].second == 1000L)
    }

    @Test
    fun testEmergencySosMessageFormatting() {
        val withCoords = EmergencySosBeaconEngine.formatSosMessage(19.0760, 72.8777)
        assertTrue(withCoords.contains("https://maps.google.com/?q=19.076,72.8777"))

        val withoutCoords = EmergencySosBeaconEngine.formatSosMessage(null, null)
        assertTrue(withoutCoords.contains("Location unavailable (GPS offline)"))
    }

    @Test
    fun testEmergencySosToolLifecycle() = runBlocking {
        val tool = EmergencySosTool(null)

        val start = tool.execute(mapOf("action" to "start", "contact" to "112"))
        assertTrue(start.success)
        assertTrue(EmergencySosBeaconEngine.isBeaconActive())

        val status = tool.execute(mapOf("action" to "status"))
        assertTrue(status.success)
        assertEquals("true", status.data["active"])

        val stop = tool.execute(mapOf("action" to "stop"))
        assertTrue(stop.success)
        assertFalse(EmergencySosBeaconEngine.isBeaconActive())
    }

    // ---------------------------------------------------------
    // 4. LockScreenSecurityPolicy Authorizations
    // ---------------------------------------------------------

    @Test
    fun testLockScreenSecurityPolicyPhase6Authorizations() {
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("KEYGUARD_UNLOCK", emptyMap(), RiskLevel.MEDIUM))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SCREEN_UNLOCK", emptyMap(), RiskLevel.LOW))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SCREEN_LOCK", emptyMap(), RiskLevel.LOW))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("EMERGENCY_SOS", emptyMap(), RiskLevel.HIGH))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SOS", emptyMap(), RiskLevel.CRITICAL))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("STOP_SOS", emptyMap(), RiskLevel.HIGH))
    }

    // ---------------------------------------------------------
    // 5. Bilingual Voice Intent Fast-Paths
    // ---------------------------------------------------------

    @Test
    fun testBilingualEmergencySosFastPaths() {
        val englishTriggers = listOf("send sos", "emergency", "help me", "send emergency alert")
        for (q in englishTriggers) {
            val res = intentModel.classify(q)
            assertNotNull("Should resolve $q", res)
            assertEquals(AssistantIntent.EMERGENCY_SOS, res?.intent)
            assertEquals("start", res?.params?.get("action"))
        }

        val hindiTriggers = listOf("bachao", "madad karo", "bachao mujhe")
        for (q in hindiTriggers) {
            val res = intentModel.classify(q)
            assertNotNull("Should resolve $q", res)
            assertEquals(AssistantIntent.EMERGENCY_SOS, res?.intent)
            assertEquals("start", res?.params?.get("action"))
        }

        val cancelTriggers = listOf("stop sos", "cancel sos", "sos band karo")
        for (q in cancelTriggers) {
            val res = intentModel.classify(q)
            assertNotNull("Should resolve $q", res)
            assertEquals(AssistantIntent.EMERGENCY_SOS, res?.intent)
            assertEquals("stop", res?.params?.get("action"))
        }
    }

    @Test
    fun testDisplayWakeSleepAndPinFastPaths() {
        val wake = intentModel.classify("wake screen")
        assertEquals(AssistantIntent.SCREEN_UNLOCK, wake?.intent)
        assertEquals("wake", wake?.params?.get("action"))

        val lock = intentModel.classify("lock phone")
        assertEquals(AssistantIntent.SCREEN_LOCK, lock?.intent)
        assertEquals("lock", lock?.params?.get("action"))

        val pinUnlock = intentModel.classify("unlock phone with pin 5432")
        assertEquals(AssistantIntent.SCREEN_UNLOCK, pinUnlock?.intent)
        assertEquals("5432", pinUnlock?.params?.get("pin"))
    }

    @Test
    fun testToolRegistryPhase6Aliases() {
        assertEquals("EMERGENCY_SOS", ToolRegistry.aliases["SOS"])
        assertEquals("EMERGENCY_SOS", ToolRegistry.aliases["BEACON"])
        assertEquals("EMERGENCY_SOS", ToolRegistry.aliases["STOP_SOS"])
        assertEquals("KEYGUARD_UNLOCK", ToolRegistry.aliases["KEYGUARD_UNLOCK"])
        assertEquals("KEYGUARD_UNLOCK", ToolRegistry.aliases["DISMISS_KEYGUARD"])
    }
}
