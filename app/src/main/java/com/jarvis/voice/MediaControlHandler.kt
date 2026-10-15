package com.jarvis.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.jarvis.tools.MediaPlaybackControlTool
import com.jarvis.tools.OpenAppTool
import com.jarvis.tools.PlaybackSpeedTool
import com.jarvis.tools.ToolResult
import com.jarvis.voice.AppWhitelist
import kotlinx.coroutines.runBlocking

/**
 * Handles voice‑triggered media control commands. The handler is injected into [BabelFishInterpreter] and can be called directly
 * to execute commands without round‑tripping through the LLM.
 */
class MediaControlHandler(
    private val context: Context,
    private val audioManager: AudioManager? = null
) {
    private val TAG = "MediaControlHandler"
    private val playbackTool = MediaPlaybackControlTool(context)
    private val speedTool = PlaybackSpeedTool(context)
    private val openAppTool = OpenAppTool(context)

    /** Simple sealed hierarchy representing supported commands. */
    sealed class MediaControlCommand {
        object VolumeUp : MediaControlCommand()
        object VolumeDown : MediaControlCommand()
        object Mute : MediaControlCommand()
        object Unmute : MediaControlCommand()
        object PlayPause : MediaControlCommand()
        object NextTrack : MediaControlCommand()
        object PreviousTrack : MediaControlCommand()
        data class LaunchApp(val appName: String) : MediaControlCommand()
        data class SetSpeed(val speed: Float) : MediaControlCommand()
    }

    /**
     * Attempt to parse a raw voice string into a [MediaControlCommand].
     * Returns null when the text does not match any known pattern.
     */
    fun parseCommand(text: String): MediaControlCommand? {
        val lowered = text.lowercase()
        return when {
            lowered.contains("volume up") || lowered.contains("raise volume") || lowered.contains("louder") -> MediaControlCommand.VolumeUp
            lowered.contains("volume down") || lowered.contains("lower volume") || lowered.contains("quieter") -> MediaControlCommand.VolumeDown
            lowered.contains("mute") -> MediaControlCommand.Mute
            lowered.contains("unmute") -> MediaControlCommand.Unmute
            lowered.contains("play") && lowered.contains("pause") -> MediaControlCommand.PlayPause
            lowered.contains("play") -> MediaControlCommand.PlayPause
            lowered.contains("pause") -> MediaControlCommand.PlayPause
            lowered.contains("next") || lowered.contains("skip") -> MediaControlCommand.NextTrack
            lowered.contains("previous") || lowered.contains("back") -> MediaControlCommand.PreviousTrack
            lowered.startsWith("open ") -> {
                val name = lowered.removePrefix("open ").trim()
                MediaControlCommand.LaunchApp(name)
            }
            lowered.startsWith("set speed ") -> {
                val parts = lowered.removePrefix("set speed ").trim().split(" ")
                val speed = parts.firstOrNull()?.toFloatOrNull()
                if (speed != null) MediaControlCommand.SetSpeed(speed) else null
            }
            else -> null
        }
    }

    /** Execute a parsed command. Returns true if a command was recognised and handled. */
    fun handleCommand(text: String): Boolean {
        val command = parseCommand(text) ?: return false
        Log.i(TAG, "Handling media command: $command")
        try {
            runBlocking {
                when (command) {
                    MediaControlCommand.VolumeUp -> playbackTool.execute(mapOf("action" to "volume_up"))
                    MediaControlCommand.VolumeDown -> playbackTool.execute(mapOf("action" to "volume_down"))
                    MediaControlCommand.Mute -> playbackTool.execute(mapOf("action" to "mute"))
                    MediaControlCommand.Unmute -> playbackTool.execute(mapOf("action" to "unmute"))
                    MediaControlCommand.PlayPause -> playbackTool.execute(mapOf("action" to "play_pause"))
                    MediaControlCommand.NextTrack -> playbackTool.execute(mapOf("action" to "next"))
                    MediaControlCommand.PreviousTrack -> playbackTool.execute(mapOf("action" to "previous"))
                    is MediaControlCommand.LaunchApp -> {
                        val pkg = AppWhitelist.resolve(command.appName) ?: command.appName
                        openAppTool.execute(mapOf("app" to pkg))
                    }
                    is MediaControlCommand.SetSpeed -> {
                        speedTool.execute(mapOf("speed" to command.speed.toString()))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute media command", e)
        }
        return true
    }
}
