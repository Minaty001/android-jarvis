package com.jarvis.foundation.worldstate

import java.util.Locale

object ForegroundAppSession {
    val sharedUiApps = setOf("WhatsApp", "Instagram", "Chrome", "Telegram", "YouTube")

    fun fromPackage(packageName: String?): String? {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
        val lower = pkg.lowercase(Locale.ROOT)
        return when {
            "whatsapp" in lower -> "WhatsApp"
            "instagram" in lower -> "Instagram"
            "chrome" in lower -> "Chrome"
            "telegram" in lower -> "Telegram"
            "youtube" in lower -> "YouTube"
            "spotify" in lower -> "Spotify"
            else -> pkg.substringAfterLast('.')
        }
    }

    fun supportsSharedUi(session: String?): Boolean = session in sharedUiApps

    fun contextualSearchQuery(goal: String, session: String?): String? {
        if (!supportsSharedUi(session)) return null
        val command = goal.trim()
        val lower = command.lowercase(Locale.ROOT)
        if (!lower.startsWith("search ")) return null
        if (Regex("^search\\s+(?:(?:in)\\s+)?(?:the\\s+)?(?:web|online|internet)\\b|^search\\s+(?:in\\s+)?(?:documents?|notes?|files?|memory|knowledge)\\b")
                .containsMatchIn(lower)
        ) return null

        return command
            .replace(Regex("(?i)^search\\s+(?:(?:in|on)\\s+)?(?:(?:this|the|current)\\s+)?app\\s+(?:for\\s+)?|^search\\s+(?:for\\s+)?"), "")
            .replace(Regex("(?i)\\s+(?:in\\s+(?:this|the)\\s+app|here|on this screen)$"), "")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}

data class WorldStateSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val ambientMode: String? = null,
    val activeMeetingTitle: String? = null,
    val notificationCount: Int = 0,
    val mediaPlaying: Boolean = false,
    val mediaTitle: String? = null,
    val activeTaskTitle: String? = null,
    val activeTaskStatus: String? = null,
    val missingPermissions: List<String> = emptyList(),
    val foregroundPackage: String? = null,
    val activeAppSession: String? = null
) {
    fun overview(): String = buildString {
        append("Battery: $batteryPercent%")
        if (isCharging) append(" (charging)")
        ambientMode?.let { append(", ambient: $it") }
        activeMeetingTitle?.let { append(", meeting: $it") }
        if (notificationCount > 0) append(", $notificationCount unread notifications")
        if (mediaPlaying) append(", media playing: ${mediaTitle ?: "unknown"}")
        activeTaskTitle?.let { append(", active task: $it [$activeTaskStatus]") }
        if (missingPermissions.isNotEmpty()) append(", missing permissions: ${missingPermissions.joinToString()}")
        activeAppSession?.let { append(", ACTIVE APP SESSION: $it") }
    }.toString()
}


/** Keeps a small history of device states so a command's before/after state can be observed. */
class WorldStateService(private val provider: com.jarvis.autonomous.AndroidStateProvider) {
    private val history = ArrayDeque<WorldStateSnapshot>()

    fun fresh(): WorldStateSnapshot = provider.snapshot().also(::record)

    fun latest(): WorldStateSnapshot = history.lastOrNull() ?: provider.snapshot()

    fun prior(): WorldStateSnapshot? = if (history.size >= 2) history[history.size - 2] else null

    val historySize: Int get() = history.size

    private fun record(snapshot: WorldStateSnapshot) {
        history.addLast(snapshot)
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}
