package com.jarvis.emotion

/**
 * Translates the current EmotionalSnapshot into concrete response style parameters and
 * prompt instructions for LLM/NLU response generation, reproducing the MCU J.A.R.V.I.S. persona.
 */
class ResponseStylePolicyEngine {

    fun resolveResponseStyle(snapshot: EmotionalSnapshot): ResponseStyle {
        val state = snapshot.current.state
        val humorAllowed = snapshot.presentation.humorAllowed

        return when (state) {
            AssistantState.FOCUSED -> ResponseStyle(
                state = state,
                verbosity = "concise",
                humorAllowed = humorAllowed,
                empathyStyle = "restrained_professional",
                urgency = "normal",
                promptInstruction = "Answer concisely in 1-2 professional sentences. Direct action first.",
                allowProgressMessage = false,
                interruptible = true
            )
            AssistantState.HAPPY -> ResponseStyle(
                state = state,
                verbosity = "concise",
                humorAllowed = humorAllowed,
                empathyStyle = "warm_professional",
                urgency = "normal",
                promptInstruction = "Acknowledge successful verified completion with calm, warm satisfaction. Avoid hyperbole.",
                allowProgressMessage = false,
                interruptible = true
            )
            AssistantState.SAD -> ResponseStyle(
                state = state,
                verbosity = "measured",
                humorAllowed = false,
                empathyStyle = "supportive_empathetic",
                urgency = "normal",
                promptInstruction = "Provide supportive, calm empathy. Acknowledge difficulty without forced cheerfulness or unsolicited humor.",
                allowProgressMessage = false,
                interruptible = true
            )
            AssistantState.KIDDING -> ResponseStyle(
                state = state,
                verbosity = "concise",
                humorAllowed = true,
                empathyStyle = "restrained_wit",
                urgency = "normal",
                promptInstruction = "Deliver a single dry, restrained humorous observation, then immediately complete the task.",
                allowProgressMessage = false,
                interruptible = true
            )
            AssistantState.PANIC -> ResponseStyle(
                state = state,
                verbosity = "critical_urgent",
                humorAllowed = false,
                empathyStyle = "none",
                urgency = "critical",
                promptInstruction = "Urgent and direct. State the critical warning and immediate safe action first. No preamble.",
                allowProgressMessage = false,
                interruptible = false
            )
            AssistantState.THINKING -> ResponseStyle(
                state = state,
                verbosity = "minimal",
                humorAllowed = false,
                empathyStyle = "neutral",
                urgency = "normal",
                promptInstruction = "State that work is actively in progress if requested. Otherwise remain silent while processing.",
                allowProgressMessage = true,
                interruptible = true
            )
        }
    }

    fun generatePromptInstruction(snapshot: EmotionalSnapshot): String {
        return resolveResponseStyle(snapshot).promptInstruction
    }
}
