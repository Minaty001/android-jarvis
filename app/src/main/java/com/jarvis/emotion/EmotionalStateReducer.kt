package com.jarvis.emotion

/**
 * Pure, deterministic reducer for the Jarvis Emotional Intelligence Engine.
 * Maintains single authoritative state transitions without side effects or Android framework dependencies.
 */
class EmotionalStateReducer {

    companion object {
        const val EXPIRY_HAPPY_MS = 4000L
        const val EXPIRY_SAD_MS = 6000L
        const val EXPIRY_KIDDING_MS = 4000L
    }

    /**
     * Pure transition function from (currentSnapshot, event, preferences) to nextSnapshot.
     */
    fun reduce(
        current: EmotionalSnapshot,
        event: EmotionalEvent,
        preferences: EmotionalPreferences = EmotionalPreferences()
    ): EmotionalSnapshot {
        val now = event.timestamp

        // 1. Guard against stale task events
        if (current.current.state == AssistantState.THINKING &&
            current.current.operationId != null &&
            event.operationId != null &&
            event.operationId != current.current.operationId &&
            event.source == SignalSource.TASK_MANAGER
        ) {
            // An event from an older or different operation cannot overwrite an active THINKING task
            return current
        }

        // 2. Strict Priority 100: Active Critical Risk (PANIC)
        // If an active hazard exists or is reported:
        if (event.isCriticalRisk || (current.current.state == AssistantState.PANIC && !event.isRiskResolved)) {
            val state = AssistantState.PANIC
            val reason = if (event.reason.isNotBlank()) event.reason else "validated_critical_risk"
            return createSnapshot(
                state = state,
                previousState = current.current.state,
                reason = reason,
                confidence = 1.0f,
                priority = 100,
                enteredAtMs = now,
                expiresAtMs = null,
                operationId = event.operationId ?: current.current.operationId,
                taskPhase = event.taskPhase,
                sentiment = event.sentiment,
                source = event.source,
                humorAllowed = false,
                speechInterruptible = false,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // If risk was resolved:
        if (current.current.state == AssistantState.PANIC && event.isRiskResolved) {
            return createSnapshot(
                state = AssistantState.FOCUSED,
                previousState = AssistantState.PANIC,
                reason = if (event.reason.isNotBlank()) event.reason else "risk_resolved",
                confidence = 1.0f,
                priority = 10,
                enteredAtMs = now,
                expiresAtMs = null,
                operationId = null,
                taskPhase = TaskPhase.IDLE,
                sentiment = Sentiment.NEUTRAL,
                source = event.source,
                humorAllowed = preferences.humorEnabled,
                speechInterruptible = true,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // 3. Priority 80: Active Task In Progress (THINKING)
        if (event.taskPhase == TaskPhase.PROCESSING || event.taskPhase == TaskPhase.WAITING_FOR_TOOL) {
            val state = AssistantState.THINKING
            val reason = if (event.reason.isNotBlank()) event.reason else "task_processing"
            return createSnapshot(
                state = state,
                previousState = current.current.state,
                reason = reason,
                confidence = 1.0f,
                priority = 80,
                enteredAtMs = now,
                expiresAtMs = null,
                operationId = event.operationId ?: current.current.operationId,
                taskPhase = event.taskPhase,
                sentiment = event.sentiment,
                source = event.source,
                humorAllowed = preferences.humorEnabled,
                speechInterruptible = true,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // If emotional presentation is disabled by user preference, skip transient affective states
        if (!preferences.emotionalPresentationEnabled) {
            val reason = if (event.reason.isNotBlank()) event.reason else "presentation_disabled_focused"
            return createSnapshot(
                state = AssistantState.FOCUSED,
                previousState = current.current.state,
                reason = reason,
                confidence = 1.0f,
                priority = 10,
                enteredAtMs = now,
                expiresAtMs = null,
                operationId = event.operationId,
                taskPhase = event.taskPhase,
                sentiment = event.sentiment,
                source = event.source,
                humorAllowed = false,
                speechInterruptible = true,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // 4. Priority 60: High-confidence Support / Empathy (SAD)
        val qualifiesForSad = (event.supportRecommended || event.source == SignalSource.USER_EXPLICIT && event.sentiment == Sentiment.NEGATIVE) &&
                event.sentimentConfidence >= preferences.sentimentConfidenceThreshold &&
                !event.seriousContext

        if (qualifiesForSad) {
            val state = AssistantState.SAD
            val reason = if (event.reason.isNotBlank()) event.reason else "support_context"
            return createSnapshot(
                state = state,
                previousState = current.current.state,
                reason = reason,
                confidence = event.sentimentConfidence,
                priority = 60,
                enteredAtMs = now,
                expiresAtMs = now + EXPIRY_SAD_MS,
                operationId = event.operationId,
                taskPhase = event.taskPhase,
                sentiment = event.sentiment,
                source = event.source,
                humorAllowed = false, // Never joke during support context
                speechInterruptible = true,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // 5. Priority 50: Verified Task Success (HAPPY)
        if (event.taskPhase == TaskPhase.SUCCEEDED && event.verifiedSuccess) {
            val state = AssistantState.HAPPY
            val reason = if (event.reason.isNotBlank()) event.reason else "task_verified_success"
            return createSnapshot(
                state = state,
                previousState = current.current.state,
                reason = reason,
                confidence = 1.0f,
                priority = 50,
                enteredAtMs = now,
                expiresAtMs = now + EXPIRY_HAPPY_MS,
                operationId = event.operationId,
                taskPhase = event.taskPhase,
                sentiment = Sentiment.POSITIVE,
                source = event.source,
                humorAllowed = preferences.humorEnabled,
                speechInterruptible = true,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // 6. Priority 40: Harmless Restrained Humor (KIDDING)
        val qualifiesForHumor = event.humorRecommended &&
                preferences.humorEnabled &&
                !event.seriousContext

        if (qualifiesForHumor) {
            val state = AssistantState.KIDDING
            val reason = if (event.reason.isNotBlank()) event.reason else "playful_humor"
            return createSnapshot(
                state = state,
                previousState = current.current.state,
                reason = reason,
                confidence = if (event.sentimentConfidence > 0) event.sentimentConfidence else 0.8f,
                priority = 40,
                enteredAtMs = now,
                expiresAtMs = now + EXPIRY_KIDDING_MS,
                operationId = event.operationId,
                taskPhase = event.taskPhase,
                sentiment = Sentiment.POSITIVE,
                source = event.source,
                humorAllowed = true,
                speechInterruptible = true,
                sessionId = current.sessionId,
                version = current.version + 1
            )
        }

        // 7. Priority 10: Default Baseline (FOCUSED)
        // Handles task failures, cancellations, neutral queries, and serious contexts suppressing humor
        val humorAllowed = preferences.humorEnabled && !event.seriousContext
        val reason = when {
            event.taskPhase == TaskPhase.FAILED -> if (event.reason.isNotBlank()) event.reason else "task_failed"
            event.taskPhase == TaskPhase.CANCELLED -> if (event.reason.isNotBlank()) event.reason else "task_cancelled"
            event.seriousContext -> "serious_context_suppression"
            event.reason.isNotBlank() -> event.reason
            else -> "default_focused"
        }

        return createSnapshot(
            state = AssistantState.FOCUSED,
            previousState = current.current.state,
            reason = reason,
            confidence = 1.0f,
            priority = 10,
            enteredAtMs = now,
            expiresAtMs = null,
            operationId = event.operationId,
            taskPhase = event.taskPhase,
            sentiment = event.sentiment,
            source = event.source,
            humorAllowed = humorAllowed,
            speechInterruptible = true,
            sessionId = current.sessionId,
            version = current.version + 1
        )
    }

    /**
     * Handles expiry of transient states (HAPPY, SAD, KIDDING).
     * Non-transient states (PANIC, THINKING, FOCUSED) never expire.
     */
    fun reduceExpiry(
        current: EmotionalSnapshot,
        hasActiveTask: Boolean = false,
        activeOperationId: String? = null
    ): EmotionalSnapshot {
        val currentState = current.current.state
        // PANIC, THINKING, and FOCUSED do not expire
        if (currentState == AssistantState.PANIC ||
            currentState == AssistantState.THINKING ||
            currentState == AssistantState.FOCUSED
        ) {
            return current
        }

        val targetState = if (hasActiveTask) AssistantState.THINKING else AssistantState.FOCUSED
        val targetPriority = if (hasActiveTask) 80 else 10
        val targetReason = if (hasActiveTask) "task_processing_resumed" else "transient_state_expired"

        return createSnapshot(
            state = targetState,
            previousState = currentState,
            reason = targetReason,
            confidence = 1.0f,
            priority = targetPriority,
            enteredAtMs = System.currentTimeMillis(),
            expiresAtMs = null,
            operationId = if (hasActiveTask) activeOperationId else null,
            taskPhase = if (hasActiveTask) TaskPhase.PROCESSING else TaskPhase.IDLE,
            sentiment = Sentiment.NEUTRAL,
            source = SignalSource.TASK_MANAGER,
            humorAllowed = true,
            speechInterruptible = true,
            sessionId = current.sessionId,
            version = current.version + 1
        )
    }

    private fun createSnapshot(
        state: AssistantState,
        previousState: AssistantState,
        reason: String,
        confidence: Float,
        priority: Int,
        enteredAtMs: Long,
        expiresAtMs: Long?,
        operationId: String?,
        taskPhase: TaskPhase,
        sentiment: Sentiment,
        source: SignalSource,
        humorAllowed: Boolean,
        speechInterruptible: Boolean,
        sessionId: String,
        version: Int
    ): EmotionalSnapshot {
        return EmotionalSnapshot(
            schemaVersion = 1,
            sessionId = sessionId,
            version = version,
            current = StateDetail(
                state = state,
                previousState = previousState,
                reason = reason,
                confidence = confidence,
                priority = priority,
                enteredAtMs = enteredAtMs,
                expiresAtMs = expiresAtMs,
                operationId = operationId
            ),
            context = ContextDetail(
                taskPhase = taskPhase,
                sentiment = sentiment,
                sentimentConfidence = confidence,
                source = source
            ),
            presentation = PresentationDetail(
                voiceProfile = state.name,
                uiProfile = state.name,
                humorAllowed = humorAllowed,
                speechInterruptible = speechInterruptible
            )
        )
    }
}
