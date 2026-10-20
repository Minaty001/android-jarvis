package com.jarvis.cognitive

import com.jarvis.cognitive.capability.*
import com.jarvis.cognitive.policy.JciPolicyDecision
import com.jarvis.cognitive.policy.JciPolicyEngine
import com.jarvis.cognitive.verifier.JciVerificationStatus
import com.jarvis.cognitive.verifier.JciVerifier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JciSafetyFoundationTest {

    private lateinit var policyEngine: JciPolicyEngine
    private lateinit var verifier: JciVerifier

    @Before
    fun setUp() {
        policyEngine = JciPolicyEngine()
        policyEngine.resetRateLimits()
        verifier = JciVerifier()
    }

    // 1. Registry tests
    @Test
    fun testEveryRegisteredCapabilityHasValidConfiguration() {
        val allCaps = JciCapabilityRegistry.getAllCapabilities()
        assertTrue("Registry must have audited canonical capabilities", allCaps.size >= 40)

        for (cap in allCaps) {
            assertNotNull("Capability ID must not be null", cap.id)
            assertTrue("Capability ID must be uppercase", cap.id == cap.id.uppercase())
            assertTrue("Capability must have non-blank description", cap.description.isNotBlank())
            assertNotNull("Risk level must be specified", cap.riskLevel)
            assertNotNull("Classification must be specified", cap.classification)
            assertNotNull("Expected evidence level must be specified", cap.expectedEvidenceLevel)
        }
    }

    @Test
    fun testInventedCapabilityIsRejected() {
        assertFalse("Invented tool must not be registered", JciCapabilityRegistry.isRegistered("BAKE_BREAD_IN_MICROWAVE"))
        assertNull("Invented tool resolution must return null", JciCapabilityRegistry.getCapability("BAKE_BREAD_IN_MICROWAVE"))

        val decision = policyEngine.evaluate("INVENTED_HACK_TOOL")
        assertFalse("Policy must reject invented capability", decision.allowed)
        assertFalse("Invented capability cannot request confirmation", decision.requiresConfirmation)
        assertTrue("Reason must state unregistered", decision.reason.contains("not in the system registry"))
    }

    @Test
    fun testAliasResolution() {
        assertEquals("FLASHLIGHT", JciCapabilityRegistry.getCanonicalName("TORCH"))
        assertEquals("FLASHLIGHT", JciCapabilityRegistry.getCanonicalName("FLASH"))
        assertEquals("TELEPHONY_CONTROL", JciCapabilityRegistry.getCanonicalName("CALL"))
        assertEquals("TELEPHONY_CONTROL", JciCapabilityRegistry.getCanonicalName("SMS"))
        assertEquals("WHATSAPP", JciCapabilityRegistry.getCanonicalName("SEND_WHATSAPP"))
        assertEquals("SYSTEM_SWITCHBOARD", JciCapabilityRegistry.getCanonicalName("SET_RINGER"))
    }

    // 2. Policy Engine Risk Tests
    @Test
    fun testLowRiskActionsAutoApproved() {
        val flashlightDecision = policyEngine.evaluate("FLASHLIGHT")
        assertTrue("Low-risk action must be auto-approved", flashlightDecision.allowed)
        assertFalse("Low-risk action must not require confirmation", flashlightDecision.requiresConfirmation)

        val weatherDecision = policyEngine.evaluate("WEATHER")
        assertTrue("Weather must be auto-approved", weatherDecision.allowed)

        val calculateDecision = policyEngine.evaluate("CALCULATE", mapOf("expression" to "2 + 2"))
        assertTrue("Calculate must be auto-approved", calculateDecision.allowed)
    }

    @Test
    fun testHighRiskActionBlockedWithoutConfirmation() {
        val whatsappDecision = policyEngine.evaluate(
            toolName = "WHATSAPP",
            params = mapOf("recipient" to "John", "message" to "Hello"),
            userConfirmed = false
        )
        assertFalse("High-risk action must NOT be allowed without confirmation", whatsappDecision.allowed)
        assertTrue("High-risk action must require confirmation", whatsappDecision.requiresConfirmation)
        assertTrue("Reason must state confirmation required", whatsappDecision.reason.contains("requires explicit user confirmation"))

        // With user confirmation, high-risk is allowed
        val confirmedDecision = policyEngine.evaluate(
            toolName = "WHATSAPP",
            params = mapOf("recipient" to "John", "message" to "Hello"),
            userConfirmed = true
        )
        assertTrue("High-risk action must be allowed with confirmation", confirmedDecision.allowed)
    }

    @Test
    fun testCriticalRiskActionHardRefused() {
        val wipeDecision = policyEngine.evaluate("DEVICE_WIPE", userConfirmed = true)
        assertFalse("Critical action must be hard-refused even if user confirmed", wipeDecision.allowed)
        assertFalse("Critical action cannot ask for ordinary confirmation", wipeDecision.requiresConfirmation)
        assertTrue("Reason must state critical policy block", wipeDecision.reason.contains("Critical destructive action is refused"))

        val rootDecision = policyEngine.evaluate("ROOT_COMMAND", mapOf("cmd" to "rm -rf /"), userConfirmed = true)
        assertFalse("Root command must be hard refused", rootDecision.allowed)
    }

    @Test
    fun testEmergencySosIsExemptFromConfirmation() {
        val sosDecision = policyEngine.evaluate("EMERGENCY_SOS", mapOf("action" to "start"), userConfirmed = false)
        assertTrue("Emergency SOS must be auto-approved for life safety", sosDecision.allowed)
        assertFalse("Emergency SOS must not wait for confirmation", sosDecision.requiresConfirmation)
    }

    // 3. Argument Validation Tests
    @Test
    fun testMissingRequiredArgumentRejected() {
        val missingQuery = policyEngine.evaluate("SEARCH_WEB", emptyMap())
        assertFalse("Missing required parameter 'query' must be rejected", missingQuery.allowed)
        assertTrue("Reason must mention missing parameter", missingQuery.reason.contains("Missing required parameter 'query'"))
    }

    @Test
    fun testInvalidArgumentTypesRejected() {
        // Integer type check with depth
        val invalidInt = policyEngine.evaluate("RESEARCH_DEEP", mapOf("query" to "Kotlin", "depth" to "not_a_number"))
        assertFalse("Non-integer value must be rejected", invalidInt.allowed)
        assertTrue("Reason must state must be an integer", invalidInt.reason.contains("must be an integer"))

        // Float type check with value
        val invalidFloat = policyEngine.evaluate("UNIT_CONVERTER", mapOf("from" to "km", "to" to "miles", "value" to "invalid_float"))
        assertFalse("Non-float value must be rejected", invalidFloat.allowed)
    }

    @Test
    fun testArgumentNumericBoundsRejected() {
        val outOfBounds = policyEngine.evaluate("RESEARCH_DEEP", mapOf("query" to "Kotlin", "depth" to "10"))
        assertFalse("Out of bounds value (> 5) must be rejected", outOfBounds.allowed)
        assertTrue("Reason must mention bounds", outOfBounds.reason.contains("must be <="))
    }

    @Test
    fun testDisallowedValuesRejected() {
        val invalidAction = policyEngine.evaluate("FLASHLIGHT", mapOf("action" to "explode"))
        assertFalse("Value not in allowedValues must be rejected", invalidAction.allowed)
        assertTrue("Reason must state invalid value", invalidAction.reason.contains("Invalid value 'explode'"))
    }

    @Test
    fun testPathTraversalAndSensitivePathsRejected() {
        val traversal = policyEngine.evaluate("FILE_READ", mapOf("path" to "/sdcard/photos/../../etc/passwd"))
        assertFalse("Path traversal '..' must be rejected", traversal.allowed)
        assertTrue("Reason must mention traversal", traversal.reason.contains("Path traversal is strictly prohibited"))

        val systemPath = policyEngine.evaluate("FILE_READ", mapOf("path" to "/system/build.prop"))
        assertFalse("Protected path '/system' must be rejected", systemPath.allowed)
        assertTrue("Reason must mention protected system path", systemPath.reason.contains("protected system path"))
    }

    @Test
    fun testInvalidPhoneNumberRejected() {
        val invalidPhone = policyEngine.evaluate(
            toolName = "TELEPHONY_CONTROL",
            params = mapOf("action" to "call", "recipient" to "John", "phone_number" to "abc???123!"),
            userConfirmed = true
        )
        assertFalse("Malformed characters in phone number must be rejected", invalidPhone.allowed)
        assertTrue("Reason must mention phone format", invalidPhone.reason.contains("Invalid phone number format"))
    }

    // 4. Rate Limiting and Deduplication Tests
    @Test
    fun testRapidDuplicateInvocationThrottled() {
        val cap = JciCapabilityRegistry.requireCapability("FLASHLIGHT")
        val params = mapOf("action" to "on")

        val first = policyEngine.evaluateCapability(cap, params)
        assertTrue("First call must succeed", first.allowed)

        // Rapid second call with identical params immediately throttled
        val duplicate = policyEngine.evaluateCapability(cap, params)
        assertFalse("Rapid duplicate must be throttled", duplicate.allowed)
        assertTrue("Reason must mention duplicate", duplicate.reason.contains("Duplicate invocation"))
    }

    @Test
    fun testRateLimitEnforced() {
        val cap = JciCapabilityRegistry.requireCapability("FILE_DELETE") // rate limit 5/min
        val params = mapOf("path" to "/sdcard/test.tmp")

        // First 5 unique invocations within minute
        for (i in 1..5) {
            val decision = policyEngine.evaluateCapability(
                cap,
                mapOf("path" to "/sdcard/test_$i.tmp"),
                userConfirmed = true
            )
            assertTrue("Invocation $i within rate limit must pass", decision.allowed)
        }

        // 6th invocation must exceed limit
        val exceeded = policyEngine.evaluateCapability(
            cap,
            mapOf("path" to "/sdcard/test_6.tmp"),
            userConfirmed = true
        )
        assertFalse("6th invocation must be blocked by rate limit", exceeded.allowed)
        assertTrue("Reason must mention rate limit", exceeded.reason.contains("Rate limit exceeded"))
    }

    // 5. Verifier Tests (Strict Evidence Levels)
    @Test
    fun testToolFailureImmediatelyRejected() {
        val cap = JciCapabilityRegistry.requireCapability("FLASHLIGHT")
        val result = verifier.verifyStep(
            capability = cap,
            toolSuccess = false,
            toolMessage = "Hardware camera error"
        )

        assertEquals(JciVerificationStatus.REJECTED, result.status)
        assertFalse("Tool failure can never report done", result.canReportDone)
        assertEquals(JciEvidenceLevel.UNVERIFIABLE, result.attainedEvidenceLevel)
    }

    @Test
    fun testStateCheckPassesWhenStateMatchesAndFailsWhenMissingOrMismatch() {
        val cap = JciCapabilityRegistry.requireCapability("FLASHLIGHT") // Expected: STATE_OBSERVED

        // 1. Missing observation -> cannot report done
        val missingObs = verifier.verifyStep(
            capability = cap,
            toolSuccess = true,
            observedState = null
        )
        assertEquals(JciVerificationStatus.UNVERIFIED, missingObs.status)
        assertFalse("Missing state observation cannot report done", missingObs.canReportDone)

        // 2. Mismatched observation -> rejected
        val mismatched = verifier.verifyStep(
            capability = cap,
            toolSuccess = true,
            observedState = mapOf("torch_on" to false),
            expectedStatePredicate = { it["torch_on"] == true }
        )
        assertEquals(JciVerificationStatus.REJECTED, mismatched.status)
        assertFalse("Mismatched state cannot report done", mismatched.canReportDone)

        // 3. Matched observation -> verified and can report done
        val verified = verifier.verifyStep(
            capability = cap,
            toolSuccess = true,
            observedState = mapOf("torch_on" to true),
            expectedStatePredicate = { it["torch_on"] == true }
        )
        assertEquals(JciVerificationStatus.VERIFIED, verified.status)
        assertTrue("Observed state match reports done", verified.canReportDone)
        assertEquals(JciEvidenceLevel.STATE_OBSERVED, verified.attainedEvidenceLevel)
    }

    @Test
    fun testSubmissionAckCannotReportDone() {
        val closeApp = JciCapabilityRegistry.requireCapability("CLOSE_APP") // Expected: SUBMISSION_ACK
        val result = verifier.verifyStep(
            capability = closeApp,
            toolSuccess = true
        )

        assertEquals(JciVerificationStatus.SUBMITTED_ONLY, result.status)
        assertEquals(JciEvidenceLevel.SUBMISSION_ACK, result.attainedEvidenceLevel)
        assertFalse("Level 4 submission ack CANNOT report done", result.canReportDone)
        assertTrue("Message must indicate dispatched", result.message.contains("Outcome cannot be independently verified"))
    }

    @Test
    fun testUserConfirmationVerification() {
        val whatsapp = JciCapabilityRegistry.requireCapability("WHATSAPP") // Expected: USER_CONFIRMED

        // Unconfirmed -> cannot report done
        val unconfirmed = verifier.verifyStep(
            capability = whatsapp,
            toolSuccess = true,
            userConfirmed = false
        )
        assertEquals(JciVerificationStatus.UNVERIFIED, unconfirmed.status)
        assertFalse("Unconfirmed high-risk action cannot report done", unconfirmed.canReportDone)

        // Confirmed -> verified
        val confirmed = verifier.verifyStep(
            capability = whatsapp,
            toolSuccess = true,
            userConfirmed = true
        )
        assertEquals(JciVerificationStatus.VERIFIED, confirmed.status)
        assertTrue("Confirmed action can report done", confirmed.canReportDone)
        assertEquals(JciEvidenceLevel.USER_CONFIRMED, confirmed.attainedEvidenceLevel)
    }
}
