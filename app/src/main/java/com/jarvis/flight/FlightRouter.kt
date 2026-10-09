package com.jarvis.flight

import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult

class FlightRouter(
    private val flightAssistant: FlightAssistant,
    private val simulatorBridge: SimulatorBridge
) {
    suspend fun route(intent: ActionIntent): CommandResult {
        return if (intent.domain == "flight_simulator") {
            simulatorBridge.executeSimCommand(intent)
        } else {
            flightAssistant.provideData(intent)
        }
    }
}
