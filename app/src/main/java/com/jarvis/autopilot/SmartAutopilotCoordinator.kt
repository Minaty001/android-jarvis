package com.jarvis.autopilot

import android.content.Context
import com.jarvis.tools.ToolResult
import com.jarvis.tools.AppAutopilotTool

/**
 * Coordinator for higher‑level autopilot flows.
 * Currently delegates to the existing AppAutopilotTool implementation.
 * Future enhancements can add multi‑step orchestration, timeout handling, etc.
 */
class SmartAutopilotCoordinator(private val context: Context) {
    /**
     * Handles an autopilot request.
     * @param params parameters as defined by AppAutopilotTool.
     * @return ToolResult indicating success or failure.
     */
    suspend fun handleAutopilot(params: Map<String, String>): ToolResult {
        // Direct delegation to the existing tool logic (without a coordinator to avoid recursion).
        val tool = AppAutopilotTool(context, null)
        return tool.execute(params)
    }
}
