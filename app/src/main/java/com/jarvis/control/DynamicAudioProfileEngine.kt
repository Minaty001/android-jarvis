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

    data class AudioReasoningContext(
        val hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
        val isHeadsetConnected: Boolean = false,
        val userPreferredVolume: Int? = null,
        val ambientState: String? = null
    )

    data class AudioProfileReport(
        val acousticLevel: AmbientAcousticLevel,
        val recommendedVolumePercent: Int,
        val currentMediaVolumePercent: Int,
        val rationale: String
    )

    fun evaluateAmbientProfile(ambientDecibels: Double? = null): AudioProfileReport =
        evaluateAmbientProfileWithReasoning(ambientDecibels, AudioReasoningContext())

    fun evaluateAmbientProfileWithReasoning(
        ambientDecibels: Double? = null,
        context: AudioReasoningContext = AudioReasoningContext()
    ): AudioProfileReport {
        val am = this.context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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

        var recommendedPct = when (level) {
            AmbientAcousticLevel.QUIET -> 25
            AmbientAcousticLevel.MODERATE -> 55
            AmbientAcousticLevel.LOUD -> 90
        }

        val rationales = mutableListOf<String>()
        rationales.add(
            when (level) {
                AmbientAcousticLevel.QUIET -> "Environment is quiet."
                AmbientAcousticLevel.MODERATE -> "Standard acoustic environment."
                AmbientAcousticLevel.LOUD -> "Noisy background detected."
            }
        )

        val isNightHours = context.hour >= 22 || context.hour < 7
        if (isNightHours) {
            recommendedPct = minOf(recommendedPct, 40)
            rationales.add("Night hours active (${context.hour}:00); capped volume to prevent disturbance.")
        }

        if (context.ambientState.equals("DRIVING", ignoreCase = true)) {
            recommendedPct = maxOf(recommendedPct, 80)
            rationales.add("Driving environment detected; elevated volume for navigation and speech audibility.")
        } else if (context.ambientState.equals("MEETING", ignoreCase = true)) {
            recommendedPct = minOf(recommendedPct, 25)
            rationales.add("Meeting context active; reduced volume.")
        }

        if (context.isHeadsetConnected) {
            recommendedPct = (recommendedPct - 20).coerceAtLeast(15)
            rationales.add("Connected headset detected; lowered output to protect user hearing.")
        }

        if (context.userPreferredVolume != null) {
            recommendedPct = Math.round(recommendedPct * 0.4f + context.userPreferredVolume * 0.6f)
            rationales.add("Blended with user learned preference (${context.userPreferredVolume}%).")
        }

        return AudioProfileReport(
            acousticLevel = level,
            recommendedVolumePercent = recommendedPct.coerceIn(10, 100),
            currentMediaVolumePercent = currentPct,
            rationale = rationales.joinToString(" ")
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
