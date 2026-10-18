package com.jarvis.emergency

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.PowerManager
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
 * 6. PARTIAL_WAKE_LOCK protection preventing CPU sleep during active emergency beacon.
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

    @Volatile
    private var activeJob: Job? = null
    @Volatile
    private var sosWakeLock: PowerManager.WakeLock? = null
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
     * Launches physical strobe and siren immediately at t=0, while GPS and telecommunications
     * dispatch concurrently in the background.
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

        // 1. Acquire PARTIAL_WAKE_LOCK to prevent CPU deep sleep while screen is off
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            sosWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::EmergencySosBeacon")?.apply {
                acquire(15 * 60 * 1000L) // 15-minute safety timeout
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire PARTIAL_WAKE_LOCK: ${e.message}")
        }

        // 2. Initialize hardware torch immediately
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        torchCameraId = try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }

        // 3. Launch physical strobe and siren immediately at t=0
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

        // 4. Asynchronously extract GPS coordinates, send emergency SMS, and dial services
        CoroutineScope(Dispatchers.IO).launch {
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

            // Dispatch emergency SMS
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

            // Dial emergency services with speakerphone enabled
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
        }

        return ToolResult(
            success = true,
            message = "Emergency SOS Beacon activated (Morse strobe, siren, location link dispatched to $targetContact).",
            data = mapOf("active" to "true", "contact" to targetContact)
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
     * Immediately stops beacon, cancelling coroutines, silences siren, releases WakeLock, and shuts off torch.
     */
    fun stopBeacon(): Boolean {
        active = false
        activeJob?.cancel()
        activeJob = null
        setTorch(false)
        try {
            if (sosWakeLock?.isHeld == true) {
                sosWakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release error: ${e.message}")
        } finally {
            sosWakeLock = null
        }
        return true
    }
}
