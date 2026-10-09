package com.jarvis.smart_home

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult

class SmartHomeRouter(private val haClient: HomeAssistantClient) {
    
    suspend fun route(intent: ActionIntent): CommandResult {
        return when (intent.device) {
            "light" -> handleLight(intent)
            "lock" -> handleLock(intent)
            "scene" -> handleScene(intent)
            else -> CommandResult.Failure("Unknown smart home device: ${intent.device}")
        }
    }

    private suspend fun handleLight(intent: ActionIntent): CommandResult {
        // e.g. intent.target = "bedroom" -> "light.bedroom"
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
