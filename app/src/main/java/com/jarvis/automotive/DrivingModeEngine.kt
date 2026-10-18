package com.jarvis.automotive

import android.content.Context
import android.util.Log
import com.jarvis.telecom.CallAndSmsAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated In-Car Driving Mode Engine.
 * Provides persistent open-mic listening, mandatory speakerphone call routing,
 * auto-narration of incoming messages, and glanceable HUD state for hands-free driving.
 */
class DrivingModeEngine(private val context: Context? = null) {

    private val _isDrivingModeActive = MutableStateFlow(false)
    val isDrivingModeActive: StateFlow<Boolean> = _isDrivingModeActive.asStateFlow()

    var onDrivingModeChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "DrivingModeEngine"

        @Volatile
        var instance: DrivingModeEngine? = null
            internal set
    }

    init {
        instance = this
    }

    fun setDrivingMode(enabled: Boolean): Boolean {
        if (_isDrivingModeActive.value == enabled) {
            return true
        }
        _isDrivingModeActive.value = enabled
        Log.i(TAG, "Driving mode toggled to: $enabled")

        if (enabled) {
            // 1. Force speakerphone for active telephony
            CallAndSmsAgent.instance?.setSpeakerphone(true)
        }

        onDrivingModeChanged?.invoke(enabled)
        return true
    }

    fun isEnabled(): Boolean = _isDrivingModeActive.value
}
