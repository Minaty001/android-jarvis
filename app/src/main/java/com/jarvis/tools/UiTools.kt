package com.jarvis.tools

import android.content.Context
import com.jarvis.accessibility.UiAutomationManager
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

class UiClickTool(private val context: Context? = null) : Tool {
    override val name: String = "UI_CLICK"
    override val description: String = "Clicks on a UI element matching text, view ID, or content description, or clicks specific coordinates (x, y)."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("target", "string", "Button text, content description, or resource ID to click", required = false),
            ParameterSchema("x", "number", "Optional X screen coordinate", required = false),
            ParameterSchema("y", "number", "Optional Y screen coordinate", required = false)
        ),
        riskLevel = RiskLevel.MEDIUM
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val x = params["x"]?.toFloatOrNull()
        val y = params["y"]?.toFloatOrNull()
        if (x != null && y != null) {
            return UiAutomationManager.clickCoordinates(x, y)
        }

        val target = params["target"] ?: params["text"] ?: params["id"]
            ?: return ToolResult(false, "Target element or coordinates required")

        return UiAutomationManager.click(target)
    }
}

class UiScrollTool(private val context: Context? = null) : Tool {
    override val name: String = "UI_SCROLL"
    override val description: String = "Scrolls active window. Parameter: direction ('down', 'up', 'forward', 'backward')."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("direction", "string", "Direction to scroll: down, up, forward, backward", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val direction = params["direction"] ?: "down"
        val ctx = context ?: return ToolResult(false, "Accessibility Service is not enabled. Please enable Jarvis Automation in Settings.")
        return UiAutomationManager.scroll(ctx, direction)
    }
}

class UiTypeTool(private val context: Context? = null) : Tool {
    override val name: String = "UI_TYPE"
    override val description: String = "Types text into active input field or specific target view. Parameters: text (required), target (optional view ID or text)."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("text", "string", "Text string to enter", required = true),
            ParameterSchema("target", "string", "Optional target view ID or label", required = false)
        ),
        riskLevel = RiskLevel.MEDIUM
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val text = params["text"] ?: return ToolResult(false, "Text parameter required")
        val target = params["target"]
        return UiAutomationManager.type(text, target)
    }
}

class UiInspectTool(private val context: Context? = null) : Tool {
    override val name: String = "UI_INSPECT"
    override val description: String = "Inspects visible UI elements, buttons, inputs, and text on current screen."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val nodes = UiAutomationManager.inspectScreen()
        if (nodes.isEmpty()) {
            return ToolResult(true, "No interactive elements detected or accessibility service is disabled.")
        }

        val sb = StringBuilder("Visible interactive elements (${nodes.size}):\n")
        nodes.take(25).forEach { node ->
            val label = node.text ?: node.contentDescription ?: node.viewId ?: "<unnamed>"
            val kind = when {
                node.isEditable -> "Input"
                node.isClickable -> "Button"
                node.isScrollable -> "Scrollable"
                else -> "Text"
            }
            sb.append("- [$kind] \"$label\" (id: ${node.viewId ?: "none"})\n")
        }
        return ToolResult(true, sb.toString().trim())
    }
}

class UiGlobalActionTool(private val context: Context? = null) : Tool {
    override val name: String = "UI_GLOBAL"
    override val description: String = "Performs device global action: 'back', 'home', 'recents', 'notifications', 'quick_settings', 'screenshot', 'lock'."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("action", "string", "Action to perform: back, home, recents, notifications, quick_settings, screenshot, lock", required = true)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"] ?: params["type"] ?: "back"
        return UiAutomationManager.performGlobalAction(action)
    }
}

class ScreenLockTool(private val context: Context? = null) : Tool {
    override val name: String = "SCREEN_LOCK"
    override val description: String = "Locks the device screen immediately."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        return DisplayWakeEngine.lockDisplay(context)
    }
}

class ScreenUnlockTool(private val context: Context? = null) : Tool {
    override val name: String = "SCREEN_UNLOCK"
    override val description: String = "Wakes up screen and dismisses lock screen overlay. Parameter: pin (optional PIN passcode), action (optional 'wake')."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("pin", "string", "Optional PIN passcode to enter on lock screen", required = false),
            ParameterSchema("action", "string", "Optional action, e.g. 'wake' to turn on display without unlock", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.lowercase()?.trim()
        if (action == "wake" || action == "wake_display" || action == "screen_on") {
            return DisplayWakeEngine.wakeDisplay(context)
        }
        val ctx = context
        var pin = params["pin"] ?: params["code"] ?: params["passcode"]
        if (pin.isNullOrBlank() && ctx != null) {
            pin = runCatching { com.jarvis.ui.data.UiPreferencesStore(ctx).loadSettings().deviceUnlockPin }.getOrNull()
        }
        if (!pin.isNullOrBlank()) {
            return KeyguardUnlockTool(context).execute(mapOf("pin" to pin))
        }
        if (ctx == null) return DisplayWakeEngine.wakeDisplay(null)
        return UiAutomationManager.unlockScreen(ctx, null)
    }
}

class UiGestureTool(private val context: Context? = null) : Tool {
    override val name: String = "UI_GESTURE"
    override val description: String = "Performs touchless gestures like long-press (800ms dwell) or horizontal swipe (left/right)."

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"] ?: "swipe"
        val target = params["target"] ?: params["direction"] ?: "left"
        return UiAutomationManager.performGestureAction(action, target)
    }
}

