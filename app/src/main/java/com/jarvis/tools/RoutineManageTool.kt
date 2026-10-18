package com.jarvis.tools

import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.routine.SmartRoutineEngine

class RoutineManageTool(private val routineEngine: SmartRoutineEngine) : Tool {
    override val name: String = "ROUTINE_MANAGE"
    override val description: String =
        "Manages routines using action=list|run|enable|disable|delete|prepare_create. Creation needs name, trigger_type, trigger_parameter, and actions_json (JSON array of ordered {toolName,params,description} actions). Supported daily triggers: TIME_SCHEDULE, WIFI_CONNECTED, WIFI_DISCONNECTED, BATTERY_LEVEL_DROPS_BELOW, BATTERY_FULL, CHARGER_CONNECTED, CHARGER_DISCONNECTED. Allowed actions: SPEAK text; DEVICE_SETTINGS set_volume/set_ringer; MEDIA_CONTROL action=stop; SYSTEM_SWITCHBOARD action=wifi|bt|led and target=on|off. Never create unsupported actions; user must say yes to the read-back before save."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "ROUTINE_MANAGE",
        description = "Lists, triggers, or configures smart contextual automation routines.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "list"
        val targetName = params["routine_id"] ?: params["name"] ?: params["routine"] ?: ""

        return when (action) {
            "list" -> {
                val routines = routineEngine.getAllRoutines()
                if (routines.isEmpty()) {
                    ToolResult.Success("No automation routines configured.")
                } else {
                    val summary = buildString {
                        append("Configured Smart Routines (${routines.size}):\n")
                        routines.forEachIndexed { idx, r ->
                            val status = if (r.isEnabled) "ACTIVE" else "DISABLED"
                            append("${idx + 1}. ${r.name} [$status] - ${r.description} (Trigger: ${r.trigger.type.name})\n")
                        }
                    }.trim()
                    ToolResult.Success(
                        message = summary,
                        data = mapOf("count" to routines.size, "routines" to routines.map { it.name })
                    )
                }
            }

            "run", "trigger", "execute" -> {
                if (targetName.isBlank()) {
                    return ToolResult.Failed("Specify the routine name or ID to run (e.g. 'bedtime', 'low_battery', 'work_mode')")
                }
                val routine = routineEngine.getRoutine(targetName)
                    ?: return ToolResult.Failed("No routine found matching '$targetName'")

                val execResult = routineEngine.executeRoutine(routine, isManual = true)
                if (execResult.executed) {
                    ToolResult.Success(
                        message = "Triggered routine '${routine.name}' successfully. Executed ${execResult.executedActionsCount} actions.",
                        data = mapOf("routine" to routine.name, "actions_executed" to execResult.executedActionsCount)
                    )
                } else {
                    ToolResult.Failed("Could not fully execute '${routine.name}': ${execResult.reason}. ${execResult.errors.joinToString("; ")}")
                }
            }

            "enable" -> {
                if (targetName.isBlank()) return ToolResult.Failed("Specify routine name or ID to enable")
                val success = routineEngine.enableRoutine(targetName, true)
                if (success) {
                    ToolResult.Success("Routine enabled: $targetName")
                } else {
                    ToolResult.Failed("Routine not found: $targetName")
                }
            }

            "disable" -> {
                if (targetName.isBlank()) return ToolResult.Failed("Specify routine name or ID to disable")
                val success = routineEngine.enableRoutine(targetName, false)
                if (success) {
                    ToolResult.Success("Routine disabled: $targetName")
                } else {
                    ToolResult.Failed("Routine not found: $targetName")
                }
            }

            "prepare_create" -> {
                val routine = runCatching {
                    com.jarvis.routine.RoutineDraftValidator.create(params["name"].orEmpty(), params["trigger_type"].orEmpty(), params["trigger_parameter"].orEmpty(), params["actions_json"].orEmpty())
                }.getOrElse { return ToolResult.Failed(it.message ?: "Invalid routine draft.") }
                if (!routineEngine.setPendingDraft(routine)) return ToolResult.Failed("A routine draft is already waiting for confirmation. Confirm or cancel it first.")
                val trigger = "${routine.trigger.type.name.lowercase().replace('_', ' ')} ${routine.trigger.parameter}".trim()
                val actions = routine.actions.mapIndexed { i, a -> "${i + 1}. ${a.description.ifBlank { a.toolName }} ${a.params}" }.joinToString("; ")
                ToolResult.Success("Draft: ${routine.name}; daily trigger: $trigger; actions: $actions. Say yes to confirm and save or cancel to discard.")
            }

            "delete", "remove" -> {
                if (targetName.isBlank()) return ToolResult.Failed("Specify routine name or ID to delete")
                val success = routineEngine.deleteRoutine(targetName)
                if (success) {
                    ToolResult.Success("Deleted routine: $targetName")
                } else {
                    ToolResult.Failed("Routine not found: $targetName")
                }
            }

            else -> ToolResult.Failed("Unknown routine action: '$action'. Supported: list, run, enable, disable, prepare_create, delete.")
        }
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        if (!result.success) {
            return VerificationResult.failure("Routine action failed: ${result.message}")
        }
        val action = params["action"]?.trim()?.lowercase() ?: "list"
        return when (action) {
            "run", "trigger", "execute" ->
                if (result.data.containsKey("actions_executed")) {
                    VerificationResult.success("Routine '${result.data["routine"]}' executed (${result.data["actions_executed"]} actions)", mapOf("outcome" to "EXECUTED"))
                } else {
                    VerificationResult.unknown("Routine run reported without an action count", mapOf("outcome" to "UNPARSED"))
                }
            "list" ->
                if (result.data.containsKey("count")) VerificationResult.success("Routine list generated", mapOf("outcome" to "REPORT_GENERATED"))
                else VerificationResult.unknown("Routine list returned without a count", mapOf("outcome" to "UNPARSED"))
            "enable", "disable" -> VerificationResult.success("Routine $action confirmed by engine", mapOf("action" to action, "outcome" to "CONFIRMED"))
            "prepare_create" -> VerificationResult.success("Routine draft awaits spoken confirmation", mapOf("outcome" to "DRAFT_PENDING"))
            else -> VerificationResult.unknown("No verifier for routine action '$action'", mapOf("outcome" to "UNKNOWN_ACTION"))
        }
    }
}
