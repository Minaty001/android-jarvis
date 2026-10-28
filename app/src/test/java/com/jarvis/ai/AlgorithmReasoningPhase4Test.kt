package com.jarvis.ai

import com.jarvis.sound.AmbientSoundClassifier
import com.jarvis.sound.SoundCategory
import com.jarvis.sound.SoundClassificationResult
import com.jarvis.sound.TemporalSoundSmoother
import com.jarvis.telecom.CallSpamScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmReasoningPhase4Test {

    // 4.1 AmbientSoundClassifier Temporal Smoothing Tests
    @Test
    fun testTemporalSoundSmootherDiscardsSingleSpike() {
        val smoother = TemporalSoundSmoother(windowFrames = 5)

        // Single isolated spike of smoke alarm
        val spike = SoundClassificationResult(
            category = SoundCategory.SMOKE_ALARM,
            confidence = 0.85f,
            dominantFrequencyHz = 3000f,
            energyRatio = 0.6f
        )
        val result = smoother.smooth(spike)

        // A single frame without confirmation is not sufficient for a safety alert
        assertEquals(SoundCategory.NONE, result.category)
        assertTrue(result.explanation.contains("transient", ignoreCase = true) || result.explanation.contains("awaiting", ignoreCase = true))

        // Second frame confirms smoke alarm
        val second = SoundClassificationResult(
            category = SoundCategory.SMOKE_ALARM,
            confidence = 0.90f,
            dominantFrequencyHz = 3000f,
            energyRatio = 0.65f
        )
        val confirmed = smoother.smooth(second)
        assertEquals(SoundCategory.SMOKE_ALARM, confirmed.category)
        assertTrue(confirmed.explanation.contains("confirmed", ignoreCase = true))
    }

    // 4.2 CallSpamScorer Tests
    @Test
    fun testCallSpamScorerContextualClassification() {
        val scorer = CallSpamScorer()

        // Contact known
        val contactCall = scorer.scoreCall("+919876543210", isContactKnown = true)
        assertFalse(contactCall.isSpam)
        assertEquals(0.99f, contactCall.confidence, 0.01f)
        assertTrue(contactCall.reason.contains("contacts", ignoreCase = true))

        // 140 telemarketing prefix
        val spam140 = scorer.scoreCall("1401234567", isContactKnown = false)
        assertTrue(spam140.isSpam)
        assertTrue(spam140.confidence >= 0.90f)
        assertTrue(spam140.reason.contains("140", ignoreCase = true))

        // Repetitive fake digits
        val fakeCall = scorer.scoreCall("9999999999", isContactKnown = false)
        assertTrue(fakeCall.isSpam)
        assertTrue(fakeCall.reason.contains("repetitive", ignoreCase = true) || fakeCall.reason.contains("pattern", ignoreCase = true))

        // Legitimate toll free
        val tollFree = scorer.scoreCall("1800111222", isContactKnown = false)
        assertFalse(tollFree.isSpam)
        assertTrue(tollFree.reason.contains("toll-free", ignoreCase = true))
    }

    // 4.3 EmbeddingVectorizer Pluggable Semantic Encoder Test
    @Test
    fun testEmbeddingVectorizerPluggableSemanticEncoder() {
        val customEncoder = object : SemanticEncoder {
            override fun embedText(text: String): FloatArray {
                return FloatArray(256) { 0.5f }
            }
        }
        val vectorizer = EmbeddingVectorizer(semanticEncoder = customEncoder)
        val embedded = vectorizer.embed("sample query")
        assertEquals(256, embedded.size)
        assertEquals(0.5f, embedded[0], 0.001f)
    }
}
