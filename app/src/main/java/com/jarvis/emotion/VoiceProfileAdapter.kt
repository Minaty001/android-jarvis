package com.jarvis.emotion

import com.jarvis.voice.TtsEngine

/**
 * Adapts assistant emotional state into TTS engine rate and pitch configurations,
 * synchronizing vocal expression with synthesized utterances.
 */
class VoiceProfileAdapter {

    /**
     * Resolves the VoiceProfile for a given state and applies rate/pitch to the TtsEngine.
     */
    fun applyProfile(
        ttsEngine: TtsEngine,
        state: AssistantState,
        preferences: EmotionalPreferences = EmotionalPreferences()
    ) {
        val targetProfile = if (!preferences.voiceExpressionEnabled && state != AssistantState.PANIC) {
            VoiceProfile.FOCUSED
        } else {
            VoiceProfile.forState(state)
        }

        ttsEngine.applyVoiceProfile(targetProfile.speechRate, targetProfile.pitch)
    }

    /**
     * Resolves profile from an EmotionalSnapshot.
     */
    fun applyProfile(
        ttsEngine: TtsEngine,
        snapshot: EmotionalSnapshot,
        preferences: EmotionalPreferences = EmotionalPreferences()
    ) {
        applyProfile(ttsEngine, snapshot.current.state, preferences)
    }
}
