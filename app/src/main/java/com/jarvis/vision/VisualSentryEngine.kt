package com.jarvis.vision

import android.content.Context
import android.util.Log
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus

/**
 * V5 Visual Sentry Mode (Shoulder-surfing protection on sensitive apps).
 */
class VisualSentryEngine(private val context: Context) {
    private val TAG = "VisualSentry"
    
    // List of sensitive packages
    private val sensitivePackages = setOf(
        "com.whatsapp",
        "com.android.chrome",
        "com.google.android.apps.messaging",
        "com.chase.sig.android", 
        "com.bankofamerica.bofa"
    )

    fun onAppForegrounded(packageName: String) {
        if (sensitivePackages.contains(packageName)) {
            Log.i(TAG, "Sensitive app foregrounded: $packageName. Activating Visual Sentry.")
            activateSentry()
        }
    }
    
    private fun activateSentry() {
        // In a real implementation, this would trigger the front camera and run a face detection
        // model to count the number of faces looking at the screen.
        // For this mock, we just simulate a background check and emit a warning event.
        
        Log.i(TAG, "Running local face detection on front camera frames...")
        
        // Mocking a detected intruder
        val isIntruderDetected = true
        
        if (isIntruderDetected) {
            Log.w(TAG, "Multiple faces detected while viewing sensitive app!")
            val alert = JarvisEvent.SystemAlert(
                level = "HIGH",
                message = "Shoulder-surfing detected! Privacy at risk."
            )
            JarvisEventBus.shared.post(alert)
        }
    }
}
