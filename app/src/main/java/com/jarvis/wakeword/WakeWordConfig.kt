package com.jarvis.wakeword

/** Runtime buffering and energy-gate settings. The model manifest owns the KWS threshold. */
data class WakeWordConfig(
    val confirmationWindowCount: Int = 4,
    val requiredPositiveWindows: Int = 2,
    /** Normalized PCM RMS floor; calibrated to 0.00150 to reject ambient indoor noise (0.0004-0.0010 RMS). */
    val minEnergyRms: Double = 0.00150,
    /** Consecutive low-energy 80ms frames permitted before accumulated temporal confirmation history is reset (~240ms decay). */
    val silenceFramesBeforeReset: Int = 3,
    val inferenceIntervalSamples: Int = 1600,
    val detectionCooldownMs: Long = 1500L
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
                    confirmationWindowCount = 4,
                    requiredPositiveWindows = 2,
                    minEnergyRms = 0.00080,
                    silenceFramesBeforeReset = 3
                )
                "LOW" -> WakeWordConfig(
                    confirmationWindowCount = 5,
                    requiredPositiveWindows = 3,
                    minEnergyRms = 0.00300,
                    silenceFramesBeforeReset = 3
                )
                else -> WakeWordConfig( // BALANCED / MEDIUM / DEFAULT
                    confirmationWindowCount = 4,
                    requiredPositiveWindows = 2,
                    minEnergyRms = 0.00150,
                    silenceFramesBeforeReset = 3
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
