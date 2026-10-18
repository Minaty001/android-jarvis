package com.jarvis.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AmbientSoundClassifierTest {

    private lateinit var classifier: AmbientSoundClassifier
    private val sampleRate = 16000
    private val windowSize = 1024

    @Before
    fun setUp() {
        classifier = AmbientSoundClassifier(sampleRate = sampleRate, windowSize = windowSize)
    }

    private fun generateSineWave(
        freqHz: Double,
        numSamples: Int = 1024,
        amplitude: Short = 18000
    ): ShortArray {
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * freqHz * i / sampleRate
            samples[i] = (amplitude * sin(angle)).toInt().toShort()
        }
        return samples
    }

    private fun generateDualTone(
        freq1: Double,
        freq2: Double,
        numSamples: Int = 1024,
        amplitude: Short = 15000
    ): ShortArray {
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val s1 = amplitude * 0.5 * sin(2.0 * PI * freq1 * i / sampleRate)
            val s2 = amplitude * 0.5 * sin(2.0 * PI * freq2 * i / sampleRate)
            samples[i] = (s1 + s2).toInt().toShort()
        }
        return samples
    }

    @Test
    fun `test silence returns NONE`() {
        val silence = ShortArray(1024) { 0 }
        val result = classifier.classify(silence, silence.size)
        assertEquals(SoundCategory.NONE, result.category)
        assertTrue(result.confidence < 0.3f)
    }

    @Test
    fun `test low frequency speech-like noise returns NONE`() {
        // 120 Hz fundamental (typical male voice) with low harmonics
        val speechLike = generateSineWave(120.0, 1024, amplitude = 8000)
        val result = classifier.classify(speechLike, speechLike.size)
        assertEquals(SoundCategory.NONE, result.category)
    }

    @Test
    fun `test smoke alarm 3000 Hz pure tone detects SMOKE_ALARM`() {
        // Smoke alarms emit standard ~3000 Hz pure-tone beep
        val smokeAlarm = generateSineWave(3000.0, 1024, amplitude = 22000)
        val result = classifier.classify(smokeAlarm, smokeAlarm.size)
        assertEquals(SoundCategory.SMOKE_ALARM, result.category)
        assertTrue("Expected confidence >= 0.70, was ${result.confidence}", result.confidence >= 0.70f)
        assertTrue(result.dominantFrequencyHz in 2800f..3200f)
    }

    @Test
    fun `test baby crying fundamental 450 Hz with harmonics detects BABY_CRYING`() {
        // Infant cry: fundamental ~450 Hz + 900 Hz harmonic
        val babyCry = generateDualTone(450.0, 900.0, 1024, amplitude = 18000)
        val result = classifier.classify(babyCry, babyCry.size)
        assertEquals(SoundCategory.BABY_CRYING, result.category)
        assertTrue("Expected confidence >= 0.65, was ${result.confidence}", result.confidence >= 0.65f)
        assertTrue(result.dominantFrequencyHz in 350f..650f)
    }

    @Test
    fun `test doorbell chime dual tone 700 Hz and 500 Hz detects DOORBELL`() {
        // Ding-dong chime transition / resonance
        val doorbell = generateDualTone(700.0, 500.0, 1024, amplitude = 18000)
        val result = classifier.classify(doorbell, doorbell.size)
        assertEquals(SoundCategory.DOORBELL, result.category)
        assertTrue("Expected confidence >= 0.65, was ${result.confidence}", result.confidence >= 0.65f)
    }

    @Test
    fun `test glass break high frequency burst 5000 Hz detects GLASS_BREAK`() {
        // High frequency transient shock wave (> 4000 Hz)
        val glassBreak = generateSineWave(5200.0, 1024, amplitude = 24000)
        val result = classifier.classify(glassBreak, glassBreak.size)
        assertEquals(SoundCategory.GLASS_BREAK, result.category)
        assertTrue("Expected confidence >= 0.70, was ${result.confidence}", result.confidence >= 0.70f)
        assertTrue(result.dominantFrequencyHz >= 4000f)
    }

    @Test
    fun `test car honk dual tone 440 Hz and 550 Hz detects CAR_HONK`() {
        // Dual horn chord (e.g. 440 Hz + 550 Hz)
        val carHonk = generateDualTone(440.0, 550.0, 1024, amplitude = 26000)
        val result = classifier.classify(carHonk, carHonk.size)
        assertEquals(SoundCategory.CAR_HONK, result.category)
        assertTrue("Expected confidence >= 0.70, was ${result.confidence}", result.confidence >= 0.70f)
    }

    @Test
    fun `test reset clears internal state`() {
        val smokeAlarm = generateSineWave(3000.0, 1024, amplitude = 22000)
        classifier.classify(smokeAlarm, smokeAlarm.size)
        classifier.reset()
        val silence = ShortArray(1024) { 0 }
        val result = classifier.classify(silence, silence.size)
        assertEquals(SoundCategory.NONE, result.category)
    }
}
