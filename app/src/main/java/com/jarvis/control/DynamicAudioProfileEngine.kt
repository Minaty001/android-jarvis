package com.jarvis.control

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Dynamic Audio Profile Engine.
 * Adapts device speech volume, ringer intensity, and media output based on the ambient
 * environment (Quiet room, standard office, noisy outdoor street) without blowing out user ears.
 */
class DynamicAudioProfileEngine(private val context: Context? = null) {

    private val TAG = "DynamicAudioProfile"

    enum class AmbientAcousticLevel {
        QUIET,      // < 40 dB
        MODERATE,   // 40 - 65 dB
        LOUD        // > 65 dB
    }

    data class AudioProfileReport(
        val acousticLevel: AmbientAcousticLevel,
        val recommendedVolumePercent: Int,
        val currentMediaVolumePercent: Int,
        val rationale: String
    )

    fun evaluateAmbientProfile(ambientDecibels: Double? = null): AudioProfileReport {
        val am = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5
        val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val currentPct = Math.round((currentVol.toFloat() / maxVol.coerceAtLeast(1)) * 100)

        // If no mic reading passed, default based on time-of-day heuristic
        val level = when {
            ambientDecibels != null && ambientDecibels < 40.0 -> AmbientAcousticLevel.QUIET
            ambientDecibels != null && ambientDecibels > 65.0 -> AmbientAcousticLevel.LOUD
            ambientDecibels != null -> AmbientAcousticLevel.MODERATE
            else -> AmbientAcousticLevel.MODERATE
        }

        val recommendedPct = when (level) {
            AmbientAcousticLevel.QUIET -> 25
            AmbientAcousticLevel.MODERATE -> 55
            AmbientAcousticLevel.LOUD -> 90
        }

        val rationale = when (level) {
            AmbientAcousticLevel.QUIET -> "Environment is quiet. Kept speech volume low to prevent disturbance."
            AmbientAcousticLevel.MODERATE -> "Standard acoustic environment. Balanced volume active."
            AmbientAcousticLevel.LOUD -> "Noisy background detected. Boosted volume to ensure clear speech audibility."
        }

        return AudioProfileReport(
            acousticLevel = level,
            recommendedVolumePercent = recommendedPct,
            currentMediaVolumePercent = currentPct,
            rationale = rationale
        )
    }

    fun applyAdaptiveVolume(ambientDecibels: Double? = null): Int {
        val report = evaluateAmbientProfile(ambientDecibels)
        val am = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return report.currentMediaVolumePercent
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = Math.round((report.recommendedVolumePercent / 100f) * maxVol).coerceIn(1, maxVol)

        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
        Log.i(TAG, "Applied adaptive volume: $targetVol/$maxVol (${report.recommendedVolumePercent}%)")
        return report.recommendedVolumePercent
    }
}
