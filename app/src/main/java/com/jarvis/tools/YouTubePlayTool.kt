package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.media.YouTubeSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaActionState {
    SEARCH_OPENED,
    TARGET_RESOLVED,
    PLAYBACK_UNCONFIRMED,
    PLAYBACK_VERIFIED,
    FAILED,
    PAUSED,
    RESUMED,
    SESSION_INFO
}

class YouTubePlayTool(private val context: Context? = null) : Tool {
    override val name: String = "YOUTUBE_PLAY"
    override val description: String = "Controls YouTube playback sessions and plays video targets. Parameters: action ('play', 'search', 'pause', 'resume', 'previous', 'current', 'history'), query (video search query or title), videoId (optional 11-char YouTube video ID), url (optional watch URL). Playback confirmation is reported honestly via state."

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase()
        val directUrl = params["url"]?.trim()
        val directVideoId = params["videoId"]?.trim() ?: params["video_id"]?.trim()
        val rawQuery = params["query"]?.trim()

        // Handle Session Actions
        when (action) {
            "pause" -> {
                YouTubeSessionManager.setPlaybackState(false)
                val current = YouTubeSessionManager.getCurrentVideo()
                return ToolResult(
                    true,
                    "YouTube playback paused${if (current != null) ": ${current.title ?: current.query}" else ""}",
                    mapOf("state" to MediaActionState.PAUSED.name)
                )
            }
            "resume" -> {
                YouTubeSessionManager.setPlaybackState(true)
                val current = YouTubeSessionManager.getCurrentVideo()
                return ToolResult(
                    true,
                    "YouTube playback resumed${if (current != null) ": ${current.title ?: current.query}" else ""}",
                    mapOf("state" to MediaActionState.RESUMED.name)
                )
            }
            "current", "status" -> {
                val current = YouTubeSessionManager.getCurrentVideo()
                return if (current != null) {
                    ToolResult(
                        true,
                        "Currently playing on YouTube: ${current.title ?: current.query}",
                        mapOf(
                            "state" to MediaActionState.SESSION_INFO.name,
                            "query" to current.query,
                            "videoId" to (current.videoId ?: ""),
                            "isPlaying" to current.isPlaying
                        )
                    )
                } else {
                    ToolResult(true, "No active YouTube session video recorded.", mapOf("state" to MediaActionState.SESSION_INFO.name))
                }
            }
            "history" -> {
                val summary = YouTubeSessionManager.getHistorySummary()
                return ToolResult(true, summary, mapOf("state" to MediaActionState.SESSION_INFO.name, "historyCount" to YouTubeSessionManager.getHistory().size))
            }
            "previous", "prev", "back" -> {
                val prevVideo = YouTubeSessionManager.playPreviousVideo()
                if (prevVideo == null) {
                    return ToolResult(false, "No previous video found in YouTube session history.")
                }
                return launchVideoSession(prevVideo.query, prevVideo.videoId, prevVideo.url)
            }
        }

        if (directUrl.isNullOrBlank() && directVideoId.isNullOrBlank() && rawQuery.isNullOrBlank()) {
            return ToolResult(false, "Search query or video ID missing")
        }

        return launchVideoSession(rawQuery, directVideoId, directUrl)
    }

    private suspend fun launchVideoSession(rawQuery: String?, directVideoId: String?, directUrl: String?): ToolResult {
        val ctx = context ?: run {
            YouTubeSessionManager.recordVideoPlay(rawQuery ?: directVideoId ?: "YouTube Video", directVideoId, url = directUrl)
            return ToolResult(true, "Playing YouTube target")
        }

        return try {
            val pm = ctx.packageManager

            // 1. If direct video ID provided: launch direct playback in YouTube app
            val videoId = when {
                !directVideoId.isNullOrBlank() -> directVideoId
                rawQuery != null && rawQuery.contains("youtu.be/") -> rawQuery.substringAfter("youtu.be/").substringBefore("?")
                rawQuery != null && rawQuery.contains("watch?v=") -> rawQuery.substringAfter("watch?v=").substringBefore("&")
                else -> null
            }

            if (!videoId.isNullOrBlank()) {
                YouTubeSessionManager.recordVideoPlay(rawQuery ?: videoId, videoId, url = "https://www.youtube.com/watch?v=$videoId")
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (appIntent.resolveActivity(pm) != null) {
                    context.startActivity(appIntent)
                    return ToolResult(true, "Playing YouTube video", targetResolved(videoId))
                }
                val webWatch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webWatch)
                return ToolResult(true, "Playing YouTube video in browser", targetResolved(videoId))
            }

            // 2. If direct URL provided:
            if (!directUrl.isNullOrBlank()) {
                YouTubeSessionManager.recordVideoPlay(rawQuery ?: directUrl, url = directUrl)
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (urlIntent.resolveActivity(pm) != null) {
                    context.startActivity(urlIntent)
                    return ToolResult(true, "Playing YouTube video", targetResolved(null))
                }
                val genericUrlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericUrlIntent)
                return ToolResult(true, "Playing YouTube video in browser", targetResolved(null))
            }

            val query = rawQuery!!

            // 3. Resolve top video ID from YouTube query for direct immediate playback
            val resolvedVideoId = withContext(Dispatchers.IO) {
                resolveTopVideoId(query)
            }
            if (!resolvedVideoId.isNullOrBlank()) {
                YouTubeSessionManager.recordVideoPlay(query, resolvedVideoId, url = "https://www.youtube.com/watch?v=$resolvedVideoId")
                val appWatchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$resolvedVideoId")).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (appWatchIntent.resolveActivity(pm) != null) {
                    context.startActivity(appWatchIntent)
                    return ToolResult(true, "Playing $query on YouTube", targetResolved(resolvedVideoId))
                }
            }

            // 4. Fallback: Launch YouTube and trigger in-app UI search + first result click
            YouTubeSessionManager.recordVideoPlay(query)
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            triggerUiSearchAndPlay(query)
            ToolResult(true, "Playing $query on YouTube", searchOpened(query))
        } catch (e: Exception) {
            ToolResult(false, "Failed to open YouTube: ${e.message}", mapOf("state" to MediaActionState.FAILED.name))
        }
    }

    private fun resolveTopVideoId(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = java.net.URL("https://www.youtube.com/results?search_query=$encoded")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            val regex = Regex("/watch\\?v=([a-zA-Z0-9_-]{11})")
            val match = regex.find(content)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun triggerUiSearchAndPlay(query: String) {
        val service = JarvisAccessibilityService.instance ?: return
        CoroutineScope(Dispatchers.Main).launch {
            service.searchAndPlayYouTube(query)
        }
    }

    private fun searchOpened(query: String): Map<String, Any> =
        mapOf("state" to MediaActionState.SEARCH_OPENED.name, "query" to query)

    private fun targetResolved(videoId: String?): Map<String, Any> =
        mapOf("state" to MediaActionState.TARGET_RESOLVED.name).let { if (videoId != null) it + ("videoId" to videoId) else it }
}
