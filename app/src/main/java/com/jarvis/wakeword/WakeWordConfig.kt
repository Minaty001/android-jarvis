package com.jarvis.wakeword

/** Runtime buffering and energy-gate settings. The model manifest owns the KWS threshold. */
data class WakeWordConfig(
    val confirmationWindowCount: Int = 3,
    val requiredPositiveWindows: Int = 2,
    /** Normalized PCM RMS floor; this is independent of the model's score threshold. */
    val minEnergyRms: Double = 0.005,
    val inferenceIntervalSamples: Int = 1600,
    val detectionCooldownMs: Long = 1200L
) {
    init {
        require(confirmationWindowCount in 1..10)
        require(requiredPositiveWindows in 1..confirmationWindowCount)
        require(minEnergyRms >= 0.0)
        require(detectionCooldownMs >= 0L)
    }
}
