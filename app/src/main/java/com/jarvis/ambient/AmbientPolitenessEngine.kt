package com.jarvis.ambient

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AmbientPolitenessEngine(
    private val context: Context,
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
) {
    private val TAG = "AmbientPolitenessEngine"
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var isRunning = AtomicBoolean(false)
    
    // Thresholds
    private val LOUD_ENVIRONMENT_THRESHOLD_DB = -30.0
    private val QUIET_ENVIRONMENT_THRESHOLD_DB = -60.0
    
    // State
    private var previousMusicVolume: Int = -1
    private var isMutedByEngine = false

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Starting Ambient Politeness Engine")
        
        scope.launch {
            while (isRunning.get()) {
                delay(30000) // Evaluate every 30 seconds
                evaluatePolitenessContext()
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        Log.i(TAG, "Stopped Ambient Politeness Engine")
    }

    private fun evaluatePolitenessContext() {
        try {
            // Check calendar (mocked for now, assumes interaction with CalendarManager)
            val inMeeting = false
            
            // Check ambient noise (would use VAD / microphone in real implementation)
            // val ambientNoiseDb = getAmbientNoiseLevel()
            val ambientNoiseDb = -50.0 // Default to normal
            
            val shouldBeQuiet = inMeeting || ambientNoiseDb < QUIET_ENVIRONMENT_THRESHOLD_DB
            
            if (shouldBeQuiet && !isMutedByEngine) {
                // Enter quiet mode
                previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (previousMusicVolume > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    isMutedByEngine = true
                    Log.i(TAG, "Ambient Politeness: Environment is quiet/meeting. Muted media volume.")
                }
            } else if (!shouldBeQuiet && isMutedByEngine) {
                // Restore volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousMusicVolume, 0)
                isMutedByEngine = false
                Log.i(TAG, "Ambient Politeness: Environment normal. Restored media volume to \$previousMusicVolume.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error evaluating politeness context: \${e.message}")
        }
    }
}
