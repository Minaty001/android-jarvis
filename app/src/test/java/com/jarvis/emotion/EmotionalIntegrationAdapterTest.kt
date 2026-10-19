package com.jarvis.emotion

import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmotionalIntegrationAdapterTest {

    private lateinit var eventBus: JarvisEventBus
    private lateinit var controller: EmotionalLifecycleController
    private lateinit var adapter: EmotionalIntegrationAdapter

    @Before
    fun setUp() {
        eventBus = JarvisEventBus()
    }

    @Test
    fun testTaskLifecycleTransitions() = runTest {
        controller = EmotionalLifecycleController(scope = this)
        adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        // 1. Task started -> THINKING
        adapter.onTaskStarted("task_100", "Check battery level")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)
        assertEquals("task_100", controller.snapshot.value.current.operationId)

        // 2. Tool running -> remains THINKING
        adapter.onToolStarted("task_100", "BatteryTool")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        // 3. Task completed verified -> HAPPY
        adapter.onTaskCompleted("task_100", verified = true, message = "Battery at 85%")
        assertEquals(AssistantState.HAPPY, controller.snapshot.value.current.state)
    }

    @Test
    fun testTaskFailureTransitionsToFocusedNeverPanic() = runTest {
        controller = EmotionalLifecycleController(scope = this)
        adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        adapter.onTaskStarted("task_200", "Execute payment")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        adapter.onTaskFailed("task_200", "Payment gateway timeout")
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
        assertTrue(controller.snapshot.value.current.reason.contains("gateway timeout"))
    }

    @Test
    fun testEventBusCriticalSystemAlertTriggersPanic() = runTest {
        controller = EmotionalLifecycleController(scope = this)
        adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)
        val subscriptionJob = adapter.startEventBusSubscription(scope = this)

        eventBus.post(JarvisEvent.SystemAlert(level = "CRITICAL", message = "Device battery temperature critical: 60C"))
        advanceUntilIdle()

        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)
        assertTrue(controller.snapshot.value.current.reason.contains("critical: 60C"))

        // Recovery alert -> returns to FOCUSED
        eventBus.post(JarvisEvent.SystemAlert(level = "RESOLVED", message = "Device temperature normal"))
        advanceUntilIdle()

        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
        subscriptionJob.cancel()
    }

    @Test
    fun testSpeechInputSentimentSupportAndHumor() = runTest {
        controller = EmotionalLifecycleController(scope = this)
        adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        // Distress input -> SAD
        adapter.onUserInput("I'm feeling really anxious and overwhelmed today")
        assertEquals(AssistantState.SAD, controller.snapshot.value.current.state)

        // Joke input -> KIDDING
        adapter.onUserInput("Jarvis, tell me a funny joke")
        assertEquals(AssistantState.KIDDING, controller.snapshot.value.current.state)
    }
}
