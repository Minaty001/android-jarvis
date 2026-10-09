package com.jarvis.tools

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult
import com.jarvis.control.PermissionGate
import com.jarvis.flight.FlightRouter

class FlightSimTool(private val router: FlightRouter, private val permissionGate: PermissionGate) : Tool {
    override val name = "FLIGHT_SIM"
    override val description = "Sends commands to the desktop flight simulator bridge."

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"] ?: return ToolResult.Failed("Missing action")
        val target = params["target"] ?: "unknown"
        val value = params["value"]

        val intent = ActionIntent(
            domain = "flight_simulator",
            device = "simulator",
            target = target,
            action = action,
            value = value
        )

        val authResult = permissionGate.verify(intent)
        if (!authResult.isAuthorized) return ToolResult.Failed("Authentication required: ${authResult.reason}")

        val result = router.route(intent)
        return when (result) {
            is CommandResult.Success -> ToolResult.Success("Simulator command executed.")
            is CommandResult.SuccessWithMessage -> ToolResult.Success(result.message)
            is CommandResult.Failure -> ToolResult.Failed(result.error)
        }
    }
}
