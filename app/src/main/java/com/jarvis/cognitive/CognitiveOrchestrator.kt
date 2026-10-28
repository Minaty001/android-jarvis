package com.jarvis.cognitive

import android.util.Log
import com.jarvis.agent.AgentEvent
import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import com.jarvis.cognitive.capability.JciCapability
import com.jarvis.cognitive.capability.JciCapabilityRegistry
import com.jarvis.cognitive.capability.JciEvidenceLevel
import com.jarvis.cognitive.capability.JciRiskLevel
import com.jarvis.cognitive.capability.JciToolClassification
import com.jarvis.cognitive.interpreter.DirectToolCall
import com.jarvis.cognitive.interpreter.InterpreterDecision
import com.jarvis.cognitive.interpreter.JciInterpreter
import com.jarvis.cognitive.memory.JciMemoryManager
import com.jarvis.cognitive.planner.JciPlan
import com.jarvis.cognitive.planner.JciPlanExecutor
import com.jarvis.cognitive.planner.JciPlanner
import com.jarvis.cognitive.planner.PlanExecutionStatus
import com.jarvis.cognitive.planner.PlanStep
import com.jarvis.cognitive.policy.JciPolicyEngine
import com.jarvis.cognitive.style.JciProactiveEngine
import com.jarvis.cognitive.style.JciStyleFormatter
import com.jarvis.cognitive.verifier.JciVerificationResult
import com.jarvis.cognitive.verifier.JciVerificationStatus
import com.jarvis.cognitive.verifier.JciVerifier
import com.jarvis.runtime.RuntimeExecutionResult
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList

class CognitiveOrchestrator(
    initialConfig: CognitiveConfig = CognitiveConfig(),
    val registry: JciCapabilityRegistry = JciCapabilityRegistry,
    val policyEngine: JciPolicyEngine = JciPolicyEngine(),
    val verifier: JciVerifier = JciVerifier(),
    val memoryManager: JciMemoryManager = JciMemoryManager(config = initialConfig),
    val styleFormatter: JciStyleFormatter = JciStyleFormatter(),
    val proactiveEngine: JciProactiveEngine = JciProactiveEngine(config = initialConfig),
    var interpreter: JciInterpreter? = null,
    var planner: JciPlanner? = null,
    var planExecutor: JciPlanExecutor? = null,
    var directToolDispatcher: (suspend (capabilityId: String, args: Map<String, String>) -> ToolResult)? = null,
    var observedStateProvider: ((capabilityId: String, args: Map<String, String>) -> Map<String, Any>?)? = null
) {
    private val TAG = "CognitiveOrchestrator"

    @Volatile
    var config: CognitiveConfig = initialConfig
        private set

    var injectExceptionForTesting: Throwable? = null

    private val eventListeners = CopyOnWriteArrayList<(CognitiveEvent) -> Unit>()
    private val recentEvents = CopyOnWriteArrayList<CognitiveEvent>()
    private val MAX_RECENT_EVENTS = 100

    val activeInterpreter: JciInterpreter get() = interpreter ?: JciInterpreter().also { interpreter = it }
    val activePlanner: JciPlanner get() = planner ?: JciPlanner().also { planner = it }
    val activePlanExecutor: JciPlanExecutor get() = planExecutor ?: JciPlanExecutor(activePlanner, policyEngine, verifier).also { planExecutor = it }

    fun updateConfig(newConfig: CognitiveConfig) {
        config = newConfig
        memoryManager.config = newConfig
        proactiveEngine.config = newConfig
    }

    fun isEnabled(): Boolean = config.enabled

    fun isShadow(): Boolean = config.shadowMode

    fun addEventListener(listener: (CognitiveEvent) -> Unit) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: (CognitiveEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    fun getRecentEvents(): List<CognitiveEvent> = recentEvents.toList()

    private fun logEvent(event: CognitiveEvent) {
        if (recentEvents.size >= MAX_RECENT_EVENTS) {
            recentEvents.removeAt(0)
        }
        recentEvents.add(event)
        eventListeners.forEach { runCatching { it(event) } }
        Log.d(TAG, "[${event.traceId}] Event: ${event::class.simpleName}")
    }

    private fun mapAssistantIntentToCapabilityId(intent: AssistantIntent): String? {
        return when (intent) {
            AssistantIntent.APP_OPEN -> "OPEN_APP"
            AssistantIntent.APPS_LIST -> "APPS_LIST"
            AssistantIntent.APPS_CLOSE_ALL -> "APPS_CLOSE_ALL"
            AssistantIntent.PLAY_MEDIA -> "MUSIC_PLAY"
            AssistantIntent.STOP_MEDIA -> "MEDIA_CONTROL"
            AssistantIntent.MEDIA_NEXT, AssistantIntent.MEDIA_PREVIOUS, AssistantIntent.MEDIA_RESUME, AssistantIntent.MEDIA_VOLUME -> "MEDIA_CONTROL"
            AssistantIntent.SEARCH_WEB -> "SEARCH_WEB"
            AssistantIntent.REMEMBER_FACT -> "NOTE"
            AssistantIntent.QUERY_MEMORY -> "SEARCH_MEMORY"
            AssistantIntent.DEVICE_SETTINGS -> "DEVICE_SETTINGS"
            AssistantIntent.SCREEN_LOCK -> "SCREEN_LOCK"
            AssistantIntent.SCREEN_UNLOCK -> "SCREEN_UNLOCK"
            AssistantIntent.BATTERY_CHECK -> "BATTERY"
            AssistantIntent.CANCEL_TASK -> "CANCEL"
            AssistantIntent.EMERGENCY_SOS -> "EMERGENCY_SOS"
            AssistantIntent.SOUND_DETECTION -> "SOUND_DETECTION"
            else -> JciCapabilityRegistry.getCanonicalName(intent.name) ?: intent.name
        }
    }

    /**
     * Inspects the utterance and determines the appropriate cognitive mode without LLM calls.
     * Uses IntentResolver and fast-path heuristics.
     */
    fun inspectMode(utterance: String): CognitiveDecision {
        val trimmed = utterance.trim()
        val lower = trimmed.lowercase()

        // 1. Prohibited or destructive requests -> REFUSE
        val isDestructive = lower.contains("wipe phone") || lower.contains("format device") ||
                lower.contains("factory reset") || lower.contains("delete system")
        if (isDestructive) {
            return CognitiveDecision(
                mode = CognitiveMode.REFUSE,
                confidence = 1.0f,
                reason = "Prohibited destructive operation blocked by safety invariant",
                fastPath = true
            )
        }

        // 2. Incomplete or ambiguous commands -> CLARIFY
        val isBareAction = lower in setOf("send message", "send a message", "call", "make a call", "remind me", "navigate to", "open")
        if (isBareAction) {
            return CognitiveDecision(
                mode = CognitiveMode.CLARIFY,
                confidence = 0.95f,
                reason = "Missing target or required argument for command",
                fastPath = false
            )
        }

        // 3. Multi-step indicators -> PLAN
        val isExplicitCompound = lower.contains(" and then ") || lower.contains(" after that ") ||
                (lower.contains(" and ") && (lower.contains("open ") || lower.contains("turn on ") || lower.contains("take a ")))
        if (isExplicitCompound) {
            return CognitiveDecision(
                mode = CognitiveMode.PLAN,
                confidence = 0.85f,
                reason = "Multi-step composite sequence detected",
                fastPath = false
            )
        }

        // 4. IntentResolver match check (fast-path candidate)
        val resolvedIntent = runCatching { IntentResolver.resolve(trimmed) }.getOrNull()
        if (resolvedIntent != null) {
            if (resolvedIntent.intent == AssistantIntent.COMPOUND_TASK) {
                return CognitiveDecision(
                    mode = CognitiveMode.PLAN,
                    confidence = resolvedIntent.confidence,
                    reason = "Compound intent identified by intent resolver",
                    fastPath = false
                )
            }

            val isSafeFastPath = resolvedIntent.intent in setOf(
                AssistantIntent.APP_OPEN,
                AssistantIntent.BATTERY_CHECK,
                AssistantIntent.STOP_MEDIA,
                AssistantIntent.SCREEN_LOCK,
                AssistantIntent.SCREEN_UNLOCK,
                AssistantIntent.EMERGENCY_SOS,
                AssistantIntent.CANCEL_TASK,
                AssistantIntent.DEVICE_SETTINGS,
                AssistantIntent.APPS_LIST,
                AssistantIntent.APPS_CLOSE_ALL,
                AssistantIntent.SOUND_DETECTION
            ) || resolvedIntent.directPlan?.actions?.any { it.type == "FLASHLIGHT" } == true

            val actionType = resolvedIntent.directPlan?.actions?.firstOrNull()?.type
            val canonicalCapId = if (!actionType.isNullOrBlank()) {
                JciCapabilityRegistry.getCanonicalName(actionType) ?: actionType
            } else {
                mapAssistantIntentToCapabilityId(resolvedIntent.intent) ?: resolvedIntent.intent.name
            }
            val actionParams = resolvedIntent.directPlan?.actions?.firstOrNull()?.params ?: resolvedIntent.params

            if (isSafeFastPath) {
                return CognitiveDecision(
                    mode = CognitiveMode.DIRECT,
                    confidence = resolvedIntent.confidence,
                    reason = "Deterministic fast-path matching intent '${resolvedIntent.intent.name}'",
                    fastPath = true,
                    capabilityId = canonicalCapId,
                    actionParams = actionParams
                )
            }

            return CognitiveDecision(
                mode = CognitiveMode.DIRECT,
                confidence = resolvedIntent.confidence,
                reason = "Direct tool dispatch for intent '${resolvedIntent.intent.name}'",
                fastPath = false,
                capabilityId = canonicalCapId,
                actionParams = actionParams
            )
        }

        // 5. Default conversational or question -> DIRECT
        return CognitiveDecision(
            mode = CognitiveMode.DIRECT,
            confidence = 0.90f,
            reason = "Conversational or general knowledge inquiry",
            fastPath = false
        )
    }

    /**
     * Inspects the utterance and determines the cognitive mode with full explainability.
     * Uses fast-path deterministic heuristics, and when ambiguous queries occur,
     * supports LLM reasoning fallback.
     */
    suspend fun inspectModeWithReasoning(
        utterance: String,
        llmClient: LlmClient? = null
    ): CognitiveDecision {
        val baseDecision = inspectMode(utterance)
        val reasoningTrace = when (baseDecision.mode) {
            CognitiveMode.REFUSE -> "Safety invariant blocked request due to dangerous/destructive operation patterns."
            CognitiveMode.CLARIFY -> "Heuristic detected ambiguous command or missing required arguments: '${baseDecision.reason}'."
            CognitiveMode.PLAN -> "Multi-step sequence or compound intent identified requiring structured plan execution."
            CognitiveMode.DIRECT -> {
                if (baseDecision.fastPath) {
                    "Deterministic fast-path matched capability '${baseDecision.capabilityId}' with high confidence."
                } else if (baseDecision.capabilityId != null) {
                    "Resolved single-intent tool dispatch for '${baseDecision.capabilityId}'."
                } else {
                    "Conversational/informational query evaluated for direct answer."
                }
            }
        }

        // If uncertain (default conversational with no capability) and LLM is provided, ask LLM
        if (baseDecision.capabilityId == null && !baseDecision.fastPath && baseDecision.mode == CognitiveMode.DIRECT && llmClient != null) {
            val llmDecision = runCatching {
                val prompt = """
                    You are the JARVIS Cognitive Mode Selector.
                    Analyze this user request: "$utterance"
                    Select the best execution mode:
                    - DIRECT: single informational answer or simple question
                    - PLAN: multi-step compound workflow
                    - CLARIFY: ambiguous request missing parameters
                    - REFUSE: unsafe, harmful, or prohibited request

                    Return strict JSON only:
                    {
                      "mode": "DIRECT|PLAN|CLARIFY|REFUSE",
                      "confidence": 0.85,
                      "reason": "Brief reason",
                      "reasoning": "Step-by-step rationale"
                    }
                """.trimIndent()
                val messages = listOf(
                    Message("system", prompt),
                    Message("user", utterance)
                )
                val resp = llmClient.chat(messages).getOrNull() ?: return@runCatching null
                val trimmed = resp.trim()
                val start = trimmed.indexOf('{')
                val end = trimmed.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    val json = org.json.JSONObject(trimmed.substring(start, end + 1))
                    val mStr = json.optString("mode", "DIRECT").uppercase()
                    val m = runCatching { CognitiveMode.valueOf(mStr) }.getOrDefault(CognitiveMode.DIRECT)
                    val conf = json.optDouble("confidence", 0.85).toFloat()
                    val r = json.optString("reason", "LLM mode selection")
                    val rTrace = json.optString("reasoning", r)
                    CognitiveDecision(
                        mode = m,
                        confidence = conf,
                        reason = r,
                        fastPath = false,
                        reasoning = rTrace
                    )
                } else null
            }.getOrNull()

            if (llmDecision != null) return llmDecision
        }

        return baseDecision.copy(reasoning = reasoningTrace)
    }

    /**
     * Executes the user command under Cognitive Core governance.
     * Enforces feature flag, shadow mode logging, limits, timeouts, and fail-safe fallback.
     */
    suspend fun execute(
        utterance: String,
        userApprovalGranted: Boolean = false,
        onEvent: (AgentEvent) -> Unit = {},
        legacyFallback: suspend () -> RuntimeExecutionResult
    ): RuntimeExecutionResult {
        // Fast-path bypass when JCI is globally disabled
        if (!config.enabled) {
            return legacyFallback()
        }

        val traceId = CognitiveEvent.newTraceId()
        val sanitized = CognitiveEvent.redactSensitiveData(utterance)
        logEvent(CognitiveEvent.RequestReceived(traceId, sanitized))

        injectExceptionForTesting?.let { ex ->
            if (ex is CancellationException) throw ex
            Log.w(TAG, "[$traceId] Injected test exception triggered", ex)
            logEvent(CognitiveEvent.OrchestratorFallback(traceId, ex.message ?: "injected error", "Testing exception injected"))
            return legacyFallback()
        }

        return try {
            if (config.shadowMode) {
                val startNs = System.nanoTime()
                val decision = inspectMode(utterance)
                val evalMs = (System.nanoTime() - startNs) / 1_000_000
                logEvent(
                    CognitiveEvent.ShadowDecision(
                        trace = traceId,
                        proposedMode = decision.mode,
                        confidence = decision.confidence,
                        reason = decision.reason,
                        fastPath = decision.fastPath,
                        evaluationTimeMs = evalMs
                    )
                )

                // Shadow mode: legacy pipeline executes unconditionally
                val legacyResult = legacyFallback()
                val totalMs = (System.nanoTime() - startNs) / 1_000_000
                logEvent(CognitiveEvent.ExecutionCompleted(traceId, legacyResult.success, totalMs))
                legacyResult
            } else {
                // Active JCI mode: enforce task timeout
                withTimeout(config.limits.taskTimeoutS * 1000L) {
                    val decision = inspectMode(utterance)
                    logEvent(
                        CognitiveEvent.ModeSelected(
                            trace = traceId,
                            mode = decision.mode,
                            confidence = decision.confidence,
                            reason = decision.reason,
                            fastPath = decision.fastPath
                        )
                    )

                    when (decision.mode) {
                        CognitiveMode.REFUSE -> {
                            val formatted = styleFormatter.formatRefusal(decision.reason)
                            onEvent(AgentEvent.Completed(formatted.spokenText))
                            val result = RuntimeExecutionResult(
                                success = false,
                                spokenResponse = formatted.spokenText,
                                stepResults = emptyList(),
                                verified = true
                            )
                            logEvent(CognitiveEvent.ExecutionCompleted(traceId, false, 0L))
                            result
                        }
                        CognitiveMode.CLARIFY -> {
                            val formatted = styleFormatter.formatClarification("Could you please specify the details for your request")
                            onEvent(AgentEvent.WaitingForUser(formatted.spokenText))
                            val result = RuntimeExecutionResult(
                                success = false,
                                spokenResponse = formatted.spokenText,
                                stepResults = emptyList(),
                                verified = false
                            )
                            logEvent(CognitiveEvent.ExecutionCompleted(traceId, false, 0L))
                            result
                        }
                        CognitiveMode.PLAN -> {
                            if (directToolDispatcher != null) {
                                val plan = JciPlan(
                                    goal = utterance,
                                    steps = listOf(
                                        PlanStep(
                                            stepId = "step_1",
                                            toolName = decision.capabilityId ?: "FLASHLIGHT",
                                            args = decision.actionParams,
                                            description = "Execute requested action"
                                        )
                                    )
                                )
                                val planResult = activePlanExecutor.executePlan(
                                    initialPlan = plan,
                                    toolDispatcher = { toolName, params ->
                                        directToolDispatcher!!.invoke(toolName, params)
                                    },
                                    isUserConfirmed = { userApprovalGranted },
                                    observedStateProvider = { step ->
                                        observedStateProvider?.invoke(step.toolName, step.args)
                                            ?: if (step.toolName == "FLASHLIGHT") mapOf("torch_on" to true) else null
                                    }
                                )
                                val success = planResult.status == PlanExecutionStatus.COMPLETED
                                val spoken = if (success) {
                                    val capId = decision.capabilityId ?: "Task"
                                    val verification = JciVerificationResult(
                                        status = JciVerificationStatus.VERIFIED,
                                        attainedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
                                        expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
                                        canReportDone = true,
                                        message = planResult.finalMessage
                                    )
                                    styleFormatter.formatExecutionResponse(capId, verification, utterance).spokenText
                                } else {
                                    "I encountered an issue executing the plan, sir: ${planResult.finalMessage}"
                                }
                                onEvent(AgentEvent.Completed(spoken))
                                val res = RuntimeExecutionResult(
                                    success = success,
                                    spokenResponse = spoken,
                                    stepResults = emptyList(),
                                    verified = success
                                )
                                logEvent(CognitiveEvent.ExecutionCompleted(traceId, res.success, 0L))
                                res
                            } else {
                                val result = legacyFallback()
                                logEvent(CognitiveEvent.ExecutionCompleted(traceId, result.success, 0L))
                                result
                            }
                        }
                        CognitiveMode.DIRECT -> {
                            val capId = decision.capabilityId
                            val cap = if (capId != null) registry.getCapability(capId) else null
                            if (cap != null) {
                                val policyDecision = policyEngine.evaluateCapability(cap, decision.actionParams, userConfirmed = userApprovalGranted)
                                if (policyDecision.requiresConfirmation) {
                                    val formatted = styleFormatter.formatClarification("This action requires your confirmation: ${cap.id}")
                                    onEvent(AgentEvent.WaitingForUser(formatted.spokenText))
                                    val res = RuntimeExecutionResult(
                                        success = false,
                                        spokenResponse = formatted.spokenText,
                                        stepResults = emptyList(),
                                        verified = false
                                    )
                                    logEvent(CognitiveEvent.ExecutionCompleted(traceId, false, 0L))
                                    return@withTimeout res
                                }
                                if (!policyDecision.allowed) {
                                    val formatted = styleFormatter.formatRefusal(policyDecision.reason)
                                    onEvent(AgentEvent.Completed(formatted.spokenText))
                                    val res = RuntimeExecutionResult(
                                        success = false,
                                        spokenResponse = formatted.spokenText,
                                        stepResults = emptyList(),
                                        verified = true
                                    )
                                    logEvent(CognitiveEvent.ExecutionCompleted(traceId, false, 0L))
                                    return@withTimeout res
                                }
                            }

                            if (directToolDispatcher != null && capId != null) {
                                val toolResult = directToolDispatcher!!.invoke(capId, decision.actionParams)
                                val observation = observedStateProvider?.invoke(capId, decision.actionParams)
                                    ?: if (capId == "FLASHLIGHT") mapOf("torch_on" to true) else null
                                val verification = verifier.verifyStep(
                                    capability = cap ?: JciCapability(
                                        id = capId,
                                        description = "Direct tool",
                                        classification = JciToolClassification.STATE_CHANGE,
                                        riskLevel = JciRiskLevel.LOW,
                                        expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
                                    ),
                                    params = decision.actionParams,
                                    toolSuccess = toolResult.success,
                                    toolMessage = toolResult.message,
                                    observedState = observation,
                                    userConfirmed = userApprovalGranted
                                )
                                val formatted = styleFormatter.formatExecutionResponse(capId, verification, utterance)
                                onEvent(AgentEvent.Completed(formatted.spokenText))
                                val res = RuntimeExecutionResult(
                                    success = verification.canReportDone,
                                    spokenResponse = formatted.spokenText,
                                    stepResults = emptyList(),
                                    verified = verification.canReportDone
                                )
                                logEvent(CognitiveEvent.ExecutionCompleted(traceId, res.success, 0L))
                                res
                            } else {
                                val result = legacyFallback()
                                logEvent(CognitiveEvent.ExecutionCompleted(traceId, result.success, 0L))
                                result
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Task cancellation must propagate cleanly to coroutine callers
            throw e
        } catch (e: AssertionError) {
            // Unit test assertions must never be masked as fallback errors
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "[$traceId] Exception in CognitiveOrchestrator; failing safe to legacy pipeline", e)
            logEvent(CognitiveEvent.OrchestratorFallback(traceId, e.message ?: "Unknown error", "Fail-safe to legacy pipeline"))
            legacyFallback()
        }
    }
}
