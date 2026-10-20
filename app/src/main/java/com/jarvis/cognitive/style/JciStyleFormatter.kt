package com.jarvis.cognitive.style

import com.jarvis.cognitive.capability.JciEvidenceLevel
import com.jarvis.cognitive.verifier.JciVerificationResult
import com.jarvis.cognitive.verifier.JciVerificationStatus
import com.jarvis.emotion.AssistantState

class JciStyleFormatter {

    /**
     * Formats an execution result in the signature MCU J.A.R.V.I.S. persona.
     * Enforces the honesty invariant: never claim success unless independently verified.
     */
    fun formatExecutionResponse(
        capabilityId: String,
        verification: JciVerificationResult,
        userUtterance: String = ""
    ): FormattedResponse {
        val upperId = capabilityId.uppercase()

        return when (verification.status) {
            JciVerificationStatus.VERIFIED -> {
                val emotion = if (isPlayful(userUtterance)) AssistantState.KIDDING else AssistantState.HAPPY
                val text = when (upperId) {
                    "FLASHLIGHT" -> "Flashlight activated, sir."
                    "DEVICE_SETTINGS" -> "Device settings adjusted accordingly, sir."
                    "SYSTEM_SWITCHBOARD" -> "System switch toggled as requested, sir."
                    "MUSIC_PLAY", "YOUTUBE_PLAY", "SPOTIFY_PLAY" -> "Audio playback initiated, sir."
                    "MEDIA_CONTROL" -> "Media playback adjusted, sir."
                    "NOTE", "QUICK_NOTES" -> "I have committed that to memory, sir."
                    "CALCULATE" -> "${verification.message}, sir."
                    else -> "Very good, sir. Operation completed successfully."
                }
                FormattedResponse(
                    spokenText = text,
                    targetEmotion = emotion,
                    isHonestConfirmation = true
                )
            }

            JciVerificationStatus.SUBMITTED_ONLY -> {
                // Level 4 honesty rule: Can ONLY report dispatched / submitted, NEVER "done"
                val text = when (upperId) {
                    "CLOSE_APP", "APPS_CLOSE_ALL" -> "I have dispatched the close command, sir. The system will terminate the application."
                    "SPEAK" -> "Speech output dispatched, sir."
                    "UI_CLICK", "UI_SCROLL", "UI_TYPE" -> "Accessibility action dispatched to the screen, sir."
                    "INSTALL_APP" -> "Opening the Play Store for installation, sir."
                    else -> "The request has been dispatched to the subsystem, sir."
                }
                FormattedResponse(
                    spokenText = text,
                    targetEmotion = AssistantState.FOCUSED,
                    isHonestConfirmation = true
                )
            }

            JciVerificationStatus.UNVERIFIED -> {
                val text = "I dispatched the request, sir, but I could not independently verify the outcome: ${verification.message}"
                FormattedResponse(
                    spokenText = text,
                    targetEmotion = AssistantState.FOCUSED,
                    isHonestConfirmation = true
                )
            }

            JciVerificationStatus.REJECTED -> {
                val text = "I encountered an issue executing that command, sir: ${verification.message}"
                FormattedResponse(
                    spokenText = text,
                    targetEmotion = AssistantState.SAD,
                    isHonestConfirmation = true
                )
            }
        }
    }

    /**
     * Formats direct conversational answers with calm precision and restrained wit.
     */
    fun formatDirectAnswer(
        answer: String,
        preferredEmotion: AssistantState? = null
    ): FormattedResponse {
        val trimmed = answer.trim()
        val emotion = preferredEmotion ?: when {
            isJoke(trimmed) -> AssistantState.KIDDING
            isApology(trimmed) -> AssistantState.SAD
            else -> AssistantState.FOCUSED
        }

        return FormattedResponse(
            spokenText = trimmed,
            targetEmotion = emotion,
            isHonestConfirmation = true
        )
    }

    /**
     * Formats clarification inquiries.
     */
    fun formatClarification(question: String): FormattedResponse {
        val text = if (!question.endsWith("sir?") && !question.endsWith("sir.")) {
            "${question.trim()} sir?"
        } else question.trim()

        return FormattedResponse(
            spokenText = text,
            targetEmotion = AssistantState.THINKING,
            isHonestConfirmation = true
        )
    }

    /**
     * Formats policy refusals politely and firmly.
     */
    fun formatRefusal(reason: String): FormattedResponse {
        val text = "I am afraid I cannot do that, sir: $reason"
        return FormattedResponse(
            spokenText = text,
            targetEmotion = AssistantState.FOCUSED,
            isHonestConfirmation = true
        )
    }

    private fun isPlayful(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("joke") || lower.contains("funny") || lower.contains("humor") || lower.contains("sarcasm")
    }

    private fun isJoke(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("why did the") || lower.contains("knock knock") || lower.contains("haha")
    }

    private fun isApology(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("i am sorry") || lower.startsWith("i apologize") || lower.startsWith("regrettably")
    }
}
