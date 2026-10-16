package com.jarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KwsTemporalConfirmationTest {

    @Test
    fun testTrailingSilenceDoesNotDiscardConfirmation() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2, spikeMultiplier = 1.15f)
        // First frame (below spike multiplier 1.15x)
        assertEquals(0f, confirmation.add(1.05f, 1.0f), 0f)
        // Second frame completes required positive windows
        val score = confirmation.add(1.08f, 1.0f)
        assertTrue("Must confirm on second positive hit without waiting for empty windows", score >= 1.0f)
        assertEquals(1.08f, score, 0.001f)
    }

    @Test
    fun testTrailingSilenceScoreReturnsMaxScoreNotSilence() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2, spikeMultiplier = 1.15f)
        assertEquals(0f, confirmation.add(1.10f, 1.0f), 0f)
        assertEquals(0f, confirmation.add(0.3f, 1.0f), 0f)
        // 3rd frame has silence, but previous positive + current positive
        val score = confirmation.add(1.05f, 1.0f)
        assertTrue("Must return max positive score", score >= 1.10f)
    }

    @Test
    fun testHighConfidenceSpikeTriggersImmediatelyAtPointFifteen() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2, spikeMultiplier = 1.15f)
        val nonSpike = confirmation.add(1.10f, 1.0f)
        assertEquals("Score 1.10x is below 1.15x spike multiplier, must not trigger on single frame", 0f, nonSpike, 0.001f)

        val spikeConfirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2, spikeMultiplier = 1.15f)
        val score = spikeConfirmation.add(1.16f, 1.0f)
        assertEquals("Spike exceeding 1.15x threshold must trigger immediately", 1.16f, score, 0.001f)
    }

    @Test
    fun testFourWindowConfirmationWithInterveningLowFrames() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2, spikeMultiplier = 1.15f)
        // Frame 1: positive
        assertEquals(0f, confirmation.add(1.02f, 1.0f), 0f)
        // Frame 2: dip
        assertEquals(0f, confirmation.add(0.40f, 1.0f), 0f)
        // Frame 3: dip
        assertEquals(0f, confirmation.add(0.35f, 1.0f), 0f)
        // Frame 4: second positive frame within 4-frame window
        val confirmed = confirmation.add(1.06f, 1.0f)
        assertTrue("Must confirm within 4-window history despite intervening dips", confirmed >= 1.0f)
        assertEquals(1.06f, confirmed, 0.001f)
    }

    @Test
    fun testSingleWindowConfiguration() {
        val confirmation = KwsTemporalConfirmation(windowCount = 1, requiredPositiveWindows = 1)
        val score = confirmation.add(1.02f, 1.0f)
        assertEquals(1.02f, score, 0.001f)
    }
}
