package com.jarvis.media

data class SpotifyTrackSession(
    val query: String,
    val trackTitle: String? = null,
    val artist: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var isPlaying: Boolean = true
)

/**
 * Manages Spotify music sessions, currently playing track state, and
 * track playback history stack.
 */
object SpotifySessionManager {
    private var currentTrack: SpotifyTrackSession? = null
    private val history = mutableListOf<SpotifyTrackSession>()
    private var isPaused: Boolean = false

    @Synchronized
    fun recordTrackPlay(query: String, trackTitle: String? = null, artist: String? = null) {
        currentTrack?.let { prev ->
            if (history.isEmpty() || history.first().query != prev.query || history.first().trackTitle != prev.trackTitle) {
                history.add(0, prev.copy(isPlaying = false))
            }
            if (history.size > 50) {
                history.removeAt(history.lastIndex)
            }
        }

        val newSession = SpotifyTrackSession(
            query = query,
            trackTitle = trackTitle ?: query,
            artist = artist,
            isPlaying = true
        )
        currentTrack = newSession
        isPaused = false
    }

    @Synchronized
    fun setPlaybackState(playing: Boolean) {
        isPaused = !playing
        currentTrack?.isPlaying = playing
    }

    @Synchronized
    fun getCurrentTrack(): SpotifyTrackSession? = currentTrack

    @Synchronized
    fun isPlaying(): Boolean = (currentTrack?.isPlaying == true) && !isPaused

    @Synchronized
    fun playPreviousTrack(): SpotifyTrackSession? {
        if (history.isEmpty()) return null
        val prev = history.removeAt(0)
        currentTrack = prev.copy(isPlaying = true, timestamp = System.currentTimeMillis())
        isPaused = false
        return currentTrack
    }

    @Synchronized
    fun getHistory(): List<SpotifyTrackSession> = history.toList()

    @Synchronized
    fun getHistorySummary(): String {
        if (history.isEmpty() && currentTrack == null) {
            return "No Spotify playback history available."
        }
        val sb = StringBuilder()
        currentTrack?.let {
            sb.append("Currently Playing on Spotify: ${it.trackTitle ?: it.query}${if (!it.artist.isNullOrBlank()) " by ${it.artist}" else ""} (${if (it.isPlaying && !isPaused) "PLAYING" else "PAUSED"})\n")
        }
        if (history.isNotEmpty()) {
            sb.append("Recent Spotify Played Stack:\n")
            history.take(5).forEachIndexed { idx, item ->
                sb.append("${idx + 1}. ${item.trackTitle ?: item.query}\n")
            }
        }
        return sb.toString().trim()
    }

    @Synchronized
    fun clear() {
        currentTrack = null
        history.clear()
        isPaused = false
    }
}
