package com.jarvis.wakeword

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class OpenWakeWordEnergyGateTest {

    @Test
    fun testRmsCalculationOnQuietAudioIsBelowFloor() {
        val quietAudio = ShortArray(1280) { (it % 10).toShort() } // Very quiet pcm
        var sum = 0.0
        quietAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / quietAudio.size)
        assertTrue("Quiet audio RMS ($rms) must be below 0.005 floor", rms < 0.005)
    }

    @Test
    fun testRmsCalculationOnSpeechAudioExceedsFloor() {
        val speechAudio = ShortArray(1280) { 1500.toShort() } // ~0.045 RMS
        var sum = 0.0
        speechAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / speechAudio.size)
        assertTrue("Speech audio RMS ($rms) must exceed 0.005 floor", rms > 0.005)
    }
}
