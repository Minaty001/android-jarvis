package com.jarvis.emotion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Authoritative lifecycle controller for the Jarvis Emotional Intelligence Engine.
 * Exposes a hot StateFlow of EmotionalSnapshot, schedules coroutine-based expiry
 * for transient affective states, and rejects stale callbacks using monotonic generation counters.
 */
class EmotionalLifecycleController(
    private val scope: CoroutineScope,
    private val reducer: EmotionalStateReducer = EmotionalStateReducer(),
    initialPreferences: EmotionalPreferences = EmotionalPreferences()
) {

    private val _snapshot = MutableStateFlow(EmotionalSnapshot.defaultForState(AssistantState.FOCUSED))
    val snapshot: StateFlow<EmotionalSnapshot> = _snapshot.asStateFlow()

    @Volatile
    var preferences: EmotionalPreferences = initialPreferences
        private set

    private val generation = AtomicLong(0L)
    private var expiryJob: Job? = null

    @Volatile
    var activeTaskId: String? = null
        private set

    /**
     * Dispatches an emotional event into the engine, updating state synchronously
     * and scheduling or canceling expiry timers as required.
     */
    @Synchronized
    fun dispatchEvent(event: EmotionalEvent): EmotionalSnapshot {
        // Track active task lifecycle
        if (event.taskPhase == TaskPhase.PROCESSING || event.taskPhase == TaskPhase.WAITING_FOR_TOOL) {
            if (event.operationId != null) {
                activeTaskId = event.operationId
            }
        } else if (event.taskPhase in listOf(TaskPhase.SUCCEEDED, TaskPhase.FAILED, TaskPhase.CANCELLED)) {
            if (event.operationId == null || event.operationId == activeTaskId) {
                activeTaskId = null
            }
        }

        val currentSnapshot = _snapshot.value
        val nextSnapshot = reducer.reduce(currentSnapshot, event, preferences)
        val gen = generation.incrementAndGet()
        _snapshot.value = nextSnapshot

        expiryJob?.cancel()
        expiryJob = null

        val expiresAt = nextSnapshot.current.expiresAtMs
        if (expiresAt != null) {
            val durationMs = (expiresAt - nextSnapshot.current.enteredAtMs).coerceAtLeast(0L)
            expiryJob = scope.launch {
                delay(durationMs)
                handleExpiry(gen)
            }
        }

        return nextSnapshot
    }

    /**
     * Updates engine preferences dynamically.
     */
    @Synchronized
    fun updatePreferences(newPrefs: EmotionalPreferences) {
        preferences = newPrefs
    }

    /**
     * Returns the current emotional snapshot.
     */
    fun getCurrentSnapshot(): EmotionalSnapshot = _snapshot.value

    /**
     * Forces immediate reset to FOCUSED baseline, clearing active tasks and cancelling timers.
     */
    @Synchronized
    fun resetToFocused(reason: String = "manual_reset"): EmotionalSnapshot {
        expiryJob?.cancel()
        expiryJob = null
        activeTaskId = null
        val gen = generation.incrementAndGet()
        val focused = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val resetSnapshot = focused.copy(
            version = _snapshot.value.version + 1,
            current = focused.current.copy(
                reason = reason,
                previousState = _snapshot.value.current.state
            )
        )
        _snapshot.value = resetSnapshot
        return resetSnapshot
    }

    @Synchronized
    private fun handleExpiry(timerGen: Long) {
        if (generation.get() != timerGen) {
            // Stale callback: generation mismatch, safely discard
            return
        }

        val current = _snapshot.value
        val hasActive = activeTaskId != null
        val expired = reducer.reduceExpiry(current, hasActiveTask = hasActive, activeOperationId = activeTaskId)
        if (expired != current) {
            generation.incrementAndGet()
            _snapshot.value = expired
        }
    }
}
