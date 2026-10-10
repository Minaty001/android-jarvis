package com.jarvis.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.jarvis.voice.emotion.EmotionState

class VocalEmpathyEngine(private val context: Context) {
    private val TAG = "VocalEmpathyEngine"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var previousVolume: Int = -1
    private var rmsSum = 0f
    private var rmsCount = 0
    private var isWhispering = false

    fun onSpeechStart() {
        rmsSum = 0f
        rmsCount = 0
        isWhispering = false
    }

    fun onRmsChanged(rms: Float) {
        if (rms > 0) {
            rmsSum += rms
            rmsCount++
        }
    }

    fun onSpeechEnd() {
        val avgRms = if (rmsCount > 0) rmsSum / rmsCount else 0f
        isWhispering = (avgRms > 0 && avgRms < 15f) // threshold
        Log.d(TAG, "Speech ended. Avg RMS: \$avgRms. Is Whispering: \$isWhispering")
    }

    fun adaptTtsPrompt(text: String): String {
        return applyEmpathy(if (isWhispering) EmotionState.QUIET else EmotionState.NEUTRAL, text)
    }

    fun applyEmpathy(emotion: EmotionState, text: String): String {
        return when (emotion) {
            EmotionState.QUIET -> {
                Log.d(TAG, "Whisper mode detected. Lowering TTS volume.")
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val whisperVol = maxVol / 4 // 25% volume
                if (currentVol > whisperVol) {
                    previousVolume = currentVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, whisperVol, 0)
                }
                "[Whispering] \$text"
            }
            EmotionState.EXCITED -> {
                Log.d(TAG, "Excited mode detected.")
                restoreVolume()
                "[Enthusiastically] \$text"
            }
            EmotionState.FRUSTRATED -> {
                Log.d(TAG, "Frustrated mode detected. Staying calm.")
                restoreVolume()
                "[Calmly] \$text"
            }
            EmotionState.NEUTRAL -> {
                restoreVolume()
                text
            }
        }
    }

    fun restoreVolume() {
        if (previousVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            previousVolume = -1
        }
    }
}
