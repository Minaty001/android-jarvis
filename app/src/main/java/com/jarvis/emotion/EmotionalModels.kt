package com.jarvis.emotion

import java.util.UUID

enum class AssistantState {
    HAPPY,
    SAD,
    KIDDING,
    PANIC,
    THINKING,
    FOCUSED
}

enum class TaskPhase {
    IDLE,
    PROCESSING,
    WAITING_FOR_TOOL,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

enum class Sentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    UNKNOWN
}

enum class SignalSource {
    USER_EXPLICIT,
    NLU,
    TASK_MANAGER,
    SYSTEM_HEALTH,
    USER_PREFERENCE
}

data class StateDetail(
    val state: AssistantState,
    val previousState: AssistantState,
    val reason: String,
    val confidence: Float = 1.0f,
    val priority: Int = 10,
    val enteredAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long? = null,
    val operationId: String? = null
)

data class ContextDetail(
    val taskPhase: TaskPhase = TaskPhase.IDLE,
    val sentiment: Sentiment = Sentiment.UNKNOWN,
    val sentimentConfidence: Float = 0.0f,
    val source: SignalSource = SignalSource.NLU
)

data class PresentationDetail(
    val voiceProfile: String = "FOCUSED",
    val uiProfile: String = "FOCUSED",
    val humorAllowed: Boolean = true,
    val speechInterruptible: Boolean = true
)

data class EmotionalSnapshot(
    val schemaVersion: Int = 1,
    val sessionId: String = "session_${System.currentTimeMillis()}",
    val version: Int = 1,
    val current: StateDetail,
    val context: ContextDetail = ContextDetail(),
    val presentation: PresentationDetail = PresentationDetail()
) {
    companion object {
        fun defaultForState(
            state: AssistantState,
            sessionId: String = "session_${System.currentTimeMillis()}",
            previousState: AssistantState = AssistantState.FOCUSED,
            operationId: String? = null
        ): EmotionalSnapshot {
            val (priority, reason) = when (state) {
                AssistantState.PANIC -> 100 to "validated_critical_risk"
                AssistantState.THINKING -> 80 to "task_processing"
                AssistantState.SAD -> 60 to "support_context"
                AssistantState.HAPPY -> 50 to "task_verified_success"
                AssistantState.KIDDING -> 40 to "playful_humor"
                AssistantState.FOCUSED -> 10 to "default_focused"
            }
            val voiceProfile = VoiceProfile.forState(state)
            return EmotionalSnapshot(
                schemaVersion = 1,
                sessionId = sessionId,
                version = 1,
                current = StateDetail(
                    state = state,
                    previousState = previousState,
                    reason = reason,
                    confidence = 1.0f,
                    priority = priority,
                    enteredAtMs = System.currentTimeMillis(),
                    expiresAtMs = null,
                    operationId = operationId
                ),
                context = ContextDetail(
                    taskPhase = if (state == AssistantState.THINKING) TaskPhase.PROCESSING else TaskPhase.IDLE,
                    source = SignalSource.NLU
                ),
                presentation = PresentationDetail(
                    voiceProfile = state.name,
                    uiProfile = state.name,
                    humorAllowed = state != AssistantState.PANIC && state != AssistantState.SAD,
                    speechInterruptible = voiceProfile.interruptible
                )
            )
        }
    }
}

data class VoiceProfile(
    val state: AssistantState,
    val speechRate: Float,
    val pitch: Float,
    val interruptible: Boolean = true
) {
    companion object {
        val FOCUSED = VoiceProfile(AssistantState.FOCUSED, speechRate = 1.00f, pitch = 1.00f, interruptible = true)
        val HAPPY = VoiceProfile(AssistantState.HAPPY, speechRate = 1.04f, pitch = 1.03f, interruptible = true)
        val SAD = VoiceProfile(AssistantState.SAD, speechRate = 0.94f, pitch = 0.97f, interruptible = true)
        val KIDDING = VoiceProfile(AssistantState.KIDDING, speechRate = 1.02f, pitch = 1.02f, interruptible = true)
        val PANIC = VoiceProfile(AssistantState.PANIC, speechRate = 1.04f, pitch = 1.01f, interruptible = false)
        val THINKING = VoiceProfile(AssistantState.THINKING, speechRate = 0.98f, pitch = 1.00f, interruptible = true)

        fun forState(state: AssistantState): VoiceProfile = when (state) {
            AssistantState.HAPPY -> HAPPY
            AssistantState.SAD -> SAD
            AssistantState.KIDDING -> KIDDING
            AssistantState.PANIC -> PANIC
            AssistantState.THINKING -> THINKING
            AssistantState.FOCUSED -> FOCUSED
        }
    }
}

data class UiEmotionProfile(
    val state: AssistantState,
    val primaryColor: Int,
    val secondaryColor: Int,
    val pulseDurationMs: Long,
    val accessibleLabel: String
) {
    companion object {
        val FOCUSED = UiEmotionProfile(AssistantState.FOCUSED, 0xFF3FD0FF.toInt(), 0xFF1FA670.toInt(), 2400L, "Jarvis: Focused and Ready")
        val HAPPY = UiEmotionProfile(AssistantState.HAPPY, 0xFF4EFAAE.toInt(), 0xFF2EB872.toInt(), 1400L, "Jarvis: Verified Success")
        val SAD = UiEmotionProfile(AssistantState.SAD, 0xFF7098B8.toInt(), 0xFF486581.toInt(), 3200L, "Jarvis: Supportive Empathy")
        val KIDDING = UiEmotionProfile(AssistantState.KIDDING, 0xFF9D7CFF.toInt(), 0xFF6549D5.toInt(), 1800L, "Jarvis: Playful")
        val PANIC = UiEmotionProfile(AssistantState.PANIC, 0xFFFF334B.toInt(), 0xFFD32F2F.toInt(), 600L, "Jarvis: Critical Emergency Alert")
        val THINKING = UiEmotionProfile(AssistantState.THINKING, 0xFFFFCF5C.toInt(), 0xFFF57F17.toInt(), 1200L, "Jarvis: Processing Request")

        fun forState(state: AssistantState): UiEmotionProfile = when (state) {
            AssistantState.HAPPY -> HAPPY
            AssistantState.SAD -> SAD
            AssistantState.KIDDING -> KIDDING
            AssistantState.PANIC -> PANIC
            AssistantState.THINKING -> THINKING
            AssistantState.FOCUSED -> FOCUSED
        }
    }
}

data class ResponseStyle(
    val state: AssistantState,
    val verbosity: String = "concise",
    val humorAllowed: Boolean = true,
    val empathyStyle: String = "restrained_professional",
    val urgency: String = "normal",
    val promptInstruction: String = "",
    val allowProgressMessage: Boolean = false,
    val interruptible: Boolean = true
)

data class EmotionalEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val operationId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sentiment: Sentiment = Sentiment.UNKNOWN,
    val sentimentConfidence: Float = 0.0f,
    val supportRecommended: Boolean = false,
    val humorRecommended: Boolean = false,
    val seriousContext: Boolean = false,
    val isCriticalRisk: Boolean = false,
    val isRiskResolved: Boolean = false,
    val taskPhase: TaskPhase = TaskPhase.IDLE,
    val verifiedSuccess: Boolean = false,
    val source: SignalSource = SignalSource.NLU,
    val reason: String = ""
)

data class EmotionalPreferences(
    val emotionalPresentationEnabled: Boolean = true,
    val humorEnabled: Boolean = true,
    val voiceExpressionEnabled: Boolean = true,
    val uiEmotionAnimationsEnabled: Boolean = true,
    val sentimentConfidenceThreshold: Float = 0.65f,
    val reducedMotionEnabled: Boolean = false
)
