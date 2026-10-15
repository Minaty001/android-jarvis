package com.jarvis.voice

/**
 * Simple whitelist of allowed app names to package identifiers.
 * The keys are lower‑cased friendly names used in voice commands.
 */
object AppWhitelist {
    private val WHITELIST: Map<String, String> = mapOf(
        "spotify" to "com.spotify.music",
        "youtube" to "com.google.android.youtube",
        "google maps" to "com.google.android.apps.maps",
        "browser" to "com.android.chrome",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "settings" to "com.android.settings"
        // Add more entries as needed
    )

    /** Resolve a spoken app name to its package name, or null if not whitelisted. */
    fun resolve(appName: String): String? = WHITELIST[appName.lowercase()]
}
