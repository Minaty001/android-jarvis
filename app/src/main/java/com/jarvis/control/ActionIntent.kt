package com.jarvis.control

data class ActionIntent(
    val domain: String,       // e.g., "smart_home", "flight_simulator", "flight_assistant"
    val device: String,       // e.g., "light", "lock"
    val target: String,       // e.g., "bedroom", "front_door", "VIDP"
    val action: String,       // e.g., "turn_on", "unlock", "set_destination"
    val value: Any? = null,   // e.g., 40
    val requiresConfirmation: Boolean = false
)

data class AuthResult(
    val isAuthorized: Boolean,
    val reason: String? = null
)

sealed class CommandResult {
    object Success : CommandResult()
    data class Failure(val error: String) : CommandResult()
    data class SuccessWithMessage(val message: String) : CommandResult()
}
