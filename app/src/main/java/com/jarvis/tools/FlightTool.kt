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

        if (action == "track_flight" || action == "gate_info" || action == "status") {
            val query = params["flight"] ?: params["value"] ?: params["number"] ?: "AI 101"
            val tracker = com.jarvis.flight.FlightTrackerEngine()
            val details = tracker.trackFlight(query)
            val msg = "${details.airline} ${details.flightNumber} Status: ${details.status}\n" +
                    "• Route: ${details.origin} -> ${details.destination}\n" +
                    "• Terminal: ${details.terminal}, Gate: ${details.gate}\n" +
                    "• Baggage Claim: ${details.baggageClaim}"
            return ToolResult.Success(
                message = msg,
                data = mapOf(
                    "flight" to details.flightNumber,
                    "terminal" to details.terminal,
                    "gate" to details.gate,
                    "status" to details.status
                )
            )
        }

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
