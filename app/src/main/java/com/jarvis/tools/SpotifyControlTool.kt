package com.jarvis.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.media.MediaSessionManager
import com.jarvis.media.SpotifySessionManager

class SpotifyControlTool(
    private val context: Context? = null,
    private val sessionManager: MediaSessionManager? = null
) : Tool {
    override val name: String = "SPOTIFY_PLAY"
    override val description: String =
        "Searches and controls music on Spotify. Parameters: action ('play', 'pause', 'resume', 'previous', 'current', 'history'), query (song title, artist, playlist name)."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "SPOTIFY_PLAY",
        description = "Deep links and plays music, playlists, and artists on Spotify.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase()
        val query = params["query"]?.trim().orEmpty()

        when (action) {
            "pause" -> {
                SpotifySessionManager.setPlaybackState(false)
                val current = SpotifySessionManager.getCurrentTrack()
                return ToolResult.Success("Spotify playback paused${if (current != null) ": '${current.trackTitle ?: current.query}'" else ""}")
            }
            "resume" -> {
                SpotifySessionManager.setPlaybackState(true)
                val current = SpotifySessionManager.getCurrentTrack()
                return ToolResult.Success("Spotify playback resumed${if (current != null) ": '${current.trackTitle ?: current.query}'" else ""}")
            }
            "current", "status" -> {
                val current = SpotifySessionManager.getCurrentTrack()
                return if (current != null) {
                    ToolResult.Success("Currently playing on Spotify: ${current.trackTitle ?: current.query}${if (current.artist != null) " by ${current.artist}" else ""}")
                } else {
                    ToolResult.Success("No active Spotify track session recorded.")
                }
            }
            "history" -> {
                val summary = SpotifySessionManager.getHistorySummary()
                return ToolResult.Success(summary)
            }
            "previous", "prev" -> {
                val prevTrack = SpotifySessionManager.playPreviousTrack()
                if (prevTrack == null) {
                    return ToolResult.Failed("No previous track found in Spotify session history.")
                }
                return launchSpotifyQuery(prevTrack.query)
            }
        }

        return launchSpotifyQuery(query)
    }

    private fun launchSpotifyQuery(query: String): ToolResult {
        val ctx = context ?: run {
            if (query.isNotBlank()) SpotifySessionManager.recordTrackPlay(query)
            return ToolResult.Success("Playing Spotify target (Simulation mode).")
        }

        val spotifyPkg = "com.spotify.music"
        return try {
            val isInstalled = ctx.packageManager.getLaunchIntentForPackage(spotifyPkg) != null

            if (query.isBlank()) {
                if (isInstalled) {
                    val launchIntent = ctx.packageManager.getLaunchIntentForPackage(spotifyPkg)!!
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(launchIntent)
                    sessionManager?.recordTrackPlay(title = "Spotify App", playerApp = "spotify")
                    SpotifySessionManager.recordTrackPlay("Spotify App")
                    ToolResult.Success("Opening Spotify.", mapOf("launched" to true))
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(webIntent)
                    ToolResult.Success("Opening Spotify Web Player.", mapOf("launched" to "web"))
                }
            } else {
                sessionManager?.recordTrackPlay(title = query, playerApp = "spotify")
                SpotifySessionManager.recordTrackPlay(query)

                if (isInstalled) {
                    val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        `package` = spotifyPkg
                        putExtra(SearchManager.QUERY, query)
                        putExtra(MediaStore.EXTRA_MEDIA_TITLE, query)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (mediaIntent.resolveActivity(ctx.packageManager) != null) {
                        ctx.startActivity(mediaIntent)
                        ToolResult.Success("Playing '$query' on Spotify.", mapOf("query" to query, "source" to "app"))
                    } else {
                        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
                            `package` = spotifyPkg
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(uriIntent)
                        ToolResult.Success("Searching '$query' in Spotify.", mapOf("query" to query, "source" to "uri"))
                    }
                } else {
                    val webUrl = "https://open.spotify.com/search/${Uri.encode(query)}"
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(webIntent)
                    ToolResult.Success("Opening '$query' on Spotify Web.", mapOf("query" to query, "source" to "web"))
                }
            }
        } catch (e: Exception) {
            ToolResult.Failed("Failed to play on Spotify: ${e.message}")
        }
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        if (!result.success) {
            return VerificationResult.failure("Spotify dispatch failed: ${result.message}")
        }
        val source = result.data["source"] as? String
        return when {
            result.data["launched"] == true || source != null -> VerificationResult.success(
                "Spotify ${if (source != null) "$source " else ""}dispatch confirmed",
                mapOf("outcome" to "DISPATCHED", "playback_confirmed" to "false")
            )
            else -> VerificationResult.unknown("Spotify opened but source not reported", mapOf("outcome" to "UNPARSED"))
        }
    }
}
