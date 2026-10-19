package com.jarvis.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmotionalStateReducerTest {

    private lateinit var reducer: EmotionalStateReducer
    private val defaultPrefs = EmotionalPreferences()

    @Before
    fun setUp() {
        reducer = EmotionalStateReducer()
    }

    @Test
    fun test01_initialStateIsFocused() {
        val initial = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        assertEquals(AssistantState.FOCUSED, initial.current.state)
        assertEquals(10, initial.current.priority)
    }

    @Test
    fun test02_taskStartTransitionsToThinking() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.PROCESSING,
            source = SignalSource.TASK_MANAGER,
            reason = "task_started"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.THINKING, next.current.state)
        assertEquals(80, next.current.priority)
        assertEquals("task_1", next.current.operationId)
        assertNull(next.current.expiresAtMs)
    }

    @Test
    fun test03_pendingToolExecutionRemainsThinking() {
        val current = EmotionalSnapshot.defaultForState(
            state = AssistantState.THINKING,
            operationId = "task_1"
        )
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.WAITING_FOR_TOOL,
            source = SignalSource.TASK_MANAGER,
            reason = "tool_running"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.THINKING, next.current.state)
        assertEquals("task_1", next.current.operationId)
    }

    @Test
    fun test04_verifiedSuccessTransitionsToHappy() {
        val current = EmotionalSnapshot.defaultForState(
            state = AssistantState.THINKING,
            operationId = "task_1"
        )
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = true,
            source = SignalSource.TASK_MANAGER,
            reason = "operation_verified_success"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.HAPPY, next.current.state)
        assertEquals(50, next.current.priority)
        assertNotNull(next.current.expiresAtMs)
    }

    @Test
    fun test05_unverifiedSuccessDoesNotTriggerHappy() {
        val current = EmotionalSnapshot.defaultForState(
            state = AssistantState.THINKING,
            operationId = "task_1"
        )
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = false, // Speculative/unverified
            source = SignalSource.TASK_MANAGER,
            reason = "unverified_dispatch"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.FOCUSED, next.current.state)
    }

    @Test
    fun test06_taskFailureTransitionsToFocusedNeverPanic() {
        val current = EmotionalSnapshot.defaultForState(
            state = AssistantState.THINKING,
            operationId = "task_1"
        )
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.FAILED,
            source = SignalSource.TASK_MANAGER,
            reason = "network_timeout"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.FOCUSED, next.current.state)
        assertTrue(next.current.reason.contains("failed") || next.current.reason.contains("timeout"))
    }

    @Test
    fun test07_taskCancellationTransitionsToFocusedNeverPanic() {
        val current = EmotionalSnapshot.defaultForState(
            state = AssistantState.THINKING,
            operationId = "task_1"
        )
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.CANCELLED,
            source = SignalSource.TASK_MANAGER,
            reason = "cancelled_by_user"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.FOCUSED, next.current.state)
    }

    @Test
    fun test08_explicitSupportRequestTransitionsToSad() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            sentiment = Sentiment.NEGATIVE,
            sentimentConfidence = 0.85f,
            supportRecommended = true,
            source = SignalSource.USER_EXPLICIT,
            reason = "explicit_support_request"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.SAD, next.current.state)
        assertEquals(60, next.current.priority)
        assertNotNull(next.current.expiresAtMs)
    }

    @Test
    fun test09_highConfidenceDistressSentimentTransitionsToSad() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            sentiment = Sentiment.NEGATIVE,
            sentimentConfidence = 0.80f,
            supportRecommended = true,
            source = SignalSource.NLU,
            reason = "distress_detected"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.SAD, next.current.state)
    }

    @Test
    fun test10_ambiguousOrLowConfidenceContextDoesNotForceSad() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            sentiment = Sentiment.NEGATIVE,
            sentimentConfidence = 0.50f, // Below 0.65 threshold
            supportRecommended = true,
            source = SignalSource.NLU,
            reason = "low_confidence_distress"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.FOCUSED, next.current.state)
    }

    @Test
    fun test11_explicitJokeRequestTransitionsToKidding() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            sentiment = Sentiment.POSITIVE,
            sentimentConfidence = 0.90f,
            humorRecommended = true,
            source = SignalSource.USER_EXPLICIT,
            reason = "tell_me_a_joke"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.KIDDING, next.current.state)
        assertEquals(40, next.current.priority)
        assertNotNull(next.current.expiresAtMs)
    }

    @Test
    fun test12_playfulContextTransitionsToKidding() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            sentiment = Sentiment.POSITIVE,
            sentimentConfidence = 0.75f,
            humorRecommended = true,
            source = SignalSource.NLU,
            reason = "playful_banter"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.KIDDING, next.current.state)
    }

    @Test
    fun test13_seriousContextBlocksKidding() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            humorRecommended = true,
            seriousContext = true,
            source = SignalSource.NLU,
            reason = "inappropriate_joke_attempt"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.FOCUSED, next.current.state)
        assertFalse(next.presentation.humorAllowed)
    }

    @Test
    fun test14_criticalSystemHealthAlertTransitionsToPanic() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            isCriticalRisk = true,
            source = SignalSource.SYSTEM_HEALTH,
            reason = "device_thermal_overheat_critical"
        )
        val next = reducer.reduce(current, event, defaultPrefs)
        assertEquals(AssistantState.PANIC, next.current.state)
        assertEquals(100, next.current.priority)
        assertNull(next.current.expiresAtMs)
        assertFalse(next.presentation.speechInterruptible)
    }

    @Test
    fun test15_unrelatedEventsCannotClearPanic() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.PANIC)
        val taskCompletedEvent = EmotionalEvent(
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = true,
            source = SignalSource.TASK_MANAGER,
            reason = "task_done"
        )
        val next = reducer.reduce(current, taskCompletedEvent, defaultPrefs)
        assertEquals(AssistantState.PANIC, next.current.state)
    }

    @Test
    fun test16_validatedHazardRecoveryClearsPanic() {
        val current = EmotionalSnapshot.defaultForState(AssistantState.PANIC)
        val recoveryEvent = EmotionalEvent(
            isRiskResolved = true,
            source = SignalSource.SYSTEM_HEALTH,
            reason = "thermal_normal_recovered"
        )
        val next = reducer.reduce(current, recoveryEvent, defaultPrefs)
        assertEquals(AssistantState.FOCUSED, next.current.state)
    }

    @Test
    fun test17_expiryReturnsToUnderlyingState() {
        // Expiry of transient state HAPPY when no active task -> FOCUSED
        val happySnapshot = EmotionalSnapshot.defaultForState(AssistantState.HAPPY)
        val expireEvent = EmotionalEvent(
            reason = "state_expired"
        )
        val expired = reducer.reduceExpiry(happySnapshot, hasActiveTask = false)
        assertEquals(AssistantState.FOCUSED, expired.current.state)

        // Expiry of transient state HAPPY when active task exists -> THINKING
        val expiredWithTask = reducer.reduceExpiry(happySnapshot, hasActiveTask = true, activeOperationId = "task_active")
        assertEquals(AssistantState.THINKING, expiredWithTask.current.state)
        assertEquals("task_active", expiredWithTask.current.operationId)
    }

    @Test
    fun test18_staleExpiryDoesNotAffectPanicOrThinking() {
        val panicSnapshot = EmotionalSnapshot.defaultForState(AssistantState.PANIC)
        val expiredPanic = reducer.reduceExpiry(panicSnapshot, hasActiveTask = false)
        // Panic cannot expire!
        assertEquals(AssistantState.PANIC, expiredPanic.current.state)

        val thinkingSnapshot = EmotionalSnapshot.defaultForState(AssistantState.THINKING)
        val expiredThinking = reducer.reduceExpiry(thinkingSnapshot, hasActiveTask = true)
        // Thinking cannot expire!
        assertEquals(AssistantState.THINKING, expiredThinking.current.state)
    }

    @Test
    fun test19_staleTaskCallbackRejection() {
        // If snapshot has active operation "task_2", an event for "task_1" must be rejected or not downgrade "task_2"
        val current = EmotionalSnapshot.defaultForState(
            state = AssistantState.THINKING,
            operationId = "task_2"
        )
        val staleEvent = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = true,
            source = SignalSource.TASK_MANAGER,
            reason = "stale_task_1_completion"
        )
        val next = reducer.reduce(current, staleEvent, defaultPrefs)
        // Must stay in task_2 THINKING
        assertEquals(AssistantState.THINKING, next.current.state)
        assertEquals("task_2", next.current.operationId)
    }

    @Test
    fun test20_preferencesOverrides() {
        val prefsNoHumor = defaultPrefs.copy(humorEnabled = false)
        val current = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val jokeEvent = EmotionalEvent(
            humorRecommended = true,
            source = SignalSource.USER_EXPLICIT,
            reason = "joke_request"
        )
        val next = reducer.reduce(current, jokeEvent, prefsNoHumor)
        assertEquals(AssistantState.FOCUSED, next.current.state)
        assertFalse(next.presentation.humorAllowed)

        val prefsDisabledPresentation = defaultPrefs.copy(emotionalPresentationEnabled = false)
        val happyEvent = EmotionalEvent(
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = true,
            source = SignalSource.TASK_MANAGER,
            reason = "verified_success"
        )
        val nextWithDisabled = reducer.reduce(current, happyEvent, prefsDisabledPresentation)
        assertEquals(AssistantState.FOCUSED, nextWithDisabled.current.state)
    }
}
