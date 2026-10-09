package com.jarvis.foundation.worldstate

import java.util.Locale

object ForegroundAppSession {
    val sharedUiApps = setOf(
        "WhatsApp", "YouTube", "YouTube Music", "Play Music", "Camera", "Samsung Notes", "ChatGPT", "Gemini",
        "Claude", "DeepSeek", "Perplexity", "MX Player", "Netflix", "Samsung Music",
        "Google Search", "Chrome", "Gaming Hub", "Telegram", "Snapchat", "Instagram",
        "Gmail", "PicsArt", "OneRoom", "PUBG", "Genshin", "PhonePe", "Kotak 811",
        "MobiKwik", "Google Keep", "Google Docs", "My Files", "Files by Google",
        "Google Photos", "Google Maps", "Calculator", "Voice Recorder", "DigiLocker",
        "Termux", "GitHub", "ZArchiver", "KDE Connect", "Calendar", "Clock",
        "FM Radio", "Reminder", "Bixby", "Gallery", "Galaxy Store", "Settings",
        "Play Store", "Amazon"
    )

    fun fromPackage(packageName: String?): String? {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
        val lower = pkg.lowercase(Locale.ROOT)
        return when {
            "whatsapp" in lower -> "WhatsApp"
            "google.android.apps.youtube.music" in lower -> "YouTube Music"
            "google.android.music" in lower -> "Play Music"
            "youtube" in lower || "ytube" in lower -> "YouTube"
            "camera" in lower -> "Camera"
            "samsung.android.app.notes" in lower -> "Samsung Notes"
            "chatgpt" in lower || "openai" in lower -> "ChatGPT"
            "bard" in lower || "gemini" in lower -> "Gemini"
            "claude" in lower -> "Claude"
            "deepseek" in lower -> "DeepSeek"
            "perplexity" in lower -> "Perplexity"
            "mxtech.videoplayer" in lower -> "MX Player"
            "netflix" in lower -> "Netflix"
            "sec.android.app.music" in lower -> "Samsung Music"
            "googlequicksearchbox" in lower -> "Google Search"
            "chrome" in lower -> "Chrome"
            "gamehome" in lower || "gaminghub" in lower -> "Gaming Hub"
            "telegram" in lower -> "Telegram"
            "snapchat" in lower -> "Snapchat"
            "instagram" in lower -> "Instagram"
            "google.android.gm" in lower -> "Gmail"
            "picsart" in lower -> "PicsArt"
            "oneroom" in lower -> "OneRoom"
            "pubg" in lower -> "PUBG"
            "genshin" in lower || "levelinfinite" in lower -> "Genshin"
            "phonepe" in lower -> "PhonePe"
            "kotak811" in lower -> "Kotak 811"
            "mobikwik" in lower -> "MobiKwik"
            "google.android.keep" in lower -> "Google Keep"
            "google.android.apps.docs" in lower -> "Google Docs"
            "myfiles" in lower -> "My Files"
            "nbu.files" in lower -> "Files by Google"
            "google.android.apps.photos" in lower -> "Google Photos"
            "google.android.apps.maps" in lower -> "Google Maps"
            "calculator" in lower -> "Calculator"
            "voicenote" in lower -> "Voice Recorder"
            "digilocker" in lower -> "DigiLocker"
            "termux" in lower -> "Termux"
            "github" in lower -> "GitHub"
            "zarchiver" in lower -> "ZArchiver"
            "kdeconnect" in lower -> "KDE Connect"
            "calendar" in lower -> "Calendar"
            "clock" in lower -> "Clock"
            "app.fm" in lower -> "FM Radio"
            "reminder" in lower -> "Reminder"
            "bixby" in lower -> "Bixby"
            "gallery3d" in lower -> "Gallery"
            "samsungapps" in lower -> "Galaxy Store"
            "settings" in lower -> "Settings"
            "vending" in lower -> "Play Store"
            "amazon" in lower -> "Amazon"
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
    val activeAppSession: String? = null,
    val activeWhatsAppContact: String? = null,
    val activeMediaTrack: String? = null
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
        activeWhatsAppContact?.let { append(", WHATSAPP CHAT: $it") }
        activeMediaTrack?.let { append(", PLAYING: $it") }
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
