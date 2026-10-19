package com.jarvis.emotion

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmotionalLifecycleControllerTest {

    @Test
    fun testInitialStateIsFocused() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val initial = controller.snapshot.value
        assertEquals(AssistantState.FOCUSED, initial.current.state)
        assertEquals(10, initial.current.priority)
    }

    @Test
    fun testDispatchEventUpdatesState() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.PROCESSING,
            source = SignalSource.TASK_MANAGER
        )
        val result = controller.dispatchEvent(event)
        assertEquals(AssistantState.THINKING, result.current.state)
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)
    }

    @Test
    fun testTransientStateExpiresToFocused() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        // Transition to HAPPY (4000ms expiry)
        val event = EmotionalEvent(
            operationId = "task_1",
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = true,
            source = SignalSource.TASK_MANAGER
        )
        controller.dispatchEvent(event)
        assertEquals(AssistantState.HAPPY, controller.snapshot.value.current.state)

        // Advance 2000ms -> Still HAPPY
        advanceTimeBy(2000L)
        runCurrent()
        assertEquals(AssistantState.HAPPY, controller.snapshot.value.current.state)

        // Advance past 4000ms -> Expired to FOCUSED
        advanceTimeBy(2100L)
        runCurrent()
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
    }

    @Test
    fun testTransientStateExpiresToThinkingIfActiveTaskExists() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        // Start task 1 -> THINKING
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = "task_bg",
                taskPhase = TaskPhase.PROCESSING,
                source = SignalSource.TASK_MANAGER
            )
        )
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        // Subtask or playful banter occurs -> KIDDING (4000ms expiry)
        controller.dispatchEvent(
            EmotionalEvent(
                humorRecommended = true,
                source = SignalSource.USER_EXPLICIT
            )
        )
        assertEquals(AssistantState.KIDDING, controller.snapshot.value.current.state)

        // Advance past 4000ms -> Expired back to active task THINKING
        advanceTimeBy(4100L)
        runCurrent()
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)
        assertEquals("task_bg", controller.snapshot.value.current.operationId)
    }

    @Test
    fun testStaleTimerDiscardedOnNewEvent() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        // 1. Enter HAPPY (expires in 4000ms)
        controller.dispatchEvent(
            EmotionalEvent(
                taskPhase = TaskPhase.SUCCEEDED,
                verifiedSuccess = true,
                source = SignalSource.TASK_MANAGER
            )
        )
        assertEquals(AssistantState.HAPPY, controller.snapshot.value.current.state)

        // 2. Advance 2000ms
        advanceTimeBy(2000L)
        runCurrent()

        // 3. New event occurs: Critical risk alert -> PANIC
        controller.dispatchEvent(
            EmotionalEvent(
                isCriticalRisk = true,
                source = SignalSource.SYSTEM_HEALTH,
                reason = "overheating"
            )
        )
        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)

        // 4. Advance past original 4000ms timer
        advanceTimeBy(3000L)
        runCurrent()

        // The stale timer must NOT have reverted PANIC back to FOCUSED!
        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)
    }

    @Test
    fun testPanicNeverExpires() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        controller.dispatchEvent(
            EmotionalEvent(
                isCriticalRisk = true,
                source = SignalSource.SYSTEM_HEALTH
            )
        )
        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)
        assertNull(controller.snapshot.value.current.expiresAtMs)

        // Advance a long duration
        advanceTimeBy(60000L)
        runCurrent()

        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)
    }

    @Test
    fun testResetToFocused() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = "task_x",
                taskPhase = TaskPhase.PROCESSING,
                source = SignalSource.TASK_MANAGER
            )
        )
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        controller.resetToFocused("user_dismissed")
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
        assertEquals("user_dismissed", controller.snapshot.value.current.reason)
    }
}
