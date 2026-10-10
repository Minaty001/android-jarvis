package com.jarvis.voice

import android.util.Log
import kotlin.math.abs

/**
 * V5 Vocal Biometrics (Mock Pitch/Zero-Crossing Estimator).
 * In a real implementation, this would use a TFLite Speaker Verification model (e.g., d-vector embedding).
 */
class SpeakerVerificationEngine {
    private val TAG = "SpeakerVerification"
    
    // Baseline pitch/ZCR metrics recorded during "enrollment"
    private var enrolledZeroCrossingRate: Float = 0.05f
    private var isEnrolled = false
    
    fun enrollOwner(audioBuffer: ShortArray) {
        enrolledZeroCrossingRate = calculateZCR(audioBuffer)
        isEnrolled = true
        Log.i(TAG, "Owner enrolled with ZCR baseline: $enrolledZeroCrossingRate")
    }
    
    fun verifyOwner(audioBuffer: ShortArray): Boolean {
        if (!isEnrolled) {
            Log.w(TAG, "Owner not enrolled, bypassing verification")
            return true // Fallback to true if not enrolled to not break functionality
        }
        
        val currentZcr = calculateZCR(audioBuffer)
        val delta = abs(currentZcr - enrolledZeroCrossingRate)
        
        Log.d(TAG, "Verifying owner... Delta: $delta, Current ZCR: $currentZcr")
        
        // Very basic threshold for mock
        return delta < 0.02f
    }
    
    private fun calculateZCR(audioBuffer: ShortArray): Float {
        var crossings = 0
        for (i in 1 until audioBuffer.size) {
            if ((audioBuffer[i] >= 0 && audioBuffer[i - 1] < 0) || 
                (audioBuffer[i] < 0 && audioBuffer[i - 1] >= 0)) {
                crossings++
            }
        }
        return if (audioBuffer.isNotEmpty()) crossings.toFloat() / audioBuffer.size else 0f
    }
}
