package com.jarvis.memory

import android.util.Log
import com.jarvis.accessibility.ScreenVisionManager
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

class JarvisRecallService(
    private val indexStore: RagIndexStore
) {
    private val TAG = "JarvisRecallService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = AtomicBoolean(false)
    private var lastScreenHash = 0

    // Triggered externally by AccessibilityService when significant UI changes happen
    private var pendingCapture = false

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Starting Jarvis Recall Service (Passive Memory Indexer)")

        scope.launch {
            while (isRunning.get()) {
                delay(8000) // Poll interval to check if a capture was requested

                if (pendingCapture) {
                    pendingCapture = false
                    captureAndIndex()
                }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        Log.i(TAG, "Stopped Jarvis Recall Service")
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
            // Filter out Jarvis itself to avoid recursive indexing
            if (pkg.contains("com.jarvis")) return
            // Filter out system UI
            if (pkg.contains("com.android.systemui") || pkg.contains("launcher")) return

            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.ROOT).format(Date(timestamp))
            val appName = formatAppName(pkg)
            
            val docId = "recall_\${timestamp}_\$pkg"
            val title = "Screen Capture: \$appName at \$dateStr"
            
            Log.d(TAG, "Indexing passive memory snapshot: \$title")

            // Chunk the screen text
            val chunker = DocumentChunker()
            val (metadata, chunks) = chunker.chunk(
                rawText = capture.uiTextTree,
                docId = docId,
                title = title,
                sourcePath = "recall://$pkg",
                chunkSizeWords = 300,
                overlapWords = 50
            )

            // Store in RagIndexStore
            if (chunks.isNotEmpty()) {
                indexStore.indexDocument(metadata, chunks)
                Log.i(TAG, "Successfully indexed \${chunks.size} chunks for \$title")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture/index passive memory: \${e.message}")
        }
    }

    private fun formatAppName(pkg: String): String {
        return when {
            pkg.contains("youtube", ignoreCase = true) -> "YouTube"
            pkg.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            pkg.contains("settings", ignoreCase = true) -> "Settings"
            pkg.contains("chrome", ignoreCase = true) -> "Chrome"
            pkg.contains("gmail", ignoreCase = true) -> "Gmail"
            pkg.contains("instagram", ignoreCase = true) -> "Instagram"
            else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }
}
