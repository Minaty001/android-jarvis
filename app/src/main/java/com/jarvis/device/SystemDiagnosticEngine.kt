package com.jarvis.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SystemDiagnosticEngine(private val context: Context) {
    private val TAG = "SystemDiagnostic"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = AtomicBoolean(false)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun start() {
        if (isRunning.getAndSet(true)) return
        scope.launch {
            while (isRunning.get()) {
                runDiagnostics()
                delay(30 * 60 * 1000) // Every 30 minutes
            }
        }
    }

    fun stop() {
        isRunning.set(false)
    }

    private fun runDiagnostics() {
        Log.i(TAG, "Running autonomous system diagnostics...")
        checkBattery()
        checkMemory()
        checkStorage()
    }

    private fun checkBattery() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()

        if (batteryPct in 1.0f..20.0f) {
            JarvisEventBus.shared.post(JarvisEvent.SystemAlert("WARNING", "Battery is critically low (${batteryPct.toInt()}%). Suggest enabling extreme power saver mode."))
        }
    }

    private fun checkMemory() {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        if (memInfo.lowMemory) {
            JarvisEventBus.shared.post(JarvisEvent.SystemAlert("CRITICAL", "System memory is critically low. Device may lag. Suggest killing rogue background apps."))
        }
    }

    private fun checkStorage() {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val freeStorageBytes = availableBlocks * blockSize
        val freeStorageGB = freeStorageBytes / (1024 * 1024 * 1024).toFloat()

        if (freeStorageGB < 2.0f) {
            JarvisEventBus.shared.post(JarvisEvent.SystemAlert("WARNING", "Local storage is nearly full (only ${"%.1f".format(freeStorageGB)} GB left). Suggest cleaning cache."))
        }
    }
}
