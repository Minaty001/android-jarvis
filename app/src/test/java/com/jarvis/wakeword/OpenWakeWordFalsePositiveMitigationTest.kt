package com.jarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenWakeWordFalsePositiveMitigationTest {

    @Test
    fun testIndependentConfirmationsPreventCrossPhraseContamination() {
        val config = WakeWordConfig(confirmationWindowCount = 2, requiredPositiveWindows = 2)

        // Head A: Hey Jarvis (threshold 0.75)
        val confirmationHeyJarvis = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)
        // Head B: Jarvis (threshold 0.80)
        val confirmationJarvis = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)

        // Frame 1: Ambient speech/noise produces false spike on "Hey Jarvis" (score 0.78 / 0.75 = 1.04 ratio)
        // while "Jarvis" is low (score 0.20 / 0.80 = 0.25 ratio)
        val confirmedA1 = confirmationHeyJarvis.add(0.78f / 0.75f, 1.0f)
        val confirmedB1 = confirmationJarvis.add(0.20f / 0.80f, 1.0f)
        assertEquals("Frame 1: Hey Jarvis must not trigger on single spike", 0f, confirmedA1, 0.001f)
        assertEquals("Frame 1: Jarvis must not trigger on low ratio", 0f, confirmedB1, 0.001f)

        // Frame 2: Room sound produces false spike on "Jarvis" (score 0.82 / 0.80 = 1.025 ratio)
        // while "Hey Jarvis" drops (score 0.15 / 0.75 = 0.20 ratio)
        val confirmedA2 = confirmationHeyJarvis.add(0.15f / 0.75f, 1.0f)
        val confirmedB2 = confirmationJarvis.add(0.82f / 0.80f, 1.0f)

        // In independent confirmation architecture:
        // Neither phrase had 2 positive windows, so NEITHER phrase triggers!
        assertEquals("Frame 2: Hey Jarvis dropped so must not trigger", 0f, confirmedA2, 0.001f)
        assertEquals("Frame 2: Jarvis has only 1 positive window so must not trigger", 0f, confirmedB2, 0.001f)

        // Frame 3: Another disparate frame must also not trigger
        val confirmedA3 = confirmationHeyJarvis.add(0.10f / 0.75f, 1.0f)
        val confirmedB3 = confirmationJarvis.add(0.10f / 0.80f, 1.0f)
        assertEquals(0f, confirmedA3, 0.001f)
        assertEquals(0f, confirmedB3, 0.001f)
    }

    @Test
    fun testTrueUtteranceTriggersWhenSamePhraseHasConsecutiveHits() {
        val config = WakeWordConfig(confirmationWindowCount = 2, requiredPositiveWindows = 2)
        val confirmationHeyJarvis = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)

        // Frame 1: Intentional "Hey Jarvis" onset (score 0.88 / 0.75 = 1.17 ratio)
        val score1 = confirmationHeyJarvis.add(0.88f / 0.75f, 1.0f)
        assertEquals(0f, score1, 0.001f)

        // Frame 2: Intentional "Hey Jarvis" peak (score 0.94 / 0.75 = 1.25 ratio)
        val score2 = confirmationHeyJarvis.add(0.94f / 0.75f, 1.0f)
        assertTrue("Consecutive positive hits for same wake phrase must confirm", score2 >= 1.0f)
        assertEquals(1.253f, score2, 0.01f)
    }

    @Test
    fun testTwoWindowConfigurationRejectsSingleFrameNoiseSpike() {
        val config = WakeWordConfig(confirmationWindowCount = 2, requiredPositiveWindows = 2)
        val confirmation = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)

        // Frame 1: momentary transient noise spike (door click / table bump)
        val hit1 = confirmation.add(1.30f, 1.0f)
        assertEquals("Frame 1 spike must not trigger", 0f, hit1, 0.001f)

        // Frame 2: noise gone, returns to ambient
        val hit2 = confirmation.add(0.25f, 1.0f)
        assertEquals("Frame 2 ambient must not trigger", 0f, hit2, 0.001f)

        // Frame 3: another isolated bump
        val hit3 = confirmation.add(1.10f, 1.0f)
        assertEquals("Frame 3 isolated spike must not trigger because frame 2 was negative", 0f, hit3, 0.001f)
    }

    @Test
    fun testSilenceResetFlushesConfirmationState() {
        val config = WakeWordConfig(confirmationWindowCount = 2, requiredPositiveWindows = 2)
        val confirmation = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)

        // Positive hit 1
        confirmation.add(1.20f, 1.0f)
        // Reset due to 2 silence frames
        confirmation.reset()

        // Next hit should be treated as hit 1, NOT hit 2
        val confirmed = confirmation.add(1.15f, 1.0f)
        assertEquals("Must require 2 new hits after reset", 0f, confirmed, 0.001f)
    }
}
