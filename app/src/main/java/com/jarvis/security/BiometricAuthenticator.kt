package com.jarvis.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BiometricAuthenticator(private val context: Context) {
    private val TAG = "BiometricAuth"
    private var lastAuthTimeMs: Long = 0
    private val AUTH_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    private val mutex = Mutex()

    suspend fun verify(title: String = "Jarvis Security", subtitle: String = "High-Risk Action Requires Verification"): Boolean {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastAuthTimeMs < AUTH_TIMEOUT_MS) {
                Log.i(TAG, "Auth bypassed (within 5 minute window)")
                return true
            }

            Log.i(TAG, "Requesting biometric authentication...")
            val success = BiometricPromptActivity.authenticate(context, title, subtitle)
            
            if (success) {
                lastAuthTimeMs = System.currentTimeMillis()
                Log.i(TAG, "Biometric authentication successful")
                return true
            }
            
            Log.w(TAG, "Biometric authentication failed or cancelled")
            return false
        }
    }
}
