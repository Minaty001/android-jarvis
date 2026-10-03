package com.jarvis.media

data class AiAssistantSession(
    val appName: String, // ChatGPT, Claude, Gemini, DeepSeek, Perplexity
    val promptQuery: String,
    val responseSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages AI assistant app sessions (ChatGPT, Claude, Gemini, DeepSeek, Perplexity),
 * prompt history stack, and conversation context.
 */
object AiAssistantSessionManager {
    private var currentSession: AiAssistantSession? = null
    private val history = mutableListOf<AiAssistantSession>()

    @Synchronized
    fun recordPrompt(appName: String, promptQuery: String, responseSummary: String? = null) {
        currentSession?.let { prev ->
            if (history.isEmpty() || history.first().promptQuery != prev.promptQuery) {
                history.add(0, prev)
            }
            if (history.size > 50) {
                history.removeAt(history.lastIndex)
            }
        }

        val newSession = AiAssistantSession(
            appName = appName.trim(),
            promptQuery = promptQuery.trim(),
            responseSummary = responseSummary?.trim()
        )
        currentSession = newSession
    }

    @Synchronized
    fun getCurrentSession(): AiAssistantSession? = currentSession

    @Synchronized
    fun getPreviousSession(): AiAssistantSession? {
        if (history.isEmpty()) return null
        val prev = history.removeAt(0)
        currentSession = prev.copy(timestamp = System.currentTimeMillis())
        return currentSession
    }

    @Synchronized
    fun getHistory(): List<AiAssistantSession> = history.toList()

    @Synchronized
    fun getHistorySummary(): String {
        if (history.isEmpty() && currentSession == null) {
            return "No AI assistant session history available."
        }
        val sb = StringBuilder()
        currentSession?.let {
            sb.append("Active AI Session (${it.appName}): '${it.promptQuery}'\n")
        }
        if (history.isNotEmpty()) {
            sb.append("Recent AI Conversations:\n")
            history.take(5).forEachIndexed { idx, item ->
                sb.append("${idx + 1}. [${item.appName}] ${item.promptQuery}\n")
            }
        }
        return sb.toString().trim()
    }

    @Synchronized
    fun clear() {
        currentSession = null
        history.clear()
    }
}
