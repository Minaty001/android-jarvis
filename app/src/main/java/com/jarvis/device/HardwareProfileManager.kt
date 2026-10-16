package com.jarvis.device

import android.app.ActivityManager
import android.content.Context
import android.util.Log

enum class DeviceTier {
    LOW_END,
    MID_RANGE,
    FLAGSHIP
}

data class DeviceHardwareProfile(
    val cpuCores: Int,
    val totalRamBytes: Long,
    val isLowRamDevice: Boolean,
    val tier: DeviceTier,
    val recommendedTfliteThreads: Int,
    val maxMemoryStoreResults: Int
)

object HardwareProfileManager {
    private const val TAG = "HardwareProfileManager"

    fun calculateOptimalThreads(cores: Int, isLowRam: Boolean): Int {
        val clampedCores = maxOf(1, cores)
        return when {
            isLowRam || clampedCores <= 2 -> minOf(clampedCores, 2)
            clampedCores in 3..6 -> 2
            else -> 3
        }
    }

    fun classifyTier(cores: Int, totalRamBytes: Long, isLowRam: Boolean): DeviceTier {
        val fourGb = 4L * 1024 * 1024 * 1024
        val eightGb = 8L * 1024 * 1024 * 1024
        return when {
            isLowRam || totalRamBytes < fourGb || cores <= 4 -> DeviceTier.LOW_END
            totalRamBytes < eightGb || cores < 8 -> DeviceTier.MID_RANGE
            else -> DeviceTier.FLAGSHIP
        }
    }

    fun getProfile(context: Context?): DeviceHardwareProfile {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        if (context == null) {
            return DeviceHardwareProfile(
                cpuCores = cores,
                totalRamBytes = 4L * 1024 * 1024 * 1024,
                isLowRamDevice = false,
                tier = DeviceTier.MID_RANGE,
                recommendedTfliteThreads = 2,
                maxMemoryStoreResults = 150
            )
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        val isLowRam = if (activityManager != null) {
            activityManager.getMemoryInfo(memInfo)
            activityManager.isLowRamDevice || memInfo.lowMemory
        } else {
            cores <= 2
        }

        val totalRam = if (memInfo.totalMem > 0) memInfo.totalMem else 2L * 1024 * 1024 * 1024
        val tier = classifyTier(cores, totalRam, isLowRam)
        val threads = calculateOptimalThreads(cores, isLowRam)
        val maxResults = when (tier) {
            DeviceTier.LOW_END -> 50
            DeviceTier.MID_RANGE -> 150
            DeviceTier.FLAGSHIP -> 300
        }

        return DeviceHardwareProfile(
            cpuCores = cores,
            totalRamBytes = totalRam,
            isLowRamDevice = isLowRam,
            tier = tier,
            recommendedTfliteThreads = threads,
            maxMemoryStoreResults = maxResults
        )
    }

    fun getRecommendedTfliteThreads(context: Context?): Int =
        getProfile(context).recommendedTfliteThreads

    fun getMaxMemoryStoreResults(context: Context?): Int =
        getProfile(context).maxMemoryStoreResults

    fun isConstrainedDevice(context: Context?): Boolean =
        getProfile(context).tier == DeviceTier.LOW_END

    fun isLowEndDevice(context: Context?): Boolean =
        isConstrainedDevice(context)
}
