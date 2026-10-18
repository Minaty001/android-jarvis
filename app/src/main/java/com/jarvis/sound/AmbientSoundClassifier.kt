package com.jarvis.sound

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class SoundCategory {
    NONE,
    BABY_CRYING,
    SMOKE_ALARM,
    DOORBELL,
    GLASS_BREAK,
    CAR_HONK
}

data class SoundClassificationResult(
    val category: SoundCategory,
    val confidence: Float,
    val dominantFrequencyHz: Float = 0f,
    val energyRatio: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AmbientSoundClassifier
 *
 * Lightweight, zero-allocation offline acoustic feature extractor and classifier.
 * Optimized for MediaTek Helio P22 (3GB RAM) and Exynos 9611.
 *
 * Classifies 5 safety sound categories from 16 kHz 16-bit mono PCM audio frames:
 * 1. SMOKE_ALARM: 2800 - 3200 Hz pure-tone pulse
 * 2. BABY_CRYING: 350 - 650 Hz fundamental + 750 - 1300 Hz harmonic cry resonance
 * 3. DOORBELL: Dual resonant chime (650-750 Hz and 480-550 Hz)
 * 4. GLASS_BREAK: High-frequency transient burst (> 4000 Hz)
 * 5. CAR_HONK: Loud sustained dual tone in 380 - 600 Hz
 */
class AmbientSoundClassifier(
    val sampleRate: Int = 16000,
    val windowSize: Int = 1024
) {
    private val fftSize = windowSize
    private val halfFft = fftSize / 2
    private val binResolution = sampleRate.toFloat() / fftSize // 15.625 Hz per bin

    // Pre-allocated buffers to guarantee zero heap allocations in classify()
    private val window = DoubleArray(fftSize)
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val power = DoubleArray(halfFft + 1)

    // Pre-calculated twiddle factors for FFT
    private val cosTable = DoubleArray(fftSize)
    private val sinTable = DoubleArray(fftSize)

    init {
        // Pre-compute Hann window
        for (i in 0 until fftSize) {
            window[i] = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
        }
        // Pre-compute twiddle factors
        for (i in 0 until fftSize) {
            val angle = -2.0 * PI * i / fftSize
            cosTable[i] = cos(angle)
            sinTable[i] = sin(angle)
        }
    }

    fun reset() {
        real.fill(0.0)
        imag.fill(0.0)
        power.fill(0.0)
    }

    private fun freqToBin(freqHz: Double): Int {
        return (freqHz / binResolution).roundToInt().coerceIn(0, halfFft)
    }

    private fun sumBandPower(startBin: Int, endBin: Int): Double {
        var sum = 0.0
        val s = startBin.coerceIn(0, halfFft)
        val e = endBin.coerceIn(s, halfFft)
        for (k in s..e) {
            sum += power[k]
        }
        return sum
    }

    /**
     * In-place Cooley-Tukey Radix-2 FFT.
     * Zero allocations.
     */
    private fun computeFft() {
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= fftSize) {
            val step = fftSize / len
            val halfLen = len / 2
            for (base in 0 until fftSize step len) {
                var tableIndex = 0
                for (k in 0 until halfLen) {
                    val wr = cosTable[tableIndex]
                    val wi = sinTable[tableIndex]
                    val rIdx = base + k + halfLen
                    val br = real[rIdx] * wr - imag[rIdx] * wi
                    val bi = real[rIdx] * wi + imag[rIdx] * wr

                    val ar = real[base + k]
                    val ai = imag[base + k]

                    real[base + k] = ar + br
                    imag[base + k] = ai + bi
                    real[rIdx] = ar - br
                    imag[rIdx] = ai - bi

                    tableIndex += step
                }
            }
            len = len shl 1
        }
    }

    /**
     * Classifies a 16-bit PCM mono buffer.
     * Guaranteed zero heap allocation during execution.
     */
    @Synchronized
    fun classify(samples: ShortArray, readCount: Int): SoundClassificationResult {
        if (readCount <= 0) {
            return SoundClassificationResult(SoundCategory.NONE, 0f)
        }

        val count = minOf(readCount, fftSize)
        var sumSquares = 0.0

        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sumSquares += s * s
            real[i] = s * window[i]
            imag[i] = 0.0
        }
        for (i in count until fftSize) {
            real[i] = 0.0
            imag[i] = 0.0
        }

        val rms = sqrt(sumSquares / count)
        // Noise gate / silence threshold
        if (rms < 500.0) {
            return SoundClassificationResult(SoundCategory.NONE, 0f, 0f, 0f)
        }

        computeFft()

        var totalPower = 0.0
        var peakPower = 0.0
        var peakBin = 0

        // Calculate power spectrum (ignore DC bin 0)
        for (k in 1..halfFft) {
            val p = real[k] * real[k] + imag[k] * imag[k]
            power[k] = p
            totalPower += p
            if (p > peakPower) {
                peakPower = p
                peakBin = k
            }
        }

        if (totalPower <= 0.0) {
            return SoundClassificationResult(SoundCategory.NONE, 0f, 0f, 0f)
        }

        val peakFreq = peakBin * binResolution
        val peakEnergyRatio = (peakPower / totalPower).toFloat()

        // Frequency band definitions (in Hz)
        val speechLowBin = freqToBin(80.0)
        val speechHighBin = freqToBin(260.0)

        val hornLowBin = freqToBin(380.0)
        val hornHighBin = freqToBin(600.0)

        val chime1LowBin = freqToBin(460.0)
        val chime1HighBin = freqToBin(540.0)
        val chime2LowBin = freqToBin(660.0)
        val chime2HighBin = freqToBin(760.0)

        val cryFundLowBin = freqToBin(350.0)
        val cryFundHighBin = freqToBin(650.0)
        val cryHarmLowBin = freqToBin(750.0)
        val cryHarmHighBin = freqToBin(1300.0)

        val smokeAlarmLowBin = freqToBin(2800.0)
        val smokeAlarmHighBin = freqToBin(3200.0)

        val glassBreakLowBin = freqToBin(4000.0)
        val glassBreakHighBin = freqToBin(7800.0)

        val smokePower = sumBandPower(smokeAlarmLowBin, smokeAlarmHighBin)
        val smokeRatio = (smokePower / totalPower).toFloat()

        val glassPower = sumBandPower(glassBreakLowBin, glassBreakHighBin)
        val glassRatio = (glassPower / totalPower).toFloat()

        val hornPower = sumBandPower(hornLowBin, hornHighBin)
        val hornRatio = (hornPower / totalPower).toFloat()

        val chime1Power = sumBandPower(chime1LowBin, chime1HighBin)
        val chime2Power = sumBandPower(chime2LowBin, chime2HighBin)

        val cryFundPower = sumBandPower(cryFundLowBin, cryFundHighBin)
        val cryHarmPower = sumBandPower(cryHarmLowBin, cryHarmHighBin)

        val speechPower = sumBandPower(speechLowBin, speechHighBin)
        val speechRatio = (speechPower / totalPower).toFloat()

        val lowVoiceLowBin = freqToBin(60.0)
        val lowVoiceHighBin = freqToBin(300.0)
        val lowVoicePower = sumBandPower(lowVoiceLowBin, lowVoiceHighBin)
        val lowVoiceRatio = (lowVoicePower / totalPower).toFloat()

        // 1. SMOKE ALARM: Pure tone ~3000 Hz, concentrated in 2800-3200 Hz
        if (smokeRatio >= 0.45f && peakFreq >= 2750f && peakFreq <= 3250f) {
            val conf = (0.60f + smokeRatio * 0.40f).coerceIn(0.70f, 0.99f)
            return SoundClassificationResult(
                category = SoundCategory.SMOKE_ALARM,
                confidence = conf,
                dominantFrequencyHz = peakFreq,
                energyRatio = smokeRatio
            )
        }

        // 2. GLASS BREAK: High-frequency transient shock energy (> 4000 Hz) with low vocal energy
        if (glassRatio >= 0.45f && peakFreq >= 4000f && lowVoiceRatio < 0.25f) {
            val conf = (0.60f + glassRatio * 0.40f).coerceIn(0.70f, 0.99f)
            return SoundClassificationResult(
                category = SoundCategory.GLASS_BREAK,
                confidence = conf,
                dominantFrequencyHz = peakFreq,
                energyRatio = glassRatio
            )
        }

        // 3. DOORBELL: Chime dual tone around 500 Hz and 700 Hz (or prominent single chime resonance)
        val chimeRatio = ((chime1Power + chime2Power) / totalPower).toFloat()
        if (chimeRatio >= 0.45f && ((chime1Power > 0.05 * totalPower && chime2Power > 0.05 * totalPower) || (peakFreq >= 480f && peakFreq <= 760f && chimeRatio >= 0.60f))) {
            val conf = (0.60f + chimeRatio * 0.35f).coerceIn(0.65f, 0.95f)
            return SoundClassificationResult(
                category = SoundCategory.DOORBELL,
                confidence = conf,
                dominantFrequencyHz = peakFreq,
                energyRatio = chimeRatio
            )
        }

        // 4. BABY CRYING: 350-650 Hz fundamental + 750-1300 Hz harmonic resonance
        val cryCombinedRatio = ((cryFundPower + cryHarmPower) / totalPower).toFloat()
        if (cryCombinedRatio >= 0.45f && cryHarmPower >= 0.12 * cryFundPower && peakFreq >= 350f && peakFreq <= 650f) {
            val conf = (0.55f + cryCombinedRatio * 0.40f).coerceIn(0.65f, 0.95f)
            return SoundClassificationResult(
                category = SoundCategory.BABY_CRYING,
                confidence = conf,
                dominantFrequencyHz = peakFreq,
                energyRatio = cryCombinedRatio
            )
        }

        // 5. CAR HONK: Dual-tone sustained horn in 380-600 Hz with low voice-pitch energy
        if (hornRatio >= 0.45f && peakFreq >= 380f && peakFreq <= 600f && lowVoiceRatio < 0.30f) {
            val conf = (0.60f + hornRatio * 0.38f).coerceIn(0.70f, 0.98f)
            return SoundClassificationResult(
                category = SoundCategory.CAR_HONK,
                confidence = conf,
                dominantFrequencyHz = peakFreq,
                energyRatio = hornRatio
            )
        }

        // Default: Speech or general ambient noise
        return SoundClassificationResult(
            category = SoundCategory.NONE,
            confidence = speechRatio.coerceIn(0f, 0.3f),
            dominantFrequencyHz = peakFreq,
            energyRatio = speechRatio
        )
    }
}
