package com.jarvis.memory

import android.util.Log
import com.jarvis.accessibility.ScreenVisionManager
import com.jarvis.ai.LlmClient
import com.jarvis.rag.DocumentChunker
import com.jarvis.rag.RagIndexStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class PhotographicMemoryEngine(
    private val indexStore: RagIndexStore,
    private val llmClient: LlmClient
) {
    private val TAG = "PhotographicMemoryEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = AtomicBoolean(false)
    private var lastScreenHash = 0
    private var pendingCapture = false

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Starting Photographic Memory Engine")

        scope.launch {
            while (isRunning.get()) {
                delay(10000) // Poll interval to check if a capture was requested

                if (pendingCapture) {
                    pendingCapture = false
                    captureAndIndex()
                }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        Log.i(TAG, "Stopped Photographic Memory Engine")
    }

    fun requestCapture() {
        if (!isRunning.get()) return
        pendingCapture = true
    }

    private suspend fun captureAndIndex() {
        try {
            val capture = ScreenVisionManager.captureScreen()
            if (capture.uiTextTree.isBlank() || capture.uiTextTree.contains("No interactive UI text")) return

            // Simple deduplication based on text content hash
            val currentHash = capture.uiTextTree.hashCode()
            if (currentHash == lastScreenHash) {
                return // Screen hasn't meaningfully changed
            }
            lastScreenHash = currentHash

            val pkg = capture.activePackage ?: "unknown_app"
            if (pkg.contains("com.jarvis") || pkg.contains("com.android.systemui") || pkg.contains("launcher")) return

            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.ROOT).format(Date(timestamp))
            val appName = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            
            // Use LLM to summarize the screen content for better recall
            val prompt = """
                You are a Photographic Memory agent. Analyze this Android screen text from the app '$appName' at $dateStr.
                Extract the key information (e.g. recipes, flight numbers, articles, profiles) into a concise summary.
                
                Screen Text:
                ${capture.uiTextTree.take(2000)}
            """.trimIndent()
            
            val summary = llmClient.chat(listOf(
                com.jarvis.ai.Message("system", "You are a helpful memory agent."),
                com.jarvis.ai.Message("user", prompt)
            )).getOrNull()?.trim() ?: ""
            if (summary.isBlank() || summary.lowercase().contains("no useful info")) return

            val docId = "photo_mem_${timestamp}_$pkg"
            val title = "Screen Memory: $appName at $dateStr"
            
            Log.d(TAG, "Indexing Photographic Memory: $title")

            val chunker = DocumentChunker()
            val (metadata, chunks) = chunker.chunk(
                rawText = summary,
                docId = docId,
                title = title,
                sourcePath = "photomem://$pkg",
                chunkSizeWords = 300,
                overlapWords = 50
            )

            if (chunks.isNotEmpty()) {
                indexStore.indexDocument(metadata, chunks)
                Log.i(TAG, "Successfully indexed photographic memory chunks for $title")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture photographic memory: ${e.message}")
        }
    }
}
