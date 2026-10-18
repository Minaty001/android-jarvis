package com.jarvis.tools

import com.jarvis.foundation.LockScreenSecurityPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyguardUnlockTest {

    @Before
    fun setUp() {
        KeyguardUnlockTool.resetRateLimit()
    }

    @Test
    fun testValidatePin() {
        assertEquals("1234", KeyguardUnlockTool.validatePin("1234"))
        assertEquals("0000", KeyguardUnlockTool.validatePin(" 0000 "))
        assertEquals("987654", KeyguardUnlockTool.validatePin("987654"))
        assertEquals("12345678", KeyguardUnlockTool.validatePin("12345678"))

        assertNull(KeyguardUnlockTool.validatePin("12")) // Too short
        assertNull(KeyguardUnlockTool.validatePin("abcd")) // Non-numeric
        assertNull(KeyguardUnlockTool.validatePin("123456789")) // Too long (>8 digits)
        assertNull(KeyguardUnlockTool.validatePin(null))
        assertNull(KeyguardUnlockTool.validatePin(""))
    }

    @Test
    fun testRateLimitingEnforcesMaxThreeAttemptsPerWindow() {
        assertTrue(KeyguardUnlockTool.checkRateLimit())
        assertTrue(KeyguardUnlockTool.checkRateLimit())
        assertTrue(KeyguardUnlockTool.checkRateLimit())
        // 4th attempt within 60s should be blocked
        assertFalse(KeyguardUnlockTool.checkRateLimit())
    }

    @Test
    fun testPinMaskingInResultMessage() = runBlocking {
        val tool = KeyguardUnlockTool(null)
        val res = tool.execute(mapOf("pin" to "1234"))
        assertTrue(res.success)
        assertTrue("Message should contain masked pin", res.message.contains("****"))
        assertFalse("Message must never expose cleartext PIN", res.message.contains("1234"))
    }

    @Test
    fun testInvalidPinReturnsHelpfulFailureWithoutKeypadSpam() = runBlocking {
        val tool = KeyguardUnlockTool(null)
        val res = tool.execute(mapOf("pin" to "abc"))
        assertFalse(res.success)
        assertTrue(res.message.contains("Invalid PIN format"))
    }

    @Test
    fun testLockScreenSecurityPolicyAllowsKeyguardUnlock() {
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("KEYGUARD_UNLOCK", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SCREEN_UNLOCK", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SCREEN_LOCK", emptyMap()))
    }
}
