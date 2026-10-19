package com.jarvis.emotion

import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptanceScenariosTest {

    private lateinit var eventBus: JarvisEventBus
    private val policyEngine = ResponseStylePolicyEngine()
    private val voiceProfileAdapter = VoiceProfileAdapter()

    @Before
    fun setUp() {
        eventBus = JarvisEventBus()
    }

    /**
     * Scenario 1: Successful task completion flow
     * Idle -> Processing -> Verified Succeeded -> HAPPY (voice upbeat, glow green) -> Expiry back to FOCUSED
     */
    @Test
    fun scenario01_successfulTaskCompletionFlow() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        // 1. Initially FOCUSED
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)

        // 2. User commands task -> THINKING
        adapter.onTaskStarted("task_42", "Calculate route to Stark Tower")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        // 3. Task verifies success -> HAPPY
        adapter.onTaskCompleted("task_42", verified = true, message = "Route calculated via FDR Drive.")
        val happySnapshot = controller.snapshot.value
        assertEquals(AssistantState.HAPPY, happySnapshot.current.state)
        val style = policyEngine.resolveResponseStyle(happySnapshot)
        assertTrue(style.promptInstruction.contains("satisfaction") || style.promptInstruction.contains("verified"))

        // Voice profile upbeat
        val voice = VoiceProfile.forState(happySnapshot.current.state)
        assertEquals(1.04f, voice.speechRate, 0.001f)
        assertEquals(1.03f, voice.pitch, 0.001f)

        // 4. Expiry timer fires after 4000ms -> returns to FOCUSED
        advanceTimeBy(4100L)
        runCurrent()
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
    }

    /**
     * Scenario 2: Failed task diagnostic flow
     * Processing -> Failed -> FOCUSED with diagnostic explanation, NEVER PANIC
     */
    @Test
    fun scenario02_failedTaskDiagnosticFlow() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        adapter.onTaskStarted("task_99", "Connect to satellite uplink")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        // Failure occurs
        adapter.onTaskFailed("task_99", "Satellite handshake timed out after 3 retries.")
        val snapshot = controller.snapshot.value
        assertEquals(AssistantState.FOCUSED, snapshot.current.state)
        assertTrue(snapshot.current.reason.contains("timed out"))
    }

    /**
     * Scenario 3: Cancelled task flow
     * Processing -> Cancelled -> FOCUSED
     */
    @Test
    fun scenario03_cancelledTaskFlow() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        adapter.onTaskStarted("task_50", "Download offline map archive")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        adapter.onTaskCancelled("task_50", "User requested cancel")
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
    }

    /**
     * Scenario 4: Emotional distress support flow
     * Negative sentiment + support -> SAD -> Expiry to FOCUSED
     */
    @Test
    fun scenario04_emotionalDistressSupportFlow() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        adapter.onUserInput("I'm feeling completely overwhelmed and need help coping")
        val sadSnapshot = controller.snapshot.value
        assertEquals(AssistantState.SAD, sadSnapshot.current.state)
        assertFalse(sadSnapshot.presentation.humorAllowed)

        val voice = VoiceProfile.forState(sadSnapshot.current.state)
        assertEquals(0.94f, voice.speechRate, 0.001f)
        assertEquals(0.97f, voice.pitch, 0.001f)

        // 6000ms expiry
        advanceTimeBy(6100L)
        runCurrent()
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
    }

    /**
     * Scenario 5: Playful humor flow
     * Joke query -> KIDDING -> Expiry to FOCUSED
     */
    @Test
    fun scenario05_playfulHumorFlow() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        adapter.onUserInput("Jarvis, tell me a funny joke")
        val kiddingSnapshot = controller.snapshot.value
        assertEquals(AssistantState.KIDDING, kiddingSnapshot.current.state)
        assertTrue(kiddingSnapshot.presentation.humorAllowed)

        val style = policyEngine.resolveResponseStyle(kiddingSnapshot)
        assertTrue(style.promptInstruction.contains("dry") || style.promptInstruction.contains("humorous"))

        advanceTimeBy(4100L)
        runCurrent()
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
    }

    /**
     * Scenario 6: Urgent panic flow
     * Critical overheat alert -> PANIC -> Non-interruptible -> Recovery -> FOCUSED
     */
    @Test
    fun scenario06_urgentPanicFlow() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)
        val subJob = adapter.startEventBusSubscription(scope = this)

        eventBus.post(JarvisEvent.SystemAlert(level = "CRITICAL", message = "Device battery temperature critical: 62C"))
        advanceUntilIdle()

        val panicSnapshot = controller.snapshot.value
        assertEquals(AssistantState.PANIC, panicSnapshot.current.state)
        assertFalse(panicSnapshot.presentation.speechInterruptible)
        assertFalse(panicSnapshot.presentation.humorAllowed)

        // Validate recovery
        eventBus.post(JarvisEvent.SystemAlert(level = "RESOLVED", message = "Battery temperature returned to 34C"))
        advanceUntilIdle()

        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
        subJob.cancel()
    }

    /**
     * Scenario 7: Emergency priority override
     * Active THINKING task -> Emergency alert -> PANIC immediately supersedes
     */
    @Test
    fun scenario07_emergencyPriorityOverride() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)
        val subJob = adapter.startEventBusSubscription(scope = this)

        adapter.onTaskStarted("task_critical_override", "Compiling codebase")
        assertEquals(AssistantState.THINKING, controller.snapshot.value.current.state)

        eventBus.post(JarvisEvent.SystemAlert(level = "CRITICAL", message = "Hardware fault detected"))
        advanceUntilIdle()

        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)
        assertEquals(100, controller.snapshot.value.current.priority)
        subJob.cancel()
    }

    /**
     * Scenario 8: Humor suppression in crisis
     * Critical risk active -> Joke request is suppressed
     */
    @Test
    fun scenario08_humorSuppressionInCrisis() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)
        val subJob = adapter.startEventBusSubscription(scope = this)

        eventBus.post(JarvisEvent.SystemAlert(level = "CRITICAL", message = "Emergency SOS Active"))
        advanceUntilIdle()
        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)

        // User asks for a joke during active emergency
        adapter.onUserInput("Jarvis, tell me a joke")
        // Must stay in PANIC, humor disallowed
        assertEquals(AssistantState.PANIC, controller.snapshot.value.current.state)
        assertFalse(controller.snapshot.value.presentation.humorAllowed)
        subJob.cancel()
    }

    /**
     * Scenario 9: Ambiguous input safety
     * Tragic fictional query -> Low confidence, stays FOCUSED
     */
    @Test
    fun scenario09_ambiguousInputSafety() = runTest {
        val controller = EmotionalLifecycleController(scope = this)
        val adapter = EmotionalIntegrationAdapter(controller = controller, eventBus = eventBus)

        adapter.onUserInput("The protagonist in the novel died in a tragic battle")
        assertEquals(AssistantState.FOCUSED, controller.snapshot.value.current.state)
    }

    /**
     * Scenario 10: Reduced motion & accessible presentation
     * Accessible labels, reduced animation intensity
     */
    @Test
    fun scenario10_reducedMotionAndAccessibility() = runTest {
        for (state in AssistantState.values()) {
            val profile = UiEmotionProfile.forState(state)
            assertNotNull(profile.accessibleLabel)
            assertTrue(profile.accessibleLabel.startsWith("Jarvis:"))
        }

        val prefs = EmotionalPreferences(reducedMotionEnabled = true)
        val controller = EmotionalLifecycleController(scope = this, initialPreferences = prefs)
        assertTrue(controller.preferences.reducedMotionEnabled)
    }
}
