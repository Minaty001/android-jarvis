package com.jarvis.wakeword

/** Shared PCM and MFCC contract used by offline training and Android inference. */
data class KwsFeatureSpec(
    val sampleRate: Int = 16_000,
    val windowSamples: Int = 16_000,
    val frameLengthSamples: Int = 400,
    val frameStrideSamples: Int = 320,
    val fftSize: Int = 512,
    val numMelFilters: Int = 40,
    val numMfcc: Int = 10,
    val preEmphasis: Float = 0.97f
) {
    val numFrames: Int = (windowSamples - frameLengthSamples) / frameStrideSamples + 1

    init {
        require(sampleRate > 0 && windowSamples >= frameLengthSamples)
        require(frameLengthSamples > 0 && frameStrideSamples > 0)
        require(fftSize >= frameLengthSamples && fftSize.countOneBits() == 1)
        require(numMelFilters > 0 && numMfcc in 1..numMelFilters)
    }

    companion object {
        val DEFAULT = KwsFeatureSpec()
    }
}
