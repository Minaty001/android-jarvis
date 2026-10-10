package com.jarvis.routine

import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PatternLearningEngine(private val llmClient: LlmClient) {
    private val TAG = "PatternLearningEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = AtomicBoolean(false)
    private val eventLog = mutableListOf<String>()

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Pattern Learning Engine started")

        // 1. Subscribe to relevant events for pattern learning
        scope.launch {
            JarvisEventBus.shared.subscribe<JarvisEvent.BluetoothStateChanged>().collect { event ->
                val state = if(event.isConnected) "Connected" else "Disconnected"
                logEvent("Bluetooth $state: ${event.deviceName}")
            }
        }
        scope.launch {
            JarvisEventBus.shared.subscribe<JarvisEvent.GeofenceTransition>().collect { event ->
                val state = if(event.entered) "Enter" else "Exit"
                logEvent("Geofence $state: ${event.regionId}")
            }
        }
        scope.launch {
            JarvisEventBus.shared.subscribe<JarvisEvent.MediaStateChanged>().collect { event ->
                val state = if(event.isPlaying) "Playing" else "Paused"
                logEvent("Media $state: ${event.title} by ${event.artist}")
            }
        }

        // 2. Periodic worker for analysis
        scope.launch {
            while (isRunning.get()) {
                delay(24 * 60 * 60 * 1000L) // Analyze every 24 hours
                analyzePatterns()
            }
        }
    }

    fun stop() {
        isRunning.set(false)
    }

    @Synchronized
    fun logEvent(desc: String) {
        if (eventLog.size > 500) {
            eventLog.removeAt(0)
        }
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        eventLog.add("[$timestamp] $desc")
    }

    // Expose an on-demand method to analyze right away for testing
    fun analyzePatternsNow() {
        scope.launch { analyzePatterns() }
    }

    private suspend fun analyzePatterns() {
        if (eventLog.isEmpty()) return
        Log.i(TAG, "Analyzing ${eventLog.size} events for routine patterns...")

        val logsToAnalyze = synchronized(this) { eventLog.joinToString("\n") }
        val prompt = """
            You are a smart routine discovery engine.
            Review the following recent system events and identify any recurring habits or sequential patterns that could be automated.
            (e.g., if Bluetooth connects to "Car" and immediately Media starts playing).
            
            Logs:
            $logsToAnalyze
            
            If you find a strong pattern, suggest a new Routine configuration. Format your output as a short human-readable suggestion. If no clear patterns exist, output "No patterns found."
        """.trimIndent()

        val response = llmClient.chat(listOf(Message("user", prompt)))
        if (response.isSuccess) {
            val suggestion = response.getOrNull() ?: ""
            if (suggestion.isNotBlank() && !suggestion.contains("No patterns found", ignoreCase = true)) {
                Log.i(TAG, "Suggested Routine: $suggestion")
                JarvisEventBus.shared.post(JarvisEvent.SystemAlert("INFO", "Routine Suggestion: $suggestion"))
            }
        }
    }
}
