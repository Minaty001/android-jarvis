package com.jarvis.cognitive

import com.jarvis.cognitive.capability.JciEvidenceLevel
import com.jarvis.cognitive.style.*
import com.jarvis.cognitive.verifier.JciVerificationResult
import com.jarvis.cognitive.verifier.JciVerificationStatus
import com.jarvis.emotion.AssistantState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JciStyleAndProactiveTest {

    private lateinit var styleFormatter: JciStyleFormatter
    private lateinit var proactiveEngine: JciProactiveEngine

    @Before
    fun setUp() {
        styleFormatter = JciStyleFormatter()
        proactiveEngine = JciProactiveEngine(
            config = CognitiveConfig(enabled = true, proactive = true)
        )
        proactiveEngine.resetRateLimit()
    }

    // 1. StyleFormatter Tests
    @Test
    fun testFormatterAppliesJarvisToneToSuccessfulOutcome() {
        val verification = JciVerificationResult(
            status = JciVerificationStatus.VERIFIED,
            attainedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            canReportDone = true,
            message = "Hardware torch enabled"
        )

        val response = styleFormatter.formatExecutionResponse("FLASHLIGHT", verification)
        assertTrue(response.isHonestConfirmation)
        assertEquals(AssistantState.HAPPY, response.targetEmotion)
        assertTrue(response.spokenText.contains("Flashlight activated, sir."))
    }

    @Test
    fun testFormatterNeverReportsSuccessWhenVerifierIsNotVerified() {
        // Failed outcome
        val failed = JciVerificationResult(
            status = JciVerificationStatus.REJECTED,
            attainedEvidenceLevel = JciEvidenceLevel.UNVERIFIABLE,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            canReportDone = false,
            message = "Connection timed out"
        )

        val failedResponse = styleFormatter.formatExecutionResponse("WEATHER", failed)
        assertFalse("Must not say completed or activated on failure", failedResponse.spokenText.contains("successfully"))
        assertTrue("Must report failure reason honestly", failedResponse.spokenText.contains("Connection timed out"))
        assertEquals(AssistantState.SAD, failedResponse.targetEmotion)

        // Unverified outcome
        val unverified = JciVerificationResult(
            status = JciVerificationStatus.UNVERIFIED,
            attainedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            canReportDone = false,
            message = "No sensor reading available"
        )

        val unverifiedResponse = styleFormatter.formatExecutionResponse("FLASHLIGHT", unverified)
        assertFalse("Must not say completed or activated", unverifiedResponse.spokenText.contains("activated"))
        assertTrue("Must indicate lack of independent verification", unverifiedResponse.spokenText.contains("could not independently verify"))
    }

    @Test
    fun testSubmissionAckReportsDispatchedNeverDone() {
        val ack = JciVerificationResult(
            status = JciVerificationStatus.SUBMITTED_ONLY,
            attainedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            canReportDone = false,
            message = "Close intent sent"
        )

        val response = styleFormatter.formatExecutionResponse("CLOSE_APP", ack)
        assertFalse("Must NOT claim done or closed", response.spokenText.contains("I closed"))
        assertFalse(response.spokenText.contains("Done"))
        assertTrue("Must honestly report dispatched", response.spokenText.contains("dispatched the close command"))
    }

    @Test
    fun testHumorAndJokeMapsToKiddingEmotion() {
        val jokeResponse = styleFormatter.formatDirectAnswer("Why did the computer show up at work naked? Because it was a bare-metal server.")
        assertEquals(AssistantState.KIDDING, jokeResponse.targetEmotion)
    }

    // 2. ProactiveEngine Tests
    @Test
    fun testProactiveEngineSuppressedWhenDisabled() {
        proactiveEngine.config = CognitiveConfig(enabled = true, proactive = false) // JCI_PROACTIVE=false

        val alert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.BatteryLow(percent = 10, isCharging = false),
            currentHourOverride = 14
        )
        assertNull("Alert must be suppressed when JCI_PROACTIVE is false", alert)
    }

    @Test
    fun testProactiveEngineTriggersOnLowBatteryWhenEnabled() {
        proactiveEngine.config = CognitiveConfig(enabled = true, proactive = true)

        val alert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.BatteryLow(percent = 12, isCharging = false),
            currentHourOverride = 14
        )
        assertNotNull("Low battery alert must fire during active hours", alert)
        assertTrue(alert!!.message.contains("battery level has dropped to 12%"))
        assertFalse(alert.isCritical)
    }

    @Test
    fun testProactiveEngineSuppressedDuringQuietHoursUnlessCritical() {
        proactiveEngine.config = CognitiveConfig(enabled = true, proactive = true)

        // 23:00 (11 PM) - Quiet hour
        val normalAlert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.BatteryLow(percent = 12, isCharging = false),
            currentHourOverride = 23
        )
        assertNull("Normal alerts must be suppressed during quiet hours", normalAlert)

        // Critical safety at 23:00 must BYPASS quiet hours
        val safetyAlert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.CriticalSafety("Smoke alarm sound detected in kitchen"),
            currentHourOverride = 23
        )
        assertNotNull("Critical safety alert must bypass quiet hours", safetyAlert)
        assertTrue(safetyAlert!!.isCritical)
        assertEquals(AssistantState.PANIC, safetyAlert.emotion)
    }

    @Test
    fun testProactiveRateLimitEnforcedToOnePerHour() {
        proactiveEngine.config = CognitiveConfig(enabled = true, proactive = true)
        val now = 1_000_000L

        // First alert succeeds
        val firstAlert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.SevereWeather("Flash flood warning"),
            currentHourOverride = 15,
            currentTimeMs = now
        )
        assertNotNull(firstAlert)

        // Second alert 10 minutes later is suppressed by 1-hour rate limit
        val secondAlert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.BatteryLow(percent = 10, isCharging = false),
            currentHourOverride = 15,
            currentTimeMs = now + 600_000L // 10 minutes later
        )
        assertNull("Second alert within 1 hour must be throttled", secondAlert)

        // Third alert 65 minutes later succeeds
        val thirdAlert = proactiveEngine.evaluateTrigger(
            event = ProactiveTriggerEvent.BatteryLow(percent = 10, isCharging = false),
            currentHourOverride = 16,
            currentTimeMs = now + 3900_000L // 65 minutes later
        )
        assertNotNull("Alert after 1 hour must succeed", thirdAlert)
    }
}
