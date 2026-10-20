package com.jarvis.cognitive

enum class CognitiveMode {
    DIRECT,    // Fast-path tool execution or single direct answer
    PLAN,      // Multi-step sequential or dependent task execution
    CLARIFY,   // Ambiguous request or missing required inputs
    REFUSE     // Prohibited by policy or capability does not exist
}

data class CognitiveDecision(
    val mode: CognitiveMode,
    val confidence: Float,
    val reason: String,
    val fastPath: Boolean = false,
    val capabilityId: String? = null,
    val actionParams: Map<String, String> = emptyMap()
)
