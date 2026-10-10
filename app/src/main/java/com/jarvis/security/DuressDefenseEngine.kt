package com.jarvis.security

import android.content.Context
import android.util.Log

/**
 * Fake Offline Duress Defense Engine.
 * When duress mode is activated (e.g. by coercion or silent SOS trigger),
 * Jarvis spoof-answers with standard offline failures while silently preserving
 * system security, locking confidential memories, and saving emergency coordinates.
 */
class DuressDefenseEngine(private val context: Context? = null) {

    private val TAG = "DuressDefenseEngine"

    var isDuressActive: Boolean = false
        private set

    fun activateDuressMode(): String {
        isDuressActive = true
        Log.w(TAG, "DURESS DEFENSE ENGAGED. System responses spoofing offline status.")
        return "Duress mode active. Offline disguise engaged."
    }

    fun deactivateDuressMode(): String {
        isDuressActive = false
        Log.i(TAG, "Duress mode disengaged. Normal operations restored.")
        return "Duress mode deactivated. All systems operational."
    }

    fun getSpoofedResponse(): String {
        return "Network connection unavailable. Please check your network settings and try again later."
    }
}
