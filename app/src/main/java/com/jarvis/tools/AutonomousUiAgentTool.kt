package com.jarvis.tools

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.accessibility.ScreenVisionManager
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import kotlinx.coroutines.delay
import org.json.JSONObject

class AutonomousUiAgentTool(
    private val context: Context,
    private val llmClient: LlmClient?
) : Tool {

    override val name = "AUTONOMOUS_UI_AGENT"
    override val description = "Executes multi-step UI reasoning and interaction using LLM vision"

    private val TAG = "AutonomousUiAgent"
    private val MAX_STEPS = 8

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val goal = params["goal"]?.trim()
        if (goal.isNullOrBlank()) {
            return ToolResult.Failed("A goal is required for the Autonomous UI Agent.")
        }

        if (llmClient == null) {
            return ToolResult.Failed("LLM Client is required for Autonomous UI reasoning.")
        }

        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.Failed("Accessibility Service is not enabled. Please enable Jarvis Automation in Settings.")

        var currentStep = 0
        var lastAction = "started"
        var lastResult = "None"
        val maxSteps = params["max_steps"]?.toIntOrNull() ?: MAX_STEPS

        while (currentStep < maxSteps) {
            currentStep++
            Log.i(TAG, "--- Agent Step $currentStep/$maxSteps for Goal: '\$goal' ---")

            // Wait for UI to settle
            delay(1500)

            // 1. Capture screen
            val capture = ScreenVisionManager.captureScreen()

            // 2. Build prompt
            val prompt = """
                You are JARVIS Autonomous UI Agent.
                Your Goal: "\$goal"
                Step: \$currentStep/\$maxSteps
                Last Action Taken: \$lastAction
                Result of Last Action: \$lastResult

                Analyze the provided UI Context and image (if available).
                Decide the IMMEDIATE NEXT ACTION to take to progress towards the goal.
                
                You must output ONLY valid JSON in this exact format, with NO markdown formatting or other text:
                {
                  "thought": "brief reasoning about what to do next based on screen state",
                  "action": "tap" | "type" | "scroll_down" | "scroll_up" | "home" | "back" | "done" | "fail",
                  "element_index": 123, // Use ONLY if action is 'tap' or 'type'. Refers to the index in the UI Context list.
                  "text": "text to type", // Use ONLY if action is 'type'.
                  "reason": "final summary" // Use ONLY if action is 'done' or 'fail'.
                }
            """.trimIndent()

            // 3. Request LLM decision
            val llmResponse = llmClient.chatVision(
                prompt = prompt,
                base64ImageUrl = capture.base64Image,
                uiContext = capture.uiTextTree
            )

            if (!llmResponse.isSuccess) {
                return ToolResult.Failed("LLM reasoning failed at step \$currentStep: \${llmResponse.exceptionOrNull()?.message}")
            }

            val rawJson = llmResponse.getOrNull() ?: ""
            Log.d(TAG, "Agent Output: \$rawJson")

            // Parse JSON
            val actionData = try {
                val jsonString = if (rawJson.contains("{")) {
                    rawJson.substring(rawJson.indexOf("{"), rawJson.lastIndexOf("}") + 1)
                } else rawJson
                JSONObject(jsonString)
            } catch (e: Exception) {
                lastAction = "parsing error"
                lastResult = "Failed to parse JSON: \${e.message}"
                Log.w(TAG, "JSON Parse Error on: \$rawJson")
                continue
            }

            val action = actionData.optString("action")
            val elementIndex = actionData.optInt("element_index", -1)
            val textToType = actionData.optString("text")
            val reason = actionData.optString("reason")

            lastAction = action

            // 4. Execute Action
            when (action) {
                "done" -> {
                    return ToolResult.Success("Goal achieved: \$reason")
                }
                "fail" -> {
                    return ToolResult.Failed("Agent determined it cannot proceed: \$reason")
                }
                "home" -> {
                    val success = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    lastResult = if (success) "Navigated Home" else "Failed to navigate Home"
                }
                "back" -> {
                    val success = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    lastResult = if (success) "Navigated Back" else "Failed to navigate Back"
                }
                "scroll_down" -> {
                    val success = service.scrollDown()
                    lastResult = if (success) "Scrolled Down" else "Scroll Down Failed"
                }
                "scroll_up" -> {
                    val success = service.scrollUp()
                    lastResult = if (success) "Scrolled Up" else "Scroll Up Failed"
                }
                "tap", "click" -> {
                    if (elementIndex == -1) {
                        lastResult = "No element_index provided for tap."
                        continue
                    }
                    val targetEl = capture.elements.find { it.index == elementIndex }
                    if (targetEl != null) {
                        val cx = targetEl.bounds.centerX().toFloat()
                        val cy = targetEl.bounds.centerY().toFloat()
                        val success = service.clickCoordinatesAsync(cx, cy)
                        lastResult = if (success) "Tapped element \$elementIndex at (\$cx, \$cy)" else "Failed to tap element \$elementIndex"
                    } else {
                        lastResult = "Element index \$elementIndex not found on screen."
                    }
                }
                "type" -> {
                    if (elementIndex == -1) {
                        lastResult = "No element_index provided for type."
                        continue
                    }
                    val targetEl = capture.elements.find { it.index == elementIndex }
                    if (targetEl != null) {
                        // First tap to focus
                        val cx = targetEl.bounds.centerX().toFloat()
                        val cy = targetEl.bounds.centerY().toFloat()
                        service.clickCoordinatesAsync(cx, cy)
                        delay(800) // Wait for keyboard

                        // Find the focused node to set text, or use the global action
                        val editableNode = service.findNodeByViewId(targetEl.viewId ?: "") ?: service.findNodeByText(targetEl.text ?: "")
                        if (editableNode != null && editableNode.isEditable) {
                            val bundle = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                            }
                            val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                            lastResult = if (success) "Typed text into element \$elementIndex" else "Failed to set text for element \$elementIndex"
                        } else {
                            // Fallback if node lost
                            lastResult = "Tapped element \$elementIndex but could not set text."
                        }
                    } else {
                        lastResult = "Element index \$elementIndex not found on screen."
                    }
                }
                else -> {
                    lastResult = "Unknown action type: \$action"
                }
            }
        }

        return ToolResult.Failed("Max steps (\$maxSteps) reached before goal was completed.")
    }
}
