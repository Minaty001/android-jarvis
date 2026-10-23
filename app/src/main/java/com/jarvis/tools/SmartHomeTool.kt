package com.jarvis.tools

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult
import com.jarvis.control.PermissionGate
import com.jarvis.smart_home.HomeAssistantClient

class SmartHomeTool(
    private val haClient: HomeAssistantClient,
    private val permissionGate: PermissionGate
) : Tool {
    override val name = "SMART_HOME"
    override val description = "Controls smart home devices like lights and locks via Home Assistant."
    override val policy = ToolPolicy(requiresConfirmation = false) // Handled dynamically by PermissionGate

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val device = params["device"] ?: return ToolResult.Failed("Missing device type")
        val target = params["target"] ?: "all"
        val action = params["action"] ?: "turn_on"
        val value = params["value"]
        val requiresConfirmation = params["requiresConfirmation"]?.toBoolean() ?: false

        val intent = ActionIntent(
            domain = "smart_home",
            device = device,
            target = target,
            action = action,
            value = value,
            requiresConfirmation = requiresConfirmation
        )

        val authResult = permissionGate.verify(intent)
        if (!authResult.isAuthorized) return ToolResult.Failed("Authentication required: ${authResult.reason}")

        val result = route(intent)
        return when (result) {
            is CommandResult.Success -> ToolResult.Success("Successfully executed smart home command.")
            is CommandResult.SuccessWithMessage -> ToolResult.Success(result.message)
            is CommandResult.Failure -> ToolResult.Failed(result.error)
        }
    }

    private suspend fun route(intent: ActionIntent): CommandResult {
        return when (intent.device) {
            "light" -> handleLight(intent)
            "lock" -> handleLock(intent)
            "scene" -> handleScene(intent)
            else -> CommandResult.Failure("Unknown smart home device: ${intent.device}")
        }
    }

    private suspend fun handleLight(intent: ActionIntent): CommandResult {
        val entityId = "light.${intent.target.lowercase().replace(" ", "_")}"
        return when (intent.action) {
            "turn_on", "on" -> {
                val success = haClient.callService("light", "turn_on", entityId)
                if (success) CommandResult.Success else CommandResult.Failure("Failed to turn on $entityId")
            }
            "turn_off", "off" -> {
                val success = haClient.callService("light", "turn_off", entityId)
                if (success) CommandResult.Success else CommandResult.Failure("Failed to turn off $entityId")
            }
            "set_brightness" -> {
                val brightnessPct = (intent.value as? Number)?.toInt() ?: 100
                val brightnessInt = (brightnessPct / 100.0 * 255).toInt()
                val success = haClient.callService("light", "turn_on", entityId, mapOf("brightness" to brightnessInt))
                if (success) CommandResult.Success else CommandResult.Failure("Failed to set brightness")
            }
            "set_color" -> {
                val color = intent.value as? String ?: "white"
                val success = haClient.callService("light", "turn_on", entityId, mapOf("color_name" to color))
                if (success) CommandResult.Success else CommandResult.Failure("Failed to set color")
            }
            else -> CommandResult.Failure("Unknown light action: ${intent.action}")
        }
    }

    private suspend fun handleLock(intent: ActionIntent): CommandResult {
        val entityId = "lock.${intent.target.lowercase().replace(" ", "_")}"
        return when (intent.action) {
            "unlock" -> {
                val success = haClient.callService("lock", "unlock", entityId)
                if (success) CommandResult.Success else CommandResult.Failure("Failed to unlock $entityId")
            }
            "lock" -> {
                val success = haClient.callService("lock", "lock", entityId)
                if (success) CommandResult.Success else CommandResult.Failure("Failed to lock $entityId")
            }
            "status" -> {
                val state = haClient.getEntityState(entityId)
                if (state != null) {
                    CommandResult.SuccessWithMessage("The door is currently ${state.state}")
                } else {
                    CommandResult.Failure("Could not get status for $entityId")
                }
            }
            else -> CommandResult.Failure("Unknown lock action: ${intent.action}")
        }
    }

    private suspend fun handleScene(intent: ActionIntent): CommandResult {
        val entityId = "scene.${intent.target.lowercase().replace(" ", "_")}"
        val success = haClient.callService("scene", "turn_on", entityId)
        return if (success) CommandResult.Success else CommandResult.Failure("Failed to activate scene $entityId")
    }
}
