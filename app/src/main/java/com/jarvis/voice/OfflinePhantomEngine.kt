package com.jarvis.voice

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflinePhantomEngine(private val context: Context) {
    private val TAG = "OfflinePhantom"
    
    fun tryHandleOffline(command: String): String? {
        val lower = command.lowercase(Locale.US)
        
        Log.i(TAG, "Attempting offline phantom resolution for: '\$lower'")
        
        if (lower.contains("time") || lower.contains("clock")) {
            val format = SimpleDateFormat("h:mm a", Locale.US)
            return "The time is \${format.format(Date())}"
        }
        
        if (lower.contains("date") || lower.contains("day")) {
            val format = SimpleDateFormat("EEEE, MMMM d", Locale.US)
            return "Today is \${format.format(Date())}"
        }
        
        if (lower.contains("flashlight on") || lower.contains("torch on")) {
            toggleFlashlight(true)
            return "Flashlight turned on."
        }
        
        if (lower.contains("flashlight off") || lower.contains("torch off")) {
            toggleFlashlight(false)
            return "Flashlight turned off."
        }
        
        if (lower.contains("alarm")) {
            return "I cannot set an offline alarm yet, but I am running in Phantom mode."
        }
        
        return null // Unhandled, LLM is strictly required
    }
    
    private fun toggleFlashlight(state: Boolean) {
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camManager.cameraIdList[0]
            camManager.setTorchMode(cameraId, state)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight: \${e.message}")
        }
    }
}
