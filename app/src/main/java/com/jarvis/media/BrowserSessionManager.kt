package com.jarvis.media

data class BrowserTabSession(
    val urlOrQuery: String,
    val pageTitle: String? = null,
    val browserApp: String = "Chrome",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages web browser sessions (Chrome, Browser), web search query history,
 * active URL, and back-stack history.
 */
object BrowserSessionManager {
    private var currentTab: BrowserTabSession? = null
    private val history = mutableListOf<BrowserTabSession>()

    @Synchronized
    fun recordNavigation(urlOrQuery: String, pageTitle: String? = null, browserApp: String = "Chrome") {
        currentTab?.let { prev ->
            if (history.isEmpty() || history.first().urlOrQuery != prev.urlOrQuery) {
                history.add(0, prev)
            }
            if (history.size > 50) {
                history.removeAt(history.lastIndex)
            }
        }

        val newSession = BrowserTabSession(
            urlOrQuery = urlOrQuery.trim(),
            pageTitle = pageTitle?.trim() ?: urlOrQuery.trim(),
            browserApp = browserApp
        )
        currentTab = newSession
    }

    @Synchronized
    fun getCurrentTab(): BrowserTabSession? = currentTab

    @Synchronized
    fun getPreviousTab(): BrowserTabSession? {
        if (history.isEmpty()) return null
        val prev = history.removeAt(0)
        currentTab = prev.copy(timestamp = System.currentTimeMillis())
        return currentTab
    }

    @Synchronized
    fun getHistory(): List<BrowserTabSession> = history.toList()

    @Synchronized
    fun getHistorySummary(): String {
        if (history.isEmpty() && currentTab == null) {
            return "No browser session history available."
        }
        val sb = StringBuilder()
        currentTab?.let {
            sb.append("Active Browser Tab (${it.browserApp}): ${it.pageTitle ?: it.urlOrQuery}\n")
        }
        if (history.isNotEmpty()) {
            sb.append("Recent Browser History:\n")
            history.take(5).forEachIndexed { idx, item ->
                sb.append("${idx + 1}. ${item.pageTitle ?: item.urlOrQuery}\n")
            }
        }
        return sb.toString().trim()
    }

    @Synchronized
    fun clear() {
        currentTab = null
        history.clear()
    }
}
