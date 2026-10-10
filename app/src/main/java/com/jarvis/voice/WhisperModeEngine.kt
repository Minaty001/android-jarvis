package com.jarvis.voice

import android.util.Log
import kotlin.math.sqrt

/**
 * Whisper Mode Detection Engine.
 * Analyzes audio frames to detect unvoiced whispered utterances
 * (characterized by low overall RMS energy coupled with high zero-crossing rates).
 * Automatically signals TTS to lower speech volume and soften pitch for privacy.
 */
class WhisperModeEngine {

    private val TAG = "WhisperModeEngine"

    var isWhisperModeEngaged: Boolean = false
        private set

    fun analyzeAudioFrame(buffer: ShortArray, readSize: Int): Boolean {
        if (readSize <= 0) return false

        var sumSquare = 0.0
        var zeroCrossings = 0

        for (i in 0 until readSize) {
            val sample = buffer[i]
            sumSquare += sample * sample

            if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        val rms = sqrt(sumSquare / readSize)
        val zcr = zeroCrossings.toDouble() / readSize

        // Whispering criteria:
        // RMS between 150 and 1200 (audible but low energy)
        // High zero-crossing rate (> 0.15) indicating unvoiced friction sounds
        val isWhisper = rms in 150.0..1200.0 && zcr > 0.15
        if (isWhisper && !isWhisperModeEngaged) {
            Log.i(TAG, "Whisper speech detected: RMS=$rms, ZCR=$zcr")
        }

        isWhisperModeEngaged = isWhisper
        return isWhisper
    }

    fun getWhisperTtsParams(): Map<String, Float> {
        return if (isWhisperModeEngaged) {
            mapOf("pitch" to 0.85f, "volume" to 0.35f, "speechRate" to 0.95f)
        } else {
            mapOf("pitch" to 1.0f, "volume" to 1.0f, "speechRate" to 1.0f)
        }
    }
}
