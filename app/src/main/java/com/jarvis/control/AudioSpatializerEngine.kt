package com.jarvis.control

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Hardware Audio Spatializer & Equalizer Tuner.
 * Directly interfaces with native Android audio effects (Equalizer and Virtualizer)
 * to provide hardware-accelerated sound enhancement profiles.
 */
class AudioSpatializerEngine(private val context: Context? = null) {

    private val TAG = "AudioSpatializerEngine"

    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null

    init {
        try {
            // Apply to global session (audioSession = 0)
            equalizer = Equalizer(0, 0).apply { enabled = true }
            virtualizer = Virtualizer(0, 0).apply { enabled = true }
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect initialization note: ${e.message}")
        }
    }

    enum class SoundPreset {
        VOICE_CLARITY,
        BASS_BOOST,
        IMMERSIVE_3D,
        FLAT
    }

    fun getAvailablePresets(): List<String> = SoundPreset.values().map { it.name }

    fun applyPreset(presetName: String): String {
        val preset = try {
            SoundPreset.valueOf(presetName.trim().uppercase())
        } catch (_: Exception) {
            SoundPreset.VOICE_CLARITY
        }

        return try {
            val eq = equalizer
            val virt = virtualizer

            when (preset) {
                SoundPreset.VOICE_CLARITY -> {
                    virt?.setStrength(200.toShort())
                    eq?.let {
                        val bands = it.numberOfBands
                        if (bands >= 3) {
                            it.setBandLevel(0, (-200).toShort()) // Reduce low mud
                            it.setBandLevel((bands / 2).toShort(), 300.toShort()) // Boost voice mids
                            it.setBandLevel((bands - 1).toShort(), 200.toShort()) // High presence
                        }
                    }
                    "Applied Voice Clarity preset (mids boosted, background rumble cut)."
                }
                SoundPreset.BASS_BOOST -> {
                    virt?.setStrength(400.toShort())
                    eq?.let {
                        if (it.numberOfBands >= 1) {
                            it.setBandLevel(0, 500.toShort())
                        }
                    }
                    "Applied Bass Boost preset."
                }
                SoundPreset.IMMERSIVE_3D -> {
                    virt?.setStrength(1000.toShort())
                    "Applied Immersive 3D Spatial Audio preset."
                }
                SoundPreset.FLAT -> {
                    virt?.setStrength(0.toShort())
                    eq?.let {
                        for (b in 0 until it.numberOfBands) {
                            it.setBandLevel(b.toShort(), 0.toShort())
                        }
                    }
                    "Reset audio profile to Flat / Neutral."
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying preset $presetName: ${e.message}")
            "Preset applied in virtual mode (${preset.name}): Hardware FX not supported on this device/emulator."
        }
    }

    fun release() {
        try {
            equalizer?.release()
            virtualizer?.release()
        } catch (_: Exception) {}
    }
}
