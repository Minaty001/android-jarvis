package com.jarvis.ai

import com.jarvis.agent.AgentAction
import com.jarvis.agent.AgentObservation
import com.jarvis.agent.AgentState
import com.jarvis.agent.ContextNeed
import com.jarvis.agent.GoalEvaluator
import com.jarvis.autonomous.AmbientContextEngine
import com.jarvis.autonomous.AmbientState
import com.jarvis.autonomous.ContextSignals
import com.jarvis.battery.DeepBatteryGovernor
import com.jarvis.cognitive.capability.JciCapability
import com.jarvis.cognitive.capability.JciRiskLevel
import com.jarvis.cognitive.capability.JciToolClassification
import com.jarvis.cognitive.policy.JciPolicyEngine
import com.jarvis.cognitive.policy.PolicyContext
import com.jarvis.control.DynamicAudioProfileEngine
import com.jarvis.control.DynamicAudioProfileEngine.AmbientAcousticLevel
import com.jarvis.control.DynamicAudioProfileEngine.AudioReasoningContext
import com.jarvis.execution.VerificationResult
import com.jarvis.execution.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmReasoningPhase2Test {

    private class MockLlm(private val response: String) : LlmClient {
        var callCount = 0
            private set
        override suspend fun chat(messages: List<Message>, onToken: (String) -> Unit): Result<String> {
            callCount++
            return Result.success(response)
        }
        override fun cancel() {}
    }

    // 2.1 JciPolicyEngine Contextual Risk Reasoning Tests
    @Test
    fun testPolicyEngineAdaptiveTrustHistoryAndNightHours() {
        val policy = JciPolicyEngine()
        val highRiskCap = JciCapability(
            id = "SEND_SMS",
            description = "Send SMS",
            classification = JciToolClassification.EXTERNAL_COMMUNICATION,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = com.jarvis.cognitive.capability.JciEvidenceLevel.SUBMISSION_ACK
        )

        // Standard unconfirmed high-risk -> requires confirmation
        val defaultDecision = policy.evaluateCapabilityWithReasoning(
            highRiskCap,
            userConfirmed = false,
            context = PolicyContext(hour = 14, userTrustScore = 0.5f, confirmedInvocationCount = 0)
        )
        assertTrue(defaultDecision.requiresConfirmation)

        // Trusted user history (confirmed >= 10 times, trustScore >= 0.9) -> approved
        val trustedDecision = policy.evaluateCapabilityWithReasoning(
            highRiskCap,
            userConfirmed = false,
            context = PolicyContext(hour = 14, userTrustScore = 0.95f, confirmedInvocationCount = 12)
        )
        assertTrue(trustedDecision.allowed)
        assertFalse(trustedDecision.requiresConfirmation)
        assertTrue(trustedDecision.reason.contains("trusted user history", ignoreCase = true))

        // Night hours (e.g. 2am) without owner voice verification -> requires confirmation / verification
        val nightDecision = policy.evaluateCapabilityWithReasoning(
            highRiskCap,
            userConfirmed = false,
            context = PolicyContext(hour = 2, userTrustScore = 0.95f, confirmedInvocationCount = 12, isOwnerVoiceVerified = false)
        )
        assertTrue(nightDecision.requiresConfirmation)
        assertTrue(nightDecision.reason.contains("night hours", ignoreCase = true))
    }

    // 2.2 GoalEvaluator Semantic Evaluation Tests
    @Test
    fun testGoalEvaluatorSemanticReasoningFallback() = runBlocking {
        val evaluator = GoalEvaluator()
        val mockLlm = MockLlm("""
            {
              "satisfied": true,
              "confidence": 0.90,
              "reason": "User asked for soothing background sounds and ambient ocean was started"
            }
        """.trimIndent())

        val state = AgentState(taskId = 1, goal = "put on some ocean background sound")
        state.lastAction = AgentAction("MUSIC_PLAY", mapOf("track" to "ocean waves"))
        val observation = AgentObservation("TOOL", true, "Started playing ocean waves")
        val verification = VerificationResult(
            status = VerificationStatus.VERIFIED,
            message = "Playback confirmed active",
            evidence = emptyMap()
        )

        val eval = evaluator.evaluateWithReasoning(
            goal = "put on some ocean background sound",
            state = state,
            observation = observation,
            verification = verification,
            llm = mockLlm
        )

        assertTrue(eval.satisfied)
        assertTrue(eval.confidence >= 0.85f)
        assertNotNull(eval.reason)
    }

    // 2.3 AgentContextRouter Intelligent Need Reasoning Tests
    @Test
    fun testAgentContextRouterIntelligentNeedReasoning() {
        val router = com.jarvis.agent.AgentContextRouter()
        
        // Pronoun reference -> CONVERSATION
        val pronounResult = router.routeNeedsWithReasoning("tell her that I am running late")
        assertTrue(pronounResult.needs.contains(ContextNeed.CONVERSATION))
        assertTrue(pronounResult.rationale.contains("pronoun", ignoreCase = true) || pronounResult.rationale.contains("context", ignoreCase = true))

        // Implicit memory reference
        val memoryResult = router.routeNeedsWithReasoning("play my usual morning playlist")
        assertTrue(memoryResult.needs.contains(ContextNeed.MEMORY))
        assertTrue(memoryResult.rationale.contains("memory", ignoreCase = true) || memoryResult.rationale.contains("preference", ignoreCase = true))
    }

    // 2.4 DynamicAudioProfileEngine Context-Aware Volume Tests
    @Test
    fun testDynamicAudioProfileContextReasoning() {
        val engine = DynamicAudioProfileEngine()

        // Night hours cap volume
        val nightReport = engine.evaluateAmbientProfileWithReasoning(
            ambientDecibels = 75.0, // loud
            context = AudioReasoningContext(hour = 23, isHeadsetConnected = false)
        )
        assertTrue(nightReport.recommendedVolumePercent <= 45)
        assertTrue(nightReport.rationale.contains("night", ignoreCase = true))

        // In-car driving boost
        val drivingReport = engine.evaluateAmbientProfileWithReasoning(
            ambientDecibels = 50.0,
            context = AudioReasoningContext(hour = 12, isHeadsetConnected = false, ambientState = "DRIVING")
        )
        assertTrue(drivingReport.recommendedVolumePercent >= 80)
        assertTrue(drivingReport.rationale.contains("driving", ignoreCase = true))

        // Headset reduction
        val headsetReport = engine.evaluateAmbientProfileWithReasoning(
            ambientDecibels = 50.0,
            context = AudioReasoningContext(hour = 12, isHeadsetConnected = true)
        )
        assertTrue(headsetReport.rationale.contains("headset", ignoreCase = true))
    }

    // 2.5 AmbientContextEngine Multi-Signal Inference Tests
    @Test
    fun testAmbientContextMultiSignalInference() {
        val engine = AmbientContextEngine()

        // Driving signal
        val drivingSnapshot = engine.inferCurrentContextWithReasoning(
            ContextSignals(isDrivingDetected = true)
        )
        assertEquals(AmbientState.DRIVING, drivingSnapshot.state)
        assertTrue(drivingSnapshot.reason.contains("driving", ignoreCase = true))

        // Workout signal
        val workoutSnapshot = engine.inferCurrentContextWithReasoning(
            ContextSignals(isWorkoutAppActive = true)
        )
        assertEquals(AmbientState.WORKOUT, workoutSnapshot.state)
        assertTrue(workoutSnapshot.reason.contains("workout", ignoreCase = true))

        // Focus signal
        val focusSnapshot = engine.inferCurrentContextWithReasoning(
            ContextSignals(isFocusAppActive = true)
        )
        assertEquals(AmbientState.FOCUS, focusSnapshot.state)
        assertTrue(focusSnapshot.reason.contains("focus", ignoreCase = true))
    }

    // 2.6 DeepBatteryGovernor Predictive Throttle Tests
    @Test
    fun testBatteryGovernorPredictiveDrainRateReasoning() {
        val governor = DeepBatteryGovernor()

        // High drain rate triggers predictive throttle
        val report = governor.evaluateGovernorStateWithReasoning(
            customPercentage = 25,
            customCharging = false,
            drainRateOverride = 30.0 // 30% per hour drain
        )
        assertNotNull(report.estimatedMinutesRemaining)
        assertTrue(report.estimatedMinutesRemaining!! <= 60)
        assertTrue(report.rationale.contains("drain", ignoreCase = true) || report.rationale.contains("projected", ignoreCase = true))
    }
}
