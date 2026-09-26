package com.jarvis.ui.model

enum class AiPersonalityStyle {
    MCU_JARVIS,
    EXECUTIVE_ASSISTANT,
    WITTY_SARCASTIC,
    HINGLISH_FRIENDLY
}

data class UserProfile(
    val userName: String = "Alex",
    val userTitle: String = "Sir",
    val occupation: String = "Software Engineer",
    val personalNotes: String = "Allergic to peanuts. Car parked on level B2. Prefers concise answers.",
    val aiPersonaName: String = "J.A.R.V.I.S.",
    val aiPersonalityStyle: AiPersonalityStyle = AiPersonalityStyle.MCU_JARVIS,
    val customSystemInstructions: String = "Be polite, witty, call me Sir, and provide crisp responses."
)
