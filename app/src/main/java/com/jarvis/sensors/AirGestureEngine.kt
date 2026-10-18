package com.jarvis.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-power proximity sensor wave detector for touchless navigation and media control.
 * Detects hand wave sweeps (near-to-far transition within 100ms-800ms) across the top bezel.
 */
class AirGestureEngine(
    private val context: Context?,
    private val sensorManager: SensorManager? = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
) : SensorEventListener {

    companion object {
        private const val TAG = "AirGestureEngine"
        const val MIN_WAVE_DWELL_MS = 100L
        const val MAX_WAVE_DWELL_MS = 800L
        const val DEBOUNCE_COOLDOWN_MS = 600L
    }

    private val isRunning = AtomicBoolean(false)
    private var proximitySensor: Sensor? = null

    @Volatile
    var isEnabled: Boolean = false
        private set

    var onWaveDetected: (() -> Unit)? = null

    private var nearTimestamp: Long = 0L
    private var lastWaveTriggerTimestamp: Long = 0L

    init {
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    fun setEnabled(enabled: Boolean): Boolean {
        isEnabled = enabled
        Log.i(TAG, "Air gestures setEnabled=$enabled")
        return if (enabled) {
            start()
        } else {
            stop()
            true
        }
    }

    fun start(): Boolean {
        val manager = sensorManager
        if (manager == null) {
            Log.w(TAG, "SensorManager not available")
            return false
        }
        if (!isRunning.compareAndSet(false, true)) return true

        try {
            proximitySensor?.let {
                if (manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)) {
                    Log.i(TAG, "AirGestureEngine proximity sensor registered")
                    return true
                }
            }
            isRunning.set(false)
            return false
        } catch (e: Exception) {
            isRunning.set(false)
            Log.w(TAG, "Error registering proximity sensor for air gestures: ${e.message}")
            return false
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering sensor: ${e.message}")
        }
        Log.i(TAG, "AirGestureEngine stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning.get() || !isEnabled) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values.firstOrNull() ?: return
            val maxRange = proximitySensor?.maximumRange ?: 5.0f
            val isNear = distance < maxRange && distance < 4.0f
            processProximityTransition(isNear, System.currentTimeMillis())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Public for deterministic unit testing.
     */
    fun processProximityTransition(isNear: Boolean, timestamp: Long) {
        if (!isEnabled) return

        if (isNear) {
            nearTimestamp = timestamp
        } else {
            if (nearTimestamp > 0L) {
                val dwell = timestamp - nearTimestamp
                nearTimestamp = 0L
                if (dwell in MIN_WAVE_DWELL_MS..MAX_WAVE_DWELL_MS) {
                    if (timestamp - lastWaveTriggerTimestamp >= DEBOUNCE_COOLDOWN_MS) {
                        lastWaveTriggerTimestamp = timestamp
                        Log.i(TAG, "Air gesture wave detected! Dwell time: ${dwell}ms")
                        onWaveDetected?.invoke()
                    }
                }
            }
        }
    }
}
