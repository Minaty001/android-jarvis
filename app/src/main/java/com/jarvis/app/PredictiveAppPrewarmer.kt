package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Predictive App Pre-warming Engine.
 * Analyzes app launch frequencies per hour and weekday to anticipate which app the user
 * will likely open next, pre-warming intents and internal caches for instantaneous response.
 */
class PredictiveAppPrewarmer(private val context: Context? = null) {

    private val TAG = "PredictiveAppPrewarmer"

    // Maps "hour_of_day:package" -> count of launches
    private val launchFrequencyMap = ConcurrentHashMap<String, Int>()

    init {
        seedCommonDefaults()
    }

    private fun seedCommonDefaults() {
        // Morning defaults (8-10 AM)
        launchFrequencyMap["8:com.whatsapp"] = 15
        launchFrequencyMap["8:com.google.android.apps.maps"] = 10
        launchFrequencyMap["9:com.spotify.music"] = 12

        // Midday defaults (12-14 PM)
        launchFrequencyMap["12:com.whatsapp"] = 20
        launchFrequencyMap["13:com.google.android.youtube"] = 15

        // Evening defaults (18-21 PM)
        launchFrequencyMap["18:com.google.android.apps.maps"] = 14
        launchFrequencyMap["19:com.spotify.music"] = 18
        launchFrequencyMap["20:com.google.android.youtube"] = 25
    }

    fun recordAppLaunch(packageName: String) {
        if (packageName.isBlank()) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val key = "$hour:$packageName"
        val count = launchFrequencyMap[key] ?: 0
        launchFrequencyMap[key] = count + 1
        Log.d(TAG, "Recorded launch for $packageName at hour $hour (total: ${count + 1})")
    }

    fun getPredictedNextApp(): String? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val prefix = "$hour:"
        
        return launchFrequencyMap.entries
            .filter { it.key.startsWith(prefix) }
            .maxByOrNull { it.value }
            ?.key
            ?.removePrefix(prefix)
    }

    fun prewarmPredictedApp(): Boolean {
        val predictedPkg = getPredictedNextApp() ?: return false
        val ctx = context ?: return false

        return try {
            val pm = ctx.packageManager
            val intent = pm.getLaunchIntentForPackage(predictedPkg)
            if (intent != null) {
                // Pre-resolving intent warms the package manager resolver cache
                val resolveInfo = pm.resolveActivity(intent, 0)
                Log.i(TAG, "Pre-warmed predicted app: $predictedPkg (resolved=${resolveInfo != null})")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prewarm app $predictedPkg: ${e.message}")
            false
        }
    }
}
