package com.jarvis.cognitive

data class JciLimits(
    val maxLlmCallsSimple: Int = 1,
    val maxLlmCallsComplex: Int = 6,
    val maxSteps: Int = 12,
    val maxReplans: Int = 2,
    val maxRetries: Int = 1,
    val maxCandidates: Int = 3,
    val taskTimeoutS: Int = 120,
    val llmTimeoutS: Int = 20,
    val tokenBudget: Int = 2048,
    val clarifyThreshold: Float = 0.65f,
    val memoryTtlDays: Int = 30
)

data class CognitiveConfig(
    val enabled: Boolean = false,
    val shadowMode: Boolean = true,
    val proactive: Boolean = false,
    val provider: String = "Groq",
    val model: String = "openai/gpt-oss-20b",
    val logLevel: String = "INFO",
    val limits: JciLimits = JciLimits()
)
