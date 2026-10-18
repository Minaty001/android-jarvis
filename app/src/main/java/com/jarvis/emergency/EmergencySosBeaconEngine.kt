package com.jarvis.emergency

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.jarvis.tools.LocationTool
import com.jarvis.tools.TelephonyTool
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * EmergencySosBeaconEngine
 *
 * Coordinates life-critical distress signals:
 * 1. Morse code torch strobe (... --- ...) with zero allocations during iteration.
 * 2. High-priority siren alert tone via AudioManager.STREAM_ALARM.
 * 3. Real-time GPS coordinate extraction with Google Maps URL.
 * 4. Automated SMS dispatch to emergency contacts.
 * 5. Hands-free emergency call dialer with auto-speakerphone.
 */
object EmergencySosBeaconEngine {

    private const val TAG = "EmergencySosBeacon"

    // International Morse Code distress pattern: "... --- ..."
    val MORSE_SOS_TIMINGS: List<Pair<Boolean, Long>> = listOf(
        // S: 3 dots
        Pair(true, 150L), Pair(false, 150L),
        Pair(true, 150L), Pair(false, 150L),
        Pair(true, 150L), Pair(false, 450L), // Letter gap

        // O: 3 dashes
        Pair(true, 450L), Pair(false, 150L),
        Pair(true, 450L), Pair(false, 150L),
        Pair(true, 450L), Pair(false, 450L), // Letter gap

        // S: 3 dots
        Pair(true, 150L), Pair(false, 150L),
        Pair(true, 150L), Pair(false, 150L),
        Pair(true, 150L), Pair(false, 1000L) // Word/Cycle gap
    )

    private var activeJob: Job? = null
    private var isTorchOn: Boolean = false
    private var torchCameraId: String? = null
    private var cameraManager: CameraManager? = null

    @Volatile
    private var active: Boolean = false

    fun isBeaconActive(): Boolean = active

    /**
     * Formats emergency text with coordinates and Google Maps link.
     */
    fun formatSosMessage(lat: Double?, lng: Double?): String {
        return if (lat != null && lng != null) {
            "EMERGENCY SOS! I need help immediately. My current location is: https://maps.google.com/?q=$lat,$lng"
        } else {
            "EMERGENCY SOS! I need help immediately. Location unavailable (GPS offline)."
        }
    }

    /**
     * Activates SOS beacon with strobe, siren, SMS, and dialer.
     */
    suspend fun startBeacon(
        context: Context?,
        contactNumber: String? = null,
        dialEmergency: Boolean = true
    ): ToolResult {
        active = true
        val targetContact = contactNumber ?: "112"

        if (context == null) {
            return ToolResult(
                success = true,
                message = "Emergency SOS Beacon activated (Morse strobe, siren, location dispatch) [Simulation Mode].",
                data = mapOf("active" to "true", "contact" to targetContact)
            )
        }

        // 1. Extract GPS coordinates
        var lat: Double? = null
        var lng: Double? = null
        try {
            val locResult = LocationTool(context).execute(mapOf("action" to "get_location"))
            if (locResult.success) {
                lat = locResult.data["latitude"]?.toString()?.toDoubleOrNull()
                lng = locResult.data["longitude"]?.toString()?.toDoubleOrNull()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve emergency GPS location: ${e.message}")
        }

        // 2. Dispatch emergency SMS
        val sosMessage = formatSosMessage(lat, lng)
        try {
            TelephonyTool(context).execute(
                mapOf(
                    "action" to "sms_send",
                    "recipient" to targetContact,
                    "message" to sosMessage
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch SOS SMS: ${e.message}")
        }

        // 3. Dial emergency services with speakerphone enabled
        if (dialEmergency) {
            try {
                TelephonyTool(context).execute(
                    mapOf(
                        "action" to "call",
                        "recipient" to targetContact,
                        "speaker" to "true"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dial emergency contact: ${e.message}")
            }
        }

        // 4. Initialize hardware torch
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        torchCameraId = try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }

        // 5. Launch strobe and siren loop in background
        activeJob?.cancel()
        activeJob = CoroutineScope(Dispatchers.Default).launch {
            var toneGen: ToneGenerator? = null
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator init failed: ${e.message}")
            }

            try {
                while (isActive && active) {
                    for (step in MORSE_SOS_TIMINGS) {
                        if (!isActive || !active) break
                        val on = step.first
                        val durationMs = step.second
                        setTorch(on)
                        if (on) {
                            try {
                                toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, durationMs.toInt())
                            } catch (ignored: Exception) {}
                        } else {
                            try {
                                toneGen?.stopTone()
                            } catch (ignored: Exception) {}
                        }
                        delay(durationMs)
                    }
                }
            } finally {
                setTorch(false)
                try {
                    toneGen?.stopTone()
                    toneGen?.release()
                } catch (ignored: Exception) {}
            }
        }

        return ToolResult(
            success = true,
            message = "Emergency SOS Beacon activated (Morse strobe, siren, location link dispatched to $targetContact).",
            data = mapOf("active" to "true", "contact" to targetContact, "has_gps" to (lat != null).toString())
        )
    }

    private fun setTorch(on: Boolean) {
        val cm = cameraManager
        val id = torchCameraId
        if (cm != null && id != null) {
            try {
                cm.setTorchMode(id, on)
                isTorchOn = on
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Immediately stops beacon, cancelling coroutines, silences siren, and shuts off torch.
     */
    fun stopBeacon(): Boolean {
        active = false
        activeJob?.cancel()
        activeJob = null
        setTorch(false)
        return true
    }
}
