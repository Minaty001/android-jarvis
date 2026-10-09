import sys

code = """package com.jarvis.control

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PermissionGate(private val context: Context? = null) {
    suspend fun verify(intent: ActionIntent): AuthResult {
        if (intent.requiresConfirmation && context is FragmentActivity) {
            val success = suspendCancellableCoroutine<Boolean> { cont ->
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(context, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        cont.resume(false)
                    }
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        cont.resume(true)
                    }
                    override fun onAuthenticationFailed() {
                        cont.resume(false)
                    }
                })
                
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("JARVIS Authorization")
                    .setSubtitle("Confirm action: ${intent.actionType}")
                    .setNegativeButtonText("Cancel")
                    .build()
                
                prompt.authenticate(promptInfo)
                
                cont.invokeOnCancellation {
                    prompt.cancelAuthentication()
                }
            }
            return AuthResult(isAuthorized = success)
        }
        return AuthResult(isAuthorized = true)
    }
}
"""

with open("app/src/main/java/com/jarvis/control/PermissionGate.kt", "w") as f:
    f.write(code)

