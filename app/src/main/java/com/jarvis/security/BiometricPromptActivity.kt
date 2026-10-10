package com.jarvis.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class BiometricPromptActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Invisible activity
        
        val requestId = intent.getStringExtra("request_id") ?: return finish()
        val title = intent.getStringExtra("title") ?: "Jarvis Security"
        val subtitle = intent.getStringExtra("subtitle") ?: "Authentication required"

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w("Biometric", "Auth error: \$errString")
                    resolveRequest(requestId, false)
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i("Biometric", "Auth succeeded")
                    resolveRequest(requestId, true)
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w("Biometric", "Auth failed")
                    // Don't finish on fail, let them retry, or it will call error on max retries
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    companion object {
        private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        suspend fun authenticate(context: Context, title: String, subtitle: String): Boolean {
            val requestId = java.util.UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Boolean>()
            pendingRequests[requestId] = deferred
            
            val intent = Intent(context, BiometricPromptActivity::class.java).apply {
                putExtra("request_id", requestId)
                putExtra("title", title)
                putExtra("subtitle", subtitle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            return try {
                deferred.await()
            } finally {
                pendingRequests.remove(requestId)
            }
        }

        private fun resolveRequest(id: String, success: Boolean) {
            pendingRequests[id]?.complete(success)
        }
    }
}
