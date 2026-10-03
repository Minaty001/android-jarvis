package com.jarvis.media

import android.content.Context

data class YouTubeVideoSession(
    val query: String,
    val videoId: String? = null,
    val title: String? = null,
    val url: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var isPlaying: Boolean = true
)

/**
 * Manages active YouTube sessions, playback states, current playing video,
 * and previous video playback history stack.
 */
object YouTubeSessionManager {
    private var currentVideo: YouTubeVideoSession? = null
    private val history = mutableListOf<YouTubeVideoSession>()
    private var isPaused: Boolean = false

    @Synchronized
    fun recordVideoPlay(query: String, videoId: String? = null, title: String? = null, url: String? = null) {
        currentVideo?.let { prev ->
            // Move current video to history stack if not duplicate of last item
            if (history.isEmpty() || history.first().query != prev.query || history.first().videoId != prev.videoId) {
                history.add(0, prev.copy(isPlaying = false))
            }
            if (history.size > 50) {
                history.removeAt(history.lastIndex)
            }
        }

        val newSession = YouTubeVideoSession(
            query = query,
            videoId = videoId,
            title = title ?: query,
            url = url ?: (if (!videoId.isNullOrBlank()) "https://www.youtube.com/watch?v=$videoId" else null),
            isPlaying = true
        )
        currentVideo = newSession
        isPaused = false
    }

    @Synchronized
    fun setPlaybackState(playing: Boolean) {
        isPaused = !playing
        currentVideo?.isPlaying = playing
    }

    @Synchronized
    fun getCurrentVideo(): YouTubeVideoSession? = currentVideo

    @Synchronized
    fun isPlaying(): Boolean = (currentVideo?.isPlaying == true) && !isPaused

    @Synchronized
    fun getPreviousVideo(): YouTubeVideoSession? {
        return history.firstOrNull()
    }

    @Synchronized
    fun playPreviousVideo(): YouTubeVideoSession? {
        if (history.isEmpty()) return null
        val prev = history.removeAt(0)
        currentVideo = prev.copy(isPlaying = true, timestamp = System.currentTimeMillis())
        isPaused = false
        return currentVideo
    }

    @Synchronized
    fun getHistory(): List<YouTubeVideoSession> = history.toList()

    @Synchronized
    fun getHistorySummary(): String {
        if (history.isEmpty() && currentVideo == null) {
            return "No YouTube playback history available."
        }
        val sb = StringBuilder()
        currentVideo?.let {
            sb.append("Currently Playing: ${it.title ?: it.query} (${if (it.isPlaying && !isPaused) "PLAYING" else "PAUSED"})\n")
        }
        if (history.isNotEmpty()) {
            sb.append("Recent Played Stack:\n")
            history.take(5).forEachIndexed { idx, item ->
                sb.append("${idx + 1}. ${item.title ?: item.query}\n")
            }
        }
        return sb.toString().trim()
    }

    @Synchronized
    fun clear() {
        currentVideo = null
        history.clear()
        isPaused = false
    }
}
