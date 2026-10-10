package com.jarvis.voice

import android.content.Context
import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ui.overlay.ClipboardOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BabelFishInterpreter(
    private val context: Context,
    private val llmClient: LlmClient,
    private val asrEngine: AndroidSpeechRecognizerEngine,
    private val overlayController: ClipboardOverlayController
) {
    private val TAG = "BabelFishInterpreter"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isListening = false

    fun toggleInterpreterMode() {
        if (isListening) {
            stopInterpreter()
        } else {
            startInterpreter()
        }
    }

    private fun startInterpreter() {
        Log.i(TAG, "Starting BabelFish Interpreter")
        isListening = true
        overlayController.showContextActions("Interpreter Mode Active. Listening...", emptyList()) { }
        
        scope.launch {
            asrEngine.startListening(
                onPartial = { partial ->
                    scope.launch(Dispatchers.Main) {
                        overlayController.showContextActions(partial, emptyList()) { }
                    }
                },
                onFinal = { text ->
                    if (text.isNotBlank()) {
                        translateAndDisplay(text)
                    }
                    if (isListening) {
                        startInterpreter()
                    }
                },
                onError = { code, error ->
                    Log.e(TAG, "ASR Error \$code: \$error")
                    scope.launch(Dispatchers.Main) {
                        overlayController.showContextActions("Listening Error. Retrying...", emptyList()) { }
                    }
                    if (isListening) {
                        startInterpreter()
                    }
                }
            )
        }
    }

    private fun stopInterpreter() {
        Log.i(TAG, "Stopping BabelFish Interpreter")
        isListening = false
        scope.launch {
            asrEngine.stopListening()
        }
        overlayController.hide()
    }

    private fun translateAndDisplay(foreignText: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    Translate the following speech to English concisely. 
                    If it's already in English, summarize or echo it nicely.
                    
                    Text: "\$foreignText"
                """.trimIndent()
                
                val translation = llmClient.chat(listOf(
                    com.jarvis.ai.Message("system", "You are a real-time translator."),
                    com.jarvis.ai.Message("user", prompt)
                )).getOrNull()?.trim() ?: return@launch
                
                launch(Dispatchers.Main) {
                    overlayController.showContextActions(translation, listOf("Stop Interpreter")) { action ->
                        if (action == "Stop Interpreter") {
                            stopInterpreter()
                        }
                    }
                    Log.d(TAG, "BabelFish translated: \$translation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed: \${e.message}")
            }
        }
    }
}
