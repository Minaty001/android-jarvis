package com.jarvis.tools

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult
import com.jarvis.control.PermissionGate
import com.jarvis.flight.FlightRouter

class FlightTool(private val router: FlightRouter, private val permissionGate: PermissionGate) : Tool {
    override val name = "FLIGHT"
    override val description = "Flight assistant for retrieving GPS-based aviation data."

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"] ?: return ToolResult.Failed("Missing action")

        val intent = ActionIntent(
            domain = "flight_assistant",
            device = "assistant",
            target = "none",
            action = action,
            value = params["value"]
        )

        val authResult = permissionGate.verify(intent)
        if (!authResult.isAuthorized) return ToolResult.Failed("Authentication required: ${authResult.reason}")

        val result = router.route(intent)
        return when (result) {
            is CommandResult.Success -> ToolResult.Success("Flight task executed.")
            is CommandResult.SuccessWithMessage -> ToolResult.Success(result.message)
            is CommandResult.Failure -> ToolResult.Failed(result.error)
        }
    }
}
