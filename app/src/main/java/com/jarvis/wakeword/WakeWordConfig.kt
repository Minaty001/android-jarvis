package com.jarvis.wakeword

/** Runtime buffering and energy-gate settings. The model manifest owns the KWS threshold. */
data class WakeWordConfig(
    val confirmationWindowCount: Int = 3,
    val requiredPositiveWindows: Int = 2,
    /** Normalized PCM RMS floor; calibrated to 0.0003 for mobile phone microphones in indoor acoustic environments. */
    val minEnergyRms: Double = 0.0003,
    /** Consecutive low-energy 80ms frames permitted before accumulated temporal confirmation history is reset (~400ms decay). */
    val silenceFramesBeforeReset: Int = 5,
    val inferenceIntervalSamples: Int = 1600,
    val detectionCooldownMs: Long = 1200L
) {
    init {
        require(confirmationWindowCount in 1..10)
        require(requiredPositiveWindows in 1..confirmationWindowCount)
        require(minEnergyRms >= 0.0)
        require(silenceFramesBeforeReset in 1..20)
        require(detectionCooldownMs >= 0L)
    }
}
