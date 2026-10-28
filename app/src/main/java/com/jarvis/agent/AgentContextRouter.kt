package com.jarvis.agent

import com.jarvis.memory.AugmentedMemoryPipeline
import com.jarvis.ai.Message
import com.jarvis.runtime.MemoryNamespaces
import java.util.Locale

enum class ContextNeed {
    NONE,
    CONVERSATION,
    MEMORY,
    KNOWLEDGE_VAULT,
    DEVICE_STATE
}

data class ContextRoutingResult(
    val needs: Set<ContextNeed>,
    val rationale: String
)

class AgentContextRouter(
    private val memoryPipeline: AugmentedMemoryPipeline? = null,
    private val ragInjector: com.jarvis.rag.RagContextInjector? = null,
    private val stateProvider: com.jarvis.autonomous.AndroidStateProvider? = null,
) {
    var activeSessionId: String = "default"
    fun activeAppSession(): String? = stateProvider?.snapshot()?.activeAppSession

    fun routeNeeds(goal: String): Set<ContextNeed> = routeNeedsWithReasoning(goal).needs

    fun routeNeedsWithReasoning(goal: String): ContextRoutingResult {
        val lower = goal.trim().lowercase(Locale.ROOT)
        val needs = mutableSetOf<ContextNeed>()
        val reasons = mutableListOf<String>()

        // 1. Explicit memory references
        if (lower.contains("favorite") || lower.contains("favourite") || lower.contains("mera") ||
            lower.contains("yaad") || lower.contains("preference") || lower.contains("remember") ||
            lower.contains("badiya") || lower.contains("mujhe pasand") || lower.contains("usual") ||
            lower.contains("preferred")
        ) {
            needs.add(ContextNeed.MEMORY)
            reasons.add("User preference and personal memory references detected")
        }

        // 2. Previous context / references (pronouns, anaphora, chat history)
        val hasPronounRef = listOf(" her ", " him ", " them ", " that ", " this ", " it ").any { " $lower ".contains(it) }
        val hasHistoryRef = listOf("wahi", "again", "phir", "last", "chat", "chats", "history", "previous", "baat", "pehle", "purani", "usko").any { lower.contains(it) }
        if (hasPronounRef || hasHistoryRef) {
            needs.add(ContextNeed.CONVERSATION)
            reasons.add("Conversational anaphora or pronoun reference requiring previous chat history")
        }

        // 3. Document / Knowledge Vault references
        if (lower.contains("document") || lower.contains("doc") || lower.contains("notes") ||
            lower.contains("manual") || lower.contains("guide") || lower.contains("pdf") ||
            lower.contains("knowledge") || lower.contains("vault") || lower.contains("specs") ||
            lower.contains("report") || lower.contains("file") || lower.contains("kisme")
        ) {
            needs.add(ContextNeed.KNOWLEDGE_VAULT)
            reasons.add("Document and local knowledge vault query tokens identified")
        }

        // 4. Current device/ambient state references
        if (lower.contains("battery") || lower.contains("status") || lower.contains("notification") ||
            lower.contains("media") || lower.contains("playing") || lower.contains("kya chal") ||
            lower.contains("device state")
        ) {
            needs.add(ContextNeed.DEVICE_STATE)
            reasons.add("Device hardware and ambient state inquiry detected")
        }

        if (needs.isEmpty()) {
            needs.add(ContextNeed.NONE)
            reasons.add("Self-contained single-turn command with no external context required")
        }

        // Always include device state if there is an active foreground app session,
        // so the LLM knows which app is currently open for contextual routing.
        if (activeAppSession() != null) {
            needs.add(ContextNeed.DEVICE_STATE)
            needs.remove(ContextNeed.NONE)
            reasons.add("Active foreground app session present on screen")
        }

        return ContextRoutingResult(needs, reasons.joinToString("; "))
    }

    fun buildContext(
        goal: String,
        state: AgentState,
        workingMemory: AgentWorkingMemory,
        history: List<Message> = emptyList()
    ): String {
        val needs = routeNeeds(goal)
        if (needs.contains(ContextNeed.NONE) && needs.size == 1 && history.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()

        if (needs.contains(ContextNeed.MEMORY)) {
            val memories = memoryPipeline?.retrieveMemory(goal, limit = 4) ?: emptyList()
            if (memories.isNotEmpty()) {
                sb.append("USER MEMORY & PREFERENCES:\n")
                memories.forEach { item ->
                    sb.append("- ${item.key}: ${item.content}\n")
                }
            }
        }

        if (needs.contains(ContextNeed.CONVERSATION) || history.isNotEmpty()) {
            val formattedHistory = if (history.isNotEmpty()) {
                history.takeLast(6).joinToString("\n") { "${it.role.uppercase()}: ${it.content}" }
            } else ""
            val memoryHistoryContext = memoryPipeline?.build(
                currentRequest = goal,
                history = history,
                sessionId = activeSessionId,
                ragLimit = 3,
                magLimit = 3
            )?.formatted ?: ""
            val fullHistory = listOf(formattedHistory, memoryHistoryContext).filter { it.isNotBlank() }.joinToString("\n")
            if (fullHistory.isNotBlank()) {
                sb.append("\nRECENT CONVERSATION HISTORY & CHAT CONTEXT:\n$fullHistory\n")
            }
        }

        if (needs.contains(ContextNeed.KNOWLEDGE_VAULT)) {
            val ragContext = ragInjector?.injectContext(goal, limit = 3) ?: ""
            if (ragContext.isNotBlank()) {
                sb.append("\n$ragContext\n")
            }
        }

        if (needs.contains(ContextNeed.DEVICE_STATE)) {
            stateProvider?.let { provider ->
                sb.append("\nDEVICE STATE (from World State): ${provider.snapshot().overview()}\n")
            }
        }

        if (workingMemory.facts.isNotEmpty()) {
            sb.append("\nWORKING FACTS:\n")
            workingMemory.facts.forEach { (k, v) -> sb.append("- $k: $v\n") }
        }

        if (workingMemory.candidates.isNotEmpty()) {
            sb.append("\nCANDIDATES IDENTIFIED:\n")
            workingMemory.candidates.take(5).forEachIndexed { i, c -> sb.append("[$i] $c\n") }
        }

        return sb.toString().trim()
    }
}
