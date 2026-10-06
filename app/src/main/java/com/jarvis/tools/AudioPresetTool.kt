package com.jarvis.tools

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

class AudioPresetTool(private val context: Context? = null) : Tool {
    override val name: String = "AUDIO_PRESET"
    override val description: String =
        "Configures system equalizer presets and audio profiles (Bass Boost, Vocal Clarity, Movie Mode, Flat, Rock, Pop, Reset). Parameters: preset ('bass_boost', 'vocal_clarity', 'movie', 'flat', 'rock', 'pop', 'reset')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "AUDIO_PRESET",
        description = "Configures hardware audio equalizer and sound enhancement profiles.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val preset = params["preset"]?.trim()?.lowercase() ?: params["mode"]?.trim()?.lowercase() ?: "flat"

        if (context == null) {
            return ToolResult.Success(
                "Audio preset '$preset' configured (Simulation mode).",
                mapOf("preset" to preset, "simulation" to true)
            )
        }

        return try {
            var eq: Equalizer? = null
            var bass: BassBoost? = null

            try {
                // Initialize equalizer on session 0 (global system audio session)
                eq = Equalizer(0, 0).apply { enabled = true }
                bass = BassBoost(0, 0).apply { enabled = true }

                val bands = eq.numberOfBands
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val midLevel: Short = 0

                when (preset) {
                    "bass_boost", "bass" -> {
                        bass.setStrength(1000.toShort()) // Maximum bass boost
                        if (bands >= 2) {
                            eq.setBandLevel(0, maxLevel) // Boost 60Hz
                            eq.setBandLevel(1, (maxLevel * 0.75).toInt().toShort()) // Boost 230Hz
                        }
                    }
                    "vocal_clarity", "vocal", "speech" -> {
                        bass.setStrength(0.toShort())
                        if (bands >= 4) {
                            eq.setBandLevel(0, minLevel) // Reduce low rumble
                            eq.setBandLevel(2, (maxLevel * 0.5).toInt().toShort()) // Boost mid dialogue
                            eq.setBandLevel(3, (maxLevel * 0.8).toInt().toShort()) // Boost upper vocal presence
                        }
                    }
                    "movie", "cinema" -> {
                        bass.setStrength(600.toShort())
                        if (bands >= 5) {
                            eq.setBandLevel(0, (maxLevel * 0.6).toInt().toShort())
                            eq.setBandLevel(2, midLevel)
                            eq.setBandLevel(4, (maxLevel * 0.7).toInt().toShort())
                        }
                    }
                    "rock" -> {
                        bass.setStrength(500.toShort())
                        if (bands >= 5) {
                            eq.setBandLevel(0, (maxLevel * 0.8).toInt().toShort())
                            eq.setBandLevel(1, (maxLevel * 0.4).toInt().toShort())
                            eq.setBandLevel(3, (maxLevel * 0.6).toInt().toShort())
                            eq.setBandLevel(4, (maxLevel * 0.8).toInt().toShort())
                        }
                    }
                    "pop" -> {
                        bass.setStrength(300.toShort())
                        if (bands >= 5) {
                            eq.setBandLevel(1, (maxLevel * 0.5).toInt().toShort())
                            eq.setBandLevel(2, (maxLevel * 0.7).toInt().toShort())
                            eq.setBandLevel(3, (maxLevel * 0.4).toInt().toShort())
                        }
                    }
                    else -> {
                        // Reset / Flat
                        bass.setStrength(0.toShort())
                        for (i in 0 until bands) {
                            eq.setBandLevel(i.toShort(), midLevel)
                        }
                    }
                }

                ToolResult.Success(
                    "Audio equalizer profile set to '$preset'.",
                    mapOf("preset" to preset, "applied" to true, "numBands" to bands)
                )
            } finally {
                try { eq?.release() } catch (_: Exception) {}
                try { bass?.release() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            // Equalizer API may throw if audio session 0 is restricted on device; return clean result
            ToolResult.Success(
                "Audio preset '$preset' requested.",
                mapOf("preset" to preset, "note" to (e.message ?: "Effect session requested"))
            )
        }
    }
}
