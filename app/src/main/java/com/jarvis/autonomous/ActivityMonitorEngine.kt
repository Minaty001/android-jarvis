package com.jarvis.autonomous

import android.content.Context
import android.util.Log
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.data.model.ActivityMoment
import com.jarvis.habit.HabitManager
import com.jarvis.media.SpotifySessionManager
import kotlinx.coroutines.*
import java.util.UUID

object ActivityMonitorEngine {
    private const val TAG = "ActivityMonitorEngine"
    private var job: Job? = null

    @Volatile private var isRunning = false
    @Volatile private var currentPackage: String? = null
    @Volatile private var currentStartTime: Long = 0L

    // Reuse a single HabitManager instance instead of creating one per save
    private val habitManager = HabitManager()

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "ActivityMonitorEngine started")
        // Capture applicationContext to avoid service leaks
        val appContext = context.applicationContext
        val db = com.jarvis.data.JarvisDatabase.getInstance(appContext)

        job = GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                val pkg = JarvisAccessibilityService.currentForegroundPackage
                val now = System.currentTimeMillis()

                if (pkg != currentPackage) {
                    // Save previous moment before switching
                    val prevPkg = currentPackage
                    val prevStart = currentStartTime
                    if (prevPkg != null && prevStart > 0L) {
                        val duration = (now - prevStart) / 1000
                        if (duration > 5) {
                            saveMoment(db, prevPkg, prevStart, duration)
                        }
                    }
                    currentPackage = pkg
                    currentStartTime = now
                }

                delay(2000L)
            }
        }
    }

    private fun saveMoment(db: com.jarvis.data.JarvisDatabase, pkg: String, startTime: Long, duration: Long) {
        val appName = pkg.substringAfterLast('.')
        val media: String? = SpotifySessionManager.getCurrentTrack()?.let { track ->
            if (track.trackTitle != null) "${track.trackTitle} - ${track.artist}" else track.query
        }

        val moment = ActivityMoment(
            id = UUID.randomUUID().toString(),
            packageName = pkg,
            appName = appName,
            startTime = startTime,
            durationSeconds = duration,
            mediaPlaying = media,
            timeOfDay = habitManager.getCurrentTimeOfDay().name
        )

        try {
            // Use Array<Any?> to satisfy execSQL's Object[] parameter cleanly
            val args: Array<Any?> = arrayOf(
                moment.id,
                moment.packageName,
                moment.appName,
                moment.startTime,
                moment.durationSeconds,
                moment.mediaPlaying,
                moment.timeOfDay
            )
            db.writableDatabase.execSQL(
                "INSERT INTO activity_moments (id, package_name, app_name, start_time, duration_seconds, media_playing, time_of_day) VALUES (?, ?, ?, ?, ?, ?, ?)",
                args
            )
            Log.d(TAG, "Saved moment: ${moment.appName} for ${moment.durationSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save moment", e)
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        // Reset state so a restart begins clean
        currentPackage = null
        currentStartTime = 0L
        Log.i(TAG, "ActivityMonitorEngine stopped")
    }
}
