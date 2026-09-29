package com.jarvis.wakeword

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MfccParityTest {
    @Test
    fun androidMfccMatchesPythonGoldenForDeterministicPcm() {
        val json = javaClass.classLoader!!.getResourceAsStream("kws_mfcc_golden.json")!!
            .bufferedReader().use { it.readText() }
        val expected = JSONObject(json).getJSONArray("features")
        val audio = ShortArray(16_000) { index ->
            val sample = 0.25 * sin(2.0 * PI * 440.0 * index / 16_000) +
                0.12 * sin(2.0 * PI * 1_000.0 * index / 16_000)
            (sample * 32_768.0).toInt().toShort()
        }
        val actual = Array(49) { FloatArray(10) }
        assertEquals(49, MfccExtractor().extractMfcc(audio, actual))
        for (frame in 0 until 49) {
            val expectedFrame = expected.getJSONArray(frame)
            for (coefficient in 0 until 10) {
                assertEquals("MFCC[$frame,$coefficient]", expectedFrame.getDouble(coefficient), actual[frame][coefficient].toDouble(), 0.01)
            }
        }
    }
}
