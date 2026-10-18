package com.jarvis.tools

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.voice.TtsEngine

class ScreenReaderTool(
    private val context: Context? = null,
    private val ttsEngine: TtsEngine? = null
) : Tool {
    private val TAG = "ScreenReaderTool"

    override val name: String = "SCREEN_READER"
    override val description: String = "Extracts and reads visible on-screen text, articles, or messages aloud. Actions: 'read', 'stop', 'summary'."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("action", "string", "Action to perform: read, stop, summary", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.lowercase()?.trim() ?: "read"

        if (action == "stop" || action == "pause" || action == "chup") {
            ttsEngine?.stop()
            return ToolResult.Success("Screen reading stopped.")
        }

        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.Failed("Jarvis Accessibility Service is not running. Enable it in Android Settings -> Accessibility.")

        val root = service.rootInActiveWindow
            ?: return ToolResult.Failed("No active window content detected to read.")

        val textBlocks = mutableListOf<String>()
        val boundsList = mutableListOf<Pair<Rect, String>>()

        extractTextNodes(root, boundsList)

        // Sort primarily top-to-bottom, then left-to-right (natural reading order)
        boundsList.sortWith(compareBy({ it.first.top }, { it.first.left }))

        var lastText = ""
        for ((_, text) in boundsList) {
            val clean = text.trim()
            if (clean.length >= 2 && clean != lastText) {
                textBlocks.add(clean)
                lastText = clean
            }
        }

        if (textBlocks.isEmpty()) {
            return ToolResult.Failed("No readable text found on the current screen.")
        }

        val combinedContent = textBlocks.joinToString(". ")
        val truncatedForSpeech = if (combinedContent.length > 2000) {
            combinedContent.take(2000) + "... and more."
        } else {
            combinedContent
        }

        Log.i(TAG, "Screen reader extracted ${textBlocks.size} blocks (${combinedContent.length} chars)")

        if (ttsEngine != null) {
            ttsEngine.speak(truncatedForSpeech)
        }

        return ToolResult.Success(
            message = truncatedForSpeech,
            data = mapOf("block_count" to textBlocks.size, "total_chars" to combinedContent.length)
        )
    }

    private fun extractTextNodes(node: AccessibilityNodeInfo?, out: MutableList<Pair<Rect, String>>) {
        if (node == null) return

        val pkg = node.packageName?.toString() ?: ""
        // Skip system UI chrome (status bar, navigation bar)
        if (pkg == "com.android.systemui") return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        val content = when {
            !text.isNullOrBlank() -> text
            !desc.isNullOrBlank() && !node.isClickable -> desc
            else -> null
        }

        if (!content.isNullOrBlank()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                out.add(Pair(rect, content))
            }
        }

        for (i in 0 until node.childCount) {
            extractTextNodes(node.getChild(i), out)
        }
    }
}
