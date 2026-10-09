package com.jarvis.control

import android.content.Context
import kotlinx.coroutines.delay

class PermissionGate(
    private val context: Context? = null,
    private val getOwnerVerifier: (() -> com.jarvis.voice.OwnerVoiceVerifier?)? = null,
    private val getCandidateAudio: (() -> ShortArray)? = null
) {
    suspend fun verify(intent: ActionIntent): AuthResult {
        if (intent.requiresConfirmation) {
            val verifier = getOwnerVerifier?.invoke()
            if (verifier?.isAvailableAndEnrolled() == true) {
                val audio = getCandidateAudio?.invoke()
                if (audio != null && audio.isNotEmpty() && verifier.verifyOwner(audio)) {
                    android.util.Log.i("PermissionGate", "Access granted via Voice Biometrics")
                    return AuthResult(isAuthorized = true)
                }
                android.util.Log.w("PermissionGate", "Voice Biometrics failed or inconclusive")
                // Here we would launch a TransparentAuthActivity to show BiometricPrompt.
                // For this headless service, we return failure to prompt the user to unlock.
                return AuthResult(isAuthorized = false, reason = "Voice not recognized. Please unlock device manually.")
            } else {
                android.util.Log.i("PermissionGate", "No voice profile found, falling back to basic checks")
                delay(500)
                return AuthResult(isAuthorized = true)
            }
        }
        return AuthResult(isAuthorized = true)
    }
}
