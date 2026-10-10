package com.jarvis.notification

import android.util.Log
import com.jarvis.ai.LlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationBatchingEngine(
    private val llmClient: LlmClient
) {
    private val TAG = "NotifBatchingEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // Store batched notifications in-memory until briefed
    private val batchedNotifications = mutableListOf<String>()

    fun processNotification(appName: String, title: String, text: String, category: String) {
        // Only batch non-urgent, non-communication notifications
        if (category == "communication" || category == "alarm" || category == "call") {
            return
        }
        
        val timestamp = SimpleDateFormat("h:mm a", Locale.ROOT).format(Date())
        val entry = "[\$timestamp] \$appName: \$title - \$text"
        synchronized(batchedNotifications) {
            batchedNotifications.add(entry)
        }
        Log.d(TAG, "Batched notification: \$entry")
    }

    suspend fun generateBriefing(): String {
        val notifsToBrief = synchronized(batchedNotifications) {
            val copy = batchedNotifications.toList()
            batchedNotifications.clear()
            copy
        }
        
        if (notifsToBrief.isEmpty()) {
            return "No new notifications to summarize."
        }

        val prompt = """
            You are Jarvis, summarizing low-priority notifications for the user's daily briefing.
            Read the following batched notifications and provide a concise, natural, spoken-style summary.
            Group them by app or topic (e.g. "You have 3 emails from work, a promo from Amazon, and a YouTube upload from Marques Brownlee").
            Do not list them robotically. Be natural and helpful.
            
            Notifications:
            \${notifsToBrief.joinToString("\n")}
        """.trimIndent()

        return try {
            val summary = llmClient.chat(listOf(
                com.jarvis.ai.Message("system", "You are Jarvis, an AI assistant."),
                com.jarvis.ai.Message("user", prompt)
            )).getOrNull()?.trim() ?: "You have \${notifsToBrief.size} unseen notifications."
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate briefing: \${e.message}")
            "You have \${notifsToBrief.size} unseen notifications."
        }
    }
}
