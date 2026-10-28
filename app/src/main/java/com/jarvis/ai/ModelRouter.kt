package com.jarvis.ai

enum class ModelTier { LOCAL, STANDARD, STRONG }

data class Route(
    val tier: ModelTier,
    val model: String,
    val privacySensitive: Boolean,
    val reason: String = "",
    val complexityScore: Float = 0.0f
) {
    val usesCloud: Boolean get() = !privacySensitive && tier != ModelTier.LOCAL
}

class ModelRouter(
    private val defaultModel: String = LlmConfig.DEFAULT_MODEL,
    private val strongModel: String = LlmConfig.STRONG_MODEL,
    private val strongThresholdChars: Int = 180
) {

    fun route(utterance: String, resolvedIntent: AssistantIntent? = null): Route {
        if (privacySensitive(utterance)) {
            return Route(
                tier = ModelTier.LOCAL,
                model = "",
                privacySensitive = true,
                reason = "Privacy-sensitive content detected in utterance",
                complexityScore = 0.0f
            )
        }
        
        // Explicit API Routing based on Plan
        if (resolvedIntent == AssistantIntent.SCREEN_VISION || resolvedIntent == AssistantIntent.CAMERA_VISION) {
            return Route(
                tier = ModelTier.STRONG,
                model = LlmConfig.GEMINI_DEFAULT_MODEL,
                privacySensitive = false,
                reason = "Vision intent requires multimodal model",
                complexityScore = 0.9f
            )
        }
        
        if (resolvedIntent == AssistantIntent.DEEP_RESEARCH) {
            return Route(
                tier = ModelTier.STRONG,
                model = LlmConfig.OPENROUTER_STRONG_MODEL,
                privacySensitive = false,
                reason = "Deep research intent requires strong reasoning model",
                complexityScore = 1.0f
            )
        }

        val (tier, score, reason) = evaluateComplexity(utterance)
        val model = if (tier == ModelTier.STRONG) strongModel else defaultModel
        return Route(
            tier = tier,
            model = model,
            privacySensitive = false,
            reason = reason,
            complexityScore = score
        )
    }

    fun privacySensitive(utterance: String): Boolean = com.jarvis.foundation.SensitiveTerms.contains(utterance)

    fun evaluateComplexity(utterance: String): Triple<ModelTier, Float, String> {
        val low = utterance.lowercase()
        val multiStep = MULTI_STEP_MARKERS.any { low.contains(it) }
        val needsPlanning = PLANNING_VERBS.any { low.contains(it) }
        val verbose = utterance.length >= strongThresholdChars

        return when {
            multiStep -> Triple(ModelTier.STRONG, 0.85f, "Multi-step markers detected in utterance")
            needsPlanning -> Triple(ModelTier.STRONG, 0.80f, "Planning verbs detected requiring structured execution")
            verbose -> Triple(ModelTier.STRONG, 0.75f, "Utterance length (${utterance.length} chars) exceeds strong threshold ($strongThresholdChars chars)")
            else -> {
                val words = low.split(Regex("\\s+")).filter { it.isNotBlank() }
                val score = (words.size * 0.03f).coerceIn(0.1f, 0.5f)
                Triple(ModelTier.STANDARD, score, "Standard conversational request")
            }
        }
    }

    fun classifyComplexity(utterance: String): ModelTier = evaluateComplexity(utterance).first

    companion object {
        private val MULTI_STEP_MARKERS = listOf(
            "and then", "then ", "after that", "followed by", "before that",
            "finally", "first ", "next ", "phir", "uske baad", "firstly", "secondly"
        )

        private val PLANNING_VERBS = listOf(
            "plan", "coordinate", "organize", "arrange", "book", "schedule",
            "setup", "set up", "prepare for"
        )
    }
}