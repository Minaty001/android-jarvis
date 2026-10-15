package com.jarvis.tools

import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.tools.ToolPolicy

/**
 * Tool to adjust playback speed of the currently active media session.
 * Requires API level 31 (Android 12) for PlaybackParams support.
 */
class PlaybackSpeedTool(private val context: Context) : Tool {
    override val name: String = "PLAYBACK_SPEED"
    override val description: String = "Set playback speed for the currently playing media. Parameter: speed (float)."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.MEDIUM)

    private val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = emptyList(),
        riskLevel = RiskLevel.MEDIUM
    )

    @RequiresApi(Build.VERSION_CODES.S)
    override suspend fun execute(params: Map<String, String>): ToolResult {
        val speedStr = params["speed"]?.trim() ?: return ToolResult.Failed("Missing 'speed' parameter.")
        val speed = speedStr.toFloatOrNull() ?: return ToolResult.Failed("Invalid speed value: $speedStr")
        if (speed <= 0f) return ToolResult.Failed("Speed must be positive.")

        return try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mediaSessionManager.getActiveSessions(null)
            if (controllers.isEmpty()) {
                return ToolResult.Failed("No active media session found.")
            }
            // Use the first active controller
            val controller = controllers[0]
            controller.transportControls.setPlaybackSpeed(speed)
            Log.i(name, "Set playback speed to $speed")
            ToolResult.Success(message = "Playback speed set to $speed", data = mapOf("speed" to speed))
        } catch (e: Exception) {
            Log.e(name, "Failed to set playback speed", e)
            ToolResult.Failed("Error setting playback speed: ${e.message}")
        }
    }
}
