package com.jarvis.media

import com.jarvis.foundation.worldstate.ForegroundAppSession

data class RegisteredAppSessionRecord(
    val appName: String,
    val packageName: String,
    val lastAction: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Universal App Session Registry.
 * Tracks foreground session state, app usage history timeline, and delegates app-specific
 * session queries across all 50+ registered apps in Jarvis Android.
 */
object AppSessionRegistry {
    private var activeAppName: String? = null
    private var activePackageName: String? = null
    private val appTimeline = mutableListOf<RegisteredAppSessionRecord>()

    @Synchronized
    fun recordAppLaunch(packageName: String, actionDescription: String = "Launched app") {
        val appName = ForegroundAppSession.fromPackage(packageName) ?: packageName.substringAfterLast('.')
        activeAppName = appName
        activePackageName = packageName

        val record = RegisteredAppSessionRecord(
            appName = appName,
            packageName = packageName,
            lastAction = actionDescription
        )

        if (appTimeline.isEmpty() || appTimeline.first().packageName != packageName) {
            appTimeline.add(0, record)
        }
        if (appTimeline.size > 100) {
            appTimeline.removeAt(appTimeline.lastIndex)
        }
    }

    @Synchronized
    fun getActiveApp(): String? = activeAppName

    @Synchronized
    fun getActivePackage(): String? = activePackageName

    @Synchronized
    fun getTimeline(): List<RegisteredAppSessionRecord> = appTimeline.toList()

    @Synchronized
    fun getAppSessionSummary(queryApp: String? = null): String {
        val rawTarget = queryApp?.trim()?.ifBlank { null }
        val sb = StringBuilder()

        if (rawTarget != null && !rawTarget.equals("all", ignoreCase = true) && !rawTarget.equals("overview", ignoreCase = true)) {
            val normalized = ForegroundAppSession.fromPackage(rawTarget) ?: rawTarget
            sb.append("=== App Session Summary for '$normalized' ===\n")
            when {
                normalized.equals("YouTube", ignoreCase = true) -> {
                    sb.append(YouTubeSessionManager.getHistorySummary())
                }
                normalized.equals("WhatsApp", ignoreCase = true) -> {
                    sb.append(WhatsAppSessionManager.getHistorySummary())
                }
                normalized.equals("Spotify", ignoreCase = true) || normalized.contains("Music", ignoreCase = true) -> {
                    sb.append(SpotifySessionManager.getHistorySummary())
                }
                normalized.equals("ChatGPT", ignoreCase = true) || normalized.equals("Claude", ignoreCase = true) ||
                normalized.equals("Gemini", ignoreCase = true) || normalized.equals("DeepSeek", ignoreCase = true) ||
                normalized.equals("Perplexity", ignoreCase = true) -> {
                    sb.append(AiAssistantSessionManager.getHistorySummary())
                }
                normalized.equals("Chrome", ignoreCase = true) || normalized.contains("Browser", ignoreCase = true) -> {
                    sb.append(BrowserSessionManager.getHistorySummary())
                }
                else -> {
                    val matching = appTimeline.firstOrNull { it.appName.equals(normalized, ignoreCase = true) }
                    if (matching != null) {
                        sb.append("App: ${matching.appName} (${matching.packageName})\nLast Action: ${matching.lastAction}\n")
                    } else {
                        sb.append("App session registered for $normalized. Ready for voice controls and UI autopilot.\n")
                    }
                }
            }
        } else {
            sb.append("=== Universal App Session Overview ===\n")
            sb.append("Currently Active App: ${activeAppName ?: "None"}\n")
            if (appTimeline.isNotEmpty()) {
                sb.append("Recent App Launch Stack:\n")
                appTimeline.take(5).forEachIndexed { idx, item ->
                    sb.append("${idx + 1}. ${item.appName} (${item.lastAction})\n")
                }
            }
        }
        return sb.toString().trim()
    }

    @Synchronized
    fun clear() {
        activeAppName = null
        activePackageName = null
        appTimeline.clear()
        YouTubeSessionManager.clear()
        WhatsAppSessionManager.clear()
        SpotifySessionManager.clear()
        AiAssistantSessionManager.clear()
        BrowserSessionManager.clear()
    }
}
