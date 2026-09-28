package com.jarvis.wakeword

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.sin

class DtwMatcherTest {

    private lateinit var tempDir: File
    private lateinit var store: WakeWordTemplateStore
    private lateinit var matcher: DtwMatcher

    @Before
    fun setUp() {
        tempDir = File.createTempFile("test_dtw_templates_", "").apply {
            delete()
            mkdirs()
        }
        store = WakeWordTemplateStore(customDir = tempDir)
        store.clearTemplates()
        matcher = DtwMatcher(templateStore = store, threshold = DtwMatcher.DEFAULT_THRESHOLD)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testEuclideanDistance() {
        val v1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val v2 = floatArrayOf(4.0f, 6.0f, 8.0f)

        // sqrt((3^2) + (4^2) + (5^2)) = sqrt(9 + 16 + 25) = sqrt(50) ~= 7.071
        val dist = matcher.euclideanDistance(v1, v2)
        assertEquals(7.071f, dist, 0.01f)
    }

    @Test
    fun testDtwIdenticalSequencesZeroDistance() {
        val seq = arrayOf(
            floatArrayOf(1.0f, 2.0f, 3.0f),
            floatArrayOf(4.0f, 5.0f, 6.0f),
            floatArrayOf(7.0f, 8.0f, 9.0f)
        )

        val dist = matcher.calculateDtwDistance(seq, seq)
        assertEquals(0.0f, dist, 0.001f)
    }

    @Test
    fun testEvaluateAudioChunkMatchingIdenticalSignal() {
        // Create 2 seconds of 16 kHz audio sine wave
        val sampleRate = 16000
        val pcm = ShortArray(sampleRate * 2) { idx ->
            (sin(2.0 * Math.PI * 440.0 * idx / sampleRate) * 10000).toInt().toShort()
        }

        // Save as template 1
        store.saveTemplate(1, pcm)

        // Evaluate live chunk (1.5s of identical sine wave)
        val liveChunk = pcm.copyOf(24000)
        val result = matcher.evaluateAudioChunk(liveChunk)

        assertNotNull(result)
        assertTrue("Matching identical signal should pass threshold", result.isMatch)
        assertEquals(1, result.bestMatchedTemplateIndex)
        assertTrue("Score should be high (> 0.85)", result.similarityScore > 0.85f)
    }

    @Test
    fun testThresholdStrictnessAdjustment() {
        matcher.threshold = DtwMatcher.STRICT_THRESHOLD
        assertEquals(0.88f, matcher.threshold, 0.001f)

        matcher.threshold = DtwMatcher.DEFAULT_THRESHOLD
        assertEquals(0.80f, matcher.threshold, 0.001f)
    }
}
