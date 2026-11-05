package com.jarvis.wakeword

/** Runtime buffering and energy-gate settings. The model manifest owns the KWS threshold. */
data class WakeWordConfig(
    val confirmationWindowCount: Int = 2,
    val requiredPositiveWindows: Int = 2,
    /** Normalized PCM RMS floor; calibrated to 0.00200 to reject ambient indoor noise (0.0004-0.0012 RMS). */
    val minEnergyRms: Double = 0.00200,
    /** Consecutive low-energy 80ms frames permitted before accumulated temporal confirmation history is reset (~160ms decay). */
    val silenceFramesBeforeReset: Int = 2,
    val inferenceIntervalSamples: Int = 1600,
    val detectionCooldownMs: Long = 2000L
) {
    init {
        require(confirmationWindowCount in 1..10)
        require(requiredPositiveWindows in 1..confirmationWindowCount)
        require(minEnergyRms >= 0.0)
        require(silenceFramesBeforeReset in 1..20)
        require(detectionCooldownMs >= 0L)
    }

    companion object {
        fun fromSensitivity(sensitivity: String, isLowEnd: Boolean = false): WakeWordConfig {
            val baseConfig = when (sensitivity.trim().uppercase()) {
                "HIGH" -> WakeWordConfig(
                    confirmationWindowCount = 2,
                    requiredPositiveWindows = 2,
                    minEnergyRms = 0.00120,
                    silenceFramesBeforeReset = 2
                )
                "LOW" -> WakeWordConfig(
                    confirmationWindowCount = 3,
                    requiredPositiveWindows = 3,
                    minEnergyRms = 0.00350,
                    silenceFramesBeforeReset = 2
                )
                else -> WakeWordConfig( // BALANCED / MEDIUM / DEFAULT
                    confirmationWindowCount = 2,
                    requiredPositiveWindows = 2,
                    minEnergyRms = 0.00200,
                    silenceFramesBeforeReset = 2
                )
            }
            return if (isLowEnd) {
                baseConfig.copy(minEnergyRms = baseConfig.minEnergyRms * 0.85)
            } else {
                baseConfig
            }
        }
    }
}
