package com.jarvis.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeterministicSentimentAnalyzerTest {

    private lateinit var analyzer: DeterministicSentimentAnalyzer

    @Before
    fun setUp() {
        analyzer = DeterministicSentimentAnalyzer()
    }

    @Test
    fun testExplicitEmotionalSupportRequest() {
        val analysis = analyzer.analyze("I'm feeling really overwhelmed and need help")
        assertEquals(Sentiment.NEGATIVE, analysis.sentiment)
        assertTrue(analysis.confidence >= 0.70f)
        assertTrue(analysis.supportRecommended)
        assertFalse(analysis.humorRecommended)
    }

    @Test
    fun testExplicitGratitudeAndPositiveNews() {
        val analysis = analyzer.analyze("Thank you so much Jarvis, that worked wonderfully!")
        assertEquals(Sentiment.POSITIVE, analysis.sentiment)
        assertTrue(analysis.confidence >= 0.70f)
        assertFalse(analysis.supportRecommended)
    }

    @Test
    fun testExplicitJokeRequest() {
        val analysis = analyzer.analyze("Jarvis, tell me a funny joke")
        assertTrue(analysis.humorRecommended)
        assertFalse(analysis.seriousContext)
    }

    @Test
    fun testSeriousContextSuppressesHumor() {
        val analysis = analyzer.analyze("My friend was in a car crash at the hospital")
        assertTrue(analysis.seriousContext)
        assertFalse(analysis.humorRecommended)
    }

    @Test
    fun testNeutralTaskQuery() {
        val analysis = analyzer.analyze("What is the battery level and screen brightness?")
        assertEquals(Sentiment.NEUTRAL, analysis.sentiment)
        assertFalse(analysis.supportRecommended)
        assertFalse(analysis.humorRecommended)
        assertFalse(analysis.seriousContext)
    }

    @Test
    fun testAmbiguousOrFictionalInputReturnsLowConfidenceOrUnknown() {
        val analysis = analyzer.analyze("The movie had a tragic ending where the hero cried")
        // Not a direct emotional disclosure by the user; confidence in personal sentiment must be low
        assertTrue(analysis.confidence < 0.65f || analysis.sentiment == Sentiment.UNKNOWN)
        assertFalse(analysis.supportRecommended)
    }

    @Test
    fun testSentimentNeverInfersCriticalEmergency() {
        val analysis = analyzer.analyze("This error message is terrible and frustrating")
        assertEquals(Sentiment.NEGATIVE, analysis.sentiment)
        // Sentiment analyzer MUST NOT emit critical emergency flags; only validated system health monitors can do that
        assertFalse(analysis.isCriticalRisk)
    }
}
