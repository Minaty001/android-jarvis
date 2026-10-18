package com.jarvis.sound

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.jarvis.voice.audio.AudioFrameConsumer
import com.jarvis.voice.audio.ContinuousAudioStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

data class SoundDetectionEvent(
    val category: SoundCategory,
    val confidence: Float,
    val dominantFrequencyHz: Float,
    val timestamp: Long = System.currentTimeMillis()
)

fun interface SoundDetectionListener {
    fun onSoundDetected(event: SoundDetectionEvent)
}

/**
 * AmbientSoundMonitorEngine
 *
 * Coordinates continuous or battery-saver ambient sound monitoring.
 * Features:
 * - Battery-aware duty-cycling (1s on / 2s off when on battery power).
 * - Debounce cooldown logic (default 10s per sound category) to prevent alert storms.
 * - PARTIAL_WAKE_LOCK management to keep acoustic CPU processing alive during screen off.
 * - Thread-safe listener registration, audio stream consumer attachment, and simulation triggers.
 */
object AmbientSoundMonitorEngine : AudioFrameConsumer {
    private const val TAG = "AmbientSoundMonitor"

    enum class DutyCycleMode {
        CONTINUOUS,
        BATTERY_SAVER
    }

    private val isRunning = AtomicBoolean(false)
    private val activeCategories = mutableSetOf<SoundCategory>()
    @Volatile private var dutyCycleMode = DutyCycleMode.BATTERY_SAVER
    private var cooldownMs: Long = 10000L
    @Volatile private var sessionStartUptimeMs: Long = 0L

    private val lastDetectionTimestamps = ConcurrentHashMap<SoundCategory, Long>()
    private val detectionCounts = ConcurrentHashMap<SoundCategory, AtomicInteger>()
    private val listeners = CopyOnWriteArrayList<SoundDetectionListener>()

    private val classifier = AmbientSoundClassifier()

    private var monitorWakeLock: PowerManager.WakeLock? = null
    private var monitorScope: CoroutineScope? = null
    private var attachedStream: ContinuousAudioStream? = null

    fun isMonitoring(): Boolean = isRunning.get()

    fun getActiveCategories(): Set<SoundCategory> = synchronized(activeCategories) { activeCategories.toSet() }

    fun getDutyCycleMode(): DutyCycleMode = dutyCycleMode

    fun setCooldownMs(ms: Long) {
        cooldownMs = ms
    }

    fun updatePowerState(isCharging: Boolean) {
        dutyCycleMode = if (isCharging) DutyCycleMode.CONTINUOUS else DutyCycleMode.BATTERY_SAVER
        Log.i(TAG, "DutyCycleMode dynamically updated to: $dutyCycleMode (charging=$isCharging)")
    }

    fun addListener(listener: SoundDetectionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SoundDetectionListener) {
        listeners.remove(listener)
    }

    fun clearListeners() {
        listeners.clear()
    }

    fun attachAudioStream(stream: ContinuousAudioStream?) {
        attachedStream?.removeConsumer(this)
        attachedStream = stream
        if (isRunning.get() && stream != null) {
            stream.addConsumer(this)
        }
    }

    fun detachAudioStream() {
        attachedStream?.removeConsumer(this)
        attachedStream = null
    }

    fun getDetectionCount(category: SoundCategory): Int {
        return detectionCounts[category]?.get() ?: 0
    }

    fun resetStats() {
        synchronized(lastDetectionTimestamps) {
            lastDetectionTimestamps.clear()
        }
        detectionCounts.clear()
        classifier.reset()
    }

    @SuppressLint("WakelockTimeout")
    fun startMonitoring(
        context: Context? = null,
        categories: Set<SoundCategory> = SoundCategory.values().filter { it != SoundCategory.NONE }.toSet(),
        dutyCycleMode: DutyCycleMode = DutyCycleMode.BATTERY_SAVER,
        audioStream: ContinuousAudioStream? = null
    ): Boolean {
        synchronized(activeCategories) {
            activeCategories.clear()
            activeCategories.addAll(categories)
        }
        this.dutyCycleMode = dutyCycleMode
        sessionStartUptimeMs = System.nanoTime() / 1_000_000L

        if (context != null) {
            try {
                val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                if (isCharging) {
                    this.dutyCycleMode = DutyCycleMode.CONTINUOUS
                }
            } catch (_: Exception) {}

            try {
                monitorWakeLock?.let { if (it.isHeld) it.release() }
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                monitorWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::AmbientSoundMonitor")?.apply {
                    setReferenceCounted(false)
                    acquire(30 * 60 * 1000L) // 30 min safety timeout
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock acquisition failed or simulated in test: ${e.message}")
            }
        }

        isRunning.set(true)
        if (audioStream != null) {
            attachAudioStream(audioStream)
        }
        Log.i(TAG, "Ambient sound monitoring started. Categories: $categories, Mode: ${this.dutyCycleMode}")
        return true
    }

    fun stopMonitoring() {
        isRunning.set(false)
        detachAudioStream()
        try {
            monitorWakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release error: ${e.message}")
        }
        monitorWakeLock = null
        monitorScope?.cancel()
        monitorScope = null
        classifier.reset()
        Log.i(TAG, "Ambient sound monitoring stopped.")
    }

    fun processAudioFrame(samples: ShortArray, count: Int) {
        if (!isRunning.get() || count <= 0) return

        // Duty-cycling for MediaTek Helio P22 and Exynos 9611 battery preservation:
        // 1s active, 2s sleep cycle (3000ms period) when on BATTERY_SAVER
        if (dutyCycleMode == DutyCycleMode.BATTERY_SAVER) {
            val nowMs = System.nanoTime() / 1_000_000L
            val cycleOffset = (nowMs - sessionStartUptimeMs) % 3000L
            if (cycleOffset >= 1000L) {
                // In 2000ms sleep window of duty cycle
                return
            }
        }

        val result = classifier.classify(samples, count)
        if (result.category == SoundCategory.NONE) return

        handleDetectionResult(result)
    }

    override fun onAudioFrame(samples: ShortArray, readCount: Int) {
        processAudioFrame(samples, readCount)
    }

    fun simulateDetection(category: SoundCategory, confidence: Float = 0.95f) {
        if (!isRunning.get()) return

        val result = SoundClassificationResult(
            category = category,
            confidence = confidence,
            dominantFrequencyHz = when (category) {
                SoundCategory.SMOKE_ALARM -> 3000f
                SoundCategory.BABY_CRYING -> 480f
                SoundCategory.DOORBELL -> 600f
                SoundCategory.GLASS_BREAK -> 5200f
                SoundCategory.CAR_HONK -> 450f
                SoundCategory.NONE -> 0f
            },
            energyRatio = 0.85f
        )
        handleDetectionResult(result)
    }

    private fun handleDetectionResult(result: SoundClassificationResult) {
        val category = result.category
        if (category == SoundCategory.NONE) return

        // 1. Check category filter
        val allowed = synchronized(activeCategories) {
            activeCategories.isEmpty() || activeCategories.contains(category)
        }
        if (!allowed) return

        // 2. Monotonic atomic debounce cooldown check
        val nowMs = System.nanoTime() / 1_000_000L
        var shouldNotify = false

        synchronized(lastDetectionTimestamps) {
            val lastTime = lastDetectionTimestamps[category] ?: 0L
            if (nowMs - lastTime >= cooldownMs) {
                lastDetectionTimestamps[category] = nowMs
                shouldNotify = true
            } else {
                Log.d(TAG, "Suppressed duplicate detection for $category (cooldown active: ${nowMs - lastTime}ms < ${cooldownMs}ms)")
            }
        }

        if (!shouldNotify) return

        val currentCount = detectionCounts.computeIfAbsent(category) { AtomicInteger(0) }.incrementAndGet()

        val event = SoundDetectionEvent(
            category = category,
            confidence = result.confidence,
            dominantFrequencyHz = result.dominantFrequencyHz,
            timestamp = System.currentTimeMillis()
        )

        Log.i(TAG, "SOUND DETECTED: $category (confidence=${result.confidence}, count=$currentCount)")

        for (listener in listeners) {
            try {
                listener.onSoundDetected(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying SoundDetectionListener", e)
            }
        }
    }
}
