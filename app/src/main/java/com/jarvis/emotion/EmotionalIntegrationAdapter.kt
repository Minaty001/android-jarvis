package com.jarvis.emotion

import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Adapter integrating AssistantRuntime, AgentKernel task lifecycles, NLU sentiment analysis,
 * and JarvisEventBus system events with the EmotionalLifecycleController.
 */
class EmotionalIntegrationAdapter(
    val controller: EmotionalLifecycleController,
    val sentimentAnalyzer: DeterministicSentimentAnalyzer = DeterministicSentimentAnalyzer(),
    val policyEngine: ResponseStylePolicyEngine = ResponseStylePolicyEngine(),
    val eventBus: JarvisEventBus = JarvisEventBus.shared
) {

    /**
     * Dispatches task start event to enter THINKING state.
     */
    fun onTaskStarted(taskId: String, goal: String) {
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = taskId,
                taskPhase = TaskPhase.PROCESSING,
                source = SignalSource.TASK_MANAGER,
                reason = "task_started: $goal"
            )
        )
    }

    /**
     * Dispatches tool invocation event, maintaining THINKING state.
     */
    fun onToolStarted(taskId: String, toolName: String) {
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = taskId,
                taskPhase = TaskPhase.WAITING_FOR_TOOL,
                source = SignalSource.TASK_MANAGER,
                reason = "waiting_for_tool: $toolName"
            )
        )
    }

    /**
     * Dispatches task completion event. If verified == true, triggers HAPPY state;
     * otherwise returns to baseline FOCUSED.
     */
    fun onTaskCompleted(taskId: String, verified: Boolean, message: String) {
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = taskId,
                taskPhase = TaskPhase.SUCCEEDED,
                verifiedSuccess = verified,
                source = SignalSource.TASK_MANAGER,
                reason = message
            )
        )
    }

    /**
     * Dispatches task failure event. Strictly transitions to FOCUSED with diagnostic reason;
     * NEVER triggers PANIC unless an independent critical safety alert exists.
     */
    fun onTaskFailed(taskId: String, reason: String) {
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = taskId,
                taskPhase = TaskPhase.FAILED,
                verifiedSuccess = false,
                source = SignalSource.TASK_MANAGER,
                reason = reason
            )
        )
    }

    /**
     * Dispatches task cancellation event, returning to FOCUSED baseline.
     */
    fun onTaskCancelled(taskId: String, reason: String = "cancelled") {
        controller.dispatchEvent(
            EmotionalEvent(
                operationId = taskId,
                taskPhase = TaskPhase.CANCELLED,
                source = SignalSource.TASK_MANAGER,
                reason = reason
            )
        )
    }

    /**
     * Analyzes user speech transcript for sentiment, empathy requests, and humor triggers.
     */
    fun onUserInput(utterance: String) {
        val analysis = sentimentAnalyzer.analyze(utterance)
        controller.dispatchEvent(
            EmotionalEvent(
                sentiment = analysis.sentiment,
                sentimentConfidence = analysis.confidence,
                supportRecommended = analysis.supportRecommended,
                humorRecommended = analysis.humorRecommended,
                seriousContext = analysis.seriousContext,
                source = SignalSource.USER_EXPLICIT,
                reason = "user_input_sentiment"
            )
        )
    }

    /**
     * Subscribes to JarvisEventBus to observe system-level critical hazards and task completions.
     */
    fun startEventBusSubscription(scope: CoroutineScope): Job {
        return scope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is JarvisEvent.SystemAlert -> {
                        val level = event.level.trim().uppercase()
                        if (level == "CRITICAL" || level == "PANIC" || level == "EMERGENCY") {
                            controller.dispatchEvent(
                                EmotionalEvent(
                                    isCriticalRisk = true,
                                    source = SignalSource.SYSTEM_HEALTH,
                                    reason = event.message
                                )
                            )
                        } else if (level == "RESOLVED" || level == "NORMAL" || level == "OK") {
                            controller.dispatchEvent(
                                EmotionalEvent(
                                    isRiskResolved = true,
                                    source = SignalSource.SYSTEM_HEALTH,
                                    reason = event.message
                                )
                            )
                        }
                    }
                    is JarvisEvent.TaskCompleted -> {
                        onTaskCompleted(event.taskId, event.verified, event.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Resolves the current ResponseStyle for LLM prompt construction.
     */
    fun getActiveResponseStyle(): ResponseStyle {
        return policyEngine.resolveResponseStyle(controller.getCurrentSnapshot())
    }
}
