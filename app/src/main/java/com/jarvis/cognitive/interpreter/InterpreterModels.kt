package com.jarvis.cognitive.interpreter

import com.jarvis.cognitive.CognitiveMode

data class DirectToolCall(
    val name: String,
    val args: Map<String, String> = emptyMap()
)

data class InterpreterDecision(
    val mode: CognitiveMode,
    val confidence: Float,
    val reason: String,
    val directAnswer: String? = null,
    val directTool: DirectToolCall? = null,
    val planGoal: String? = null,
    val clarifyQuestion: String? = null,
    val refuseReason: String? = null,
    val fastPath: Boolean = false,
    val fallbackUsed: Boolean = false
)
