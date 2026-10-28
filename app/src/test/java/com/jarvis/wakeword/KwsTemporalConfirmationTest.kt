package com.jarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KwsTemporalConfirmationTest {

    @Test
    fun testTrailingSilenceDoesNotDiscardConfirmation() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2)
        // First frame
        assertEquals(0f, confirmation.add(1.05f, 1.0f), 0f)
        // Second frame completes required positive windows
        val score = confirmation.add(1.08f, 1.0f)
        assertTrue("Must confirm on second positive hit without waiting for empty windows", score >= 1.0f)
        assertEquals(1.08f, score, 0.001f)
    }

    @Test
    fun testTrailingSilenceScoreReturnsMaxScoreNotSilence() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2)
        assertEquals(0f, confirmation.add(1.10f, 1.0f), 0f)
        assertEquals(0f, confirmation.add(0.3f, 1.0f), 0f)
        // 3rd frame has silence, but previous positive + current positive
        val score = confirmation.add(1.05f, 1.0f)
        assertTrue("Must return max positive score", score >= 1.10f)
    }

    @Test
    fun testSingleFrameSpikeDoesNotBypassMultiFrameConfirmation() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2)
        // High spike exceeding threshold by 1.5x on a single 80ms frame must not trigger
        val spikeScore = confirmation.add(1.50f, 1.0f)
        assertEquals("Single-frame spike must not trigger immediate wake", 0f, spikeScore, 0.001f)

        // Second positive frame within 4-frame window completes confirmation
        val confirmedScore = confirmation.add(1.05f, 1.0f)
        assertTrue("Second positive window must confirm wake", confirmedScore >= 1.0f)
        assertEquals(1.50f, confirmedScore, 0.001f)
    }

    @Test
    fun testFourWindowConfirmationWithInterveningLowFrames() {
        val confirmation = KwsTemporalConfirmation(windowCount = 4, requiredPositiveWindows = 2)
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
