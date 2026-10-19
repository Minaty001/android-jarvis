package com.jarvis.emotion

import java.util.Locale

data class SentimentAnalysisResult(
    val sentiment: Sentiment,
    val confidence: Float,
    val supportRecommended: Boolean,
    val humorRecommended: Boolean,
    val seriousContext: Boolean,
    val isCriticalRisk: Boolean = false
)

/**
 * Pure, deterministic rules-based sentiment analyzer.
 * Identifies conversational intent and tone without neural network inference,
 * external network requests, or persistent allocations.
 */
class DeterministicSentimentAnalyzer {

    private val firstPersonTokens = listOf(
        "i'm", "im", "i am", "i feel", "i'm feeling", "i need", "i am feeling",
        "me", "my", "myself"
    )

    private val seriousKeywords = listOf(
        "car crash", "hospital", "died", "death", "passed away", "funeral",
        "emergency", "ambulance", "injury", "bleeding", "cancer", "suicide", "grief",
        "critical condition", "intensive care"
    )

    private val directDistressKeywords = listOf(
        "overwhelmed", "depressed", "anxious", "panic attack",
        "struggling", "can't take this", "cant take this", "need help", "heartbroken",
        "feeling down", "feeling sad", "hopeless", "devastated", "in distress"
    )

    private val gratitudeKeywords = listOf(
        "thank you", "thanks", "worked wonderfully", "great job", "awesome",
        "excellent", "well done", "appreciate it", "you're the best", "you rock",
        "wonderful job", "perfect"
    )

    private val negativeKeywords = listOf(
        "terrible", "frustrating", "awful", "horrible", "annoying", "hate",
        "broken", "useless", "bad", "disaster", "fail", "failed"
    )

    private val jokeKeywords = listOf(
        "tell me a joke", "tell a joke", "funny joke", "make me laugh",
        "say something funny", "tell me something funny", "crack a joke",
        "tell joke", "a joke"
    )

    private val playfulKeywords = listOf(
        "haha", "hehe", "lol", "kidding", "just teasing", "hilarious", "you funny"
    )

    fun analyze(text: String): SentimentAnalysisResult {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) {
            return SentimentAnalysisResult(
                sentiment = Sentiment.NEUTRAL,
                confidence = 0.0f,
                supportRecommended = false,
                humorRecommended = false,
                seriousContext = false,
                isCriticalRisk = false
            )
        }

        // 1. Check for serious context
        val seriousMatch = seriousKeywords.any { lower.contains(it) }

        // 2. Check for humor/jokes
        val jokeRequest = jokeKeywords.any { lower.contains(it) }
        val playfulMatch = playfulKeywords.any { lower.contains(it) }
        val humorRecommended = (jokeRequest || playfulMatch) && !seriousMatch

        // 3. Check for 1st-person distress vs 3rd-person/story
        val hasFirstPerson = firstPersonTokens.any { token ->
            lower.startsWith("$token ") || lower.contains(" $token ") || lower.contains(" $token'")
        }
        val distressMatch = directDistressKeywords.any { lower.contains(it) }

        // If distress keywords are present, determine if it is directly user-focused
        val supportRecommended = !seriousMatch && distressMatch && hasFirstPerson

        // 4. Sentiment determination
        val hasGratitude = gratitudeKeywords.any { lower.contains(it) }
        val hasNegative = negativeKeywords.any { lower.contains(it) }

        val sentiment: Sentiment
        val confidence: Float

        when {
            supportRecommended -> {
                sentiment = Sentiment.NEGATIVE
                confidence = 0.85f
            }
            seriousMatch -> {
                sentiment = Sentiment.NEGATIVE
                confidence = 0.80f
            }
            hasGratitude -> {
                sentiment = Sentiment.POSITIVE
                confidence = 0.80f
            }
            hasNegative -> {
                sentiment = Sentiment.NEGATIVE
                // Check if user is referencing an external object (e.g. error message)
                confidence = if (hasFirstPerson) 0.75f else 0.70f
            }
            distressMatch && !hasFirstPerson -> {
                // Ambiguous or third-party context (e.g. movie, fictional hero)
                sentiment = Sentiment.UNKNOWN
                confidence = 0.50f
            }
            jokeRequest || playfulMatch -> {
                sentiment = Sentiment.POSITIVE
                confidence = 0.75f
            }
            else -> {
                // Check if it's a typical neutral command or query
                sentiment = Sentiment.NEUTRAL
                confidence = 0.50f
            }
        }

        return SentimentAnalysisResult(
            sentiment = sentiment,
            confidence = confidence,
            supportRecommended = supportRecommended,
            humorRecommended = humorRecommended,
            seriousContext = seriousMatch,
            isCriticalRisk = false // Sentiment NEVER claims verified critical system emergency
        )
    }
}
