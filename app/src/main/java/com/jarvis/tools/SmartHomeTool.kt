package com.jarvis.tools

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult
import com.jarvis.control.PermissionGate
import com.jarvis.smart_home.SmartHomeRouter

class SmartHomeTool(private val router: SmartHomeRouter, private val permissionGate: PermissionGate) : Tool {
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

        val result = router.route(intent)
        return when (result) {
            is CommandResult.Success -> ToolResult.Success("Successfully executed smart home command.")
            is CommandResult.SuccessWithMessage -> ToolResult.Success(result.message)
            is CommandResult.Failure -> ToolResult.Failed(result.error)
        }
    }
}
