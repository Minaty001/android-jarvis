package com.jarvis.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ClipboardContextEngine(
    private val context: Context,
    private val llmClient: LlmClient,
    private val onContextActionReady: (String, List<String>) -> Unit
) {
    private val TAG = "ClipboardContextEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastCopiedText = ""

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (text != null && text != lastCopiedText && text.length > 3) {
                    lastCopiedText = text
                    Log.i(TAG, "New clipboard text detected: \${text.take(30)}...")
                    analyzeContext(text)
                }
            }
        }
    }

    fun start() {
        try {
            clipboard.addPrimaryClipChangedListener(clipListener)
            Log.i(TAG, "ClipboardContextEngine started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Clipboard engine: \${e.message}")
        }
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(clipListener)
    }

    private fun analyzeContext(text: String) {
        scope.launch {
            val prompt = """
                Analyze the following copied text and suggest 1 to 3 quick contextual actions for the user.
                Copied Text: "\$text"
                
                Return ONLY a comma-separated list of short action verbs (e.g., Navigate, Translate, Explain, Summarize).
            """.trimIndent()

            val response = llmClient.chat(listOf(Message("user", prompt)))
            if (response.isSuccess) {
                val actionsRaw = response.getOrNull() ?: ""
                val actions = actionsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (actions.isNotEmpty()) {
                    onContextActionReady(text, actions)
                }
            }
        }
    }
}
