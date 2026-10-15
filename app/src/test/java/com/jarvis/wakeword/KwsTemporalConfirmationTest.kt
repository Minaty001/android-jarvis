package com.jarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KwsTemporalConfirmationTest {

    @Test
    fun testTrailingSilenceDoesNotDiscardConfirmation() {
        val confirmation = KwsTemporalConfirmation(windowCount = 3, requiredPositiveWindows = 2)
        // Utterance during first 2 frames
        assertEquals(0f, confirmation.add(1.1f, 1.0f), 0f)
        // Second frame completes required positive windows
        val score = confirmation.add(1.2f, 1.0f)
        assertTrue("Must confirm on second positive hit without waiting for empty 3rd window", score >= 1.0f)
        assertEquals(1.2f, score, 0.001f)
    }

    @Test
    fun testTrailingSilenceScoreReturnsMaxScoreNotSilence() {
        val confirmation = KwsTemporalConfirmation(windowCount = 3, requiredPositiveWindows = 2)
        assertEquals(0f, confirmation.add(1.1f, 1.0f), 0f)
        assertEquals(0f, confirmation.add(0.3f, 1.0f), 0f)
        // 3rd frame has silence, but previous positive + current positive
        val score = confirmation.add(1.05f, 1.0f)
        assertTrue("Must return max positive score", score >= 1.05f)
    }

    @Test
    fun testHighConfidenceSpikeTriggersImmediately() {
        val confirmation = KwsTemporalConfirmation(windowCount = 3, requiredPositiveWindows = 2)
        val score = confirmation.add(1.30f, 1.0f)
        assertEquals("Spike exceeding 1.25x threshold must trigger immediately", 1.30f, score, 0.001f)
    }

    @Test
    fun testSingleWindowConfiguration() {
        val confirmation = KwsTemporalConfirmation(windowCount = 1, requiredPositiveWindows = 1)
        val score = confirmation.add(1.02f, 1.0f)
        assertEquals(1.02f, score, 0.001f)
    }
}
