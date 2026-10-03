package com.jarvis.agent.skills

import com.jarvis.agent.AgentAction

class RoutineSkill : Skill {
    override val id: String = "context_smart_routines"
    override val name: String = "Smart Automation Routines Skill"
    override val description: String =
        "Executes and manages context-aware automation routines such as bedtime wind-down, low battery saver, work arrival, and home arrival."

    override val triggers: List<String> = listOf(
        // English
        "bedtime routine", "night routine", "sleep routine",
        "work routine", "office routine", "work mode",
        "home routine", "home arrival",
        "low battery routine", "battery routine", "battery saver routine",
        "run routine", "trigger routine", "start routine", "execute routine",
        "list routines", "show routines", "my routines", "active automations",
        "smart routines", "automation routines", "context routines",
        "remind me", "schedule task", "schedule routine", "add routine", "create routine",
        "delete routine", "remove routine",
        // Hindi / Hinglish
        "bedtime routine chalu karo", "night routine chalu karo", "so jao",
        "work routine run karo", "office mode", "kaam ka routine",
        "ghar ka routine", "home routine chalu karo",
        "battery routine", "battery saver mode on karo",
        "routine run karo", "routine chalu karo", "routine start karo",
        "kya automations hain", "automations dikhao", "routines batao",
        "remind karo", "daily task lagao", "routine banao"
    )

    override suspend fun canHandle(goal: String, context: SkillContext): Boolean {
        val lower = goal.lowercase().trim()
        return triggers.any { lower.contains(it) } ||
                ((lower.contains("routine") || lower.contains("automation") || lower.contains("remind")) &&
                        (lower.contains("run") || lower.contains("start") || lower.contains("trigger") ||
                         lower.contains("chalu") || lower.contains("list") || lower.contains("show") ||
                         lower.contains("create") || lower.contains("add") || lower.contains("schedule") || lower.contains("delete")))
    }

    override suspend fun execute(goal: String, context: SkillContext): SkillResult {
        val lower = goal.lowercase().trim()

        val action = when {
            lower.contains("create") || lower.contains("add") || lower.contains("schedule") || lower.contains("remind me") || lower.contains("remind karo") || lower.contains("task lagao") -> {
                // Extract time if present (e.g., 10:00, 10 am, 8 pm)
                val timeMatch = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""").find(lower)
                var timeStr = "08:00"
                if (timeMatch != null) {
                    val hStr = timeMatch.groupValues[1]
                    val mStr = timeMatch.groupValues.getOrNull(2)?.ifBlank { "00" } ?: "00"
                    val amPm = timeMatch.groupValues.getOrNull(3)?.lowercase()
                    var hour = hStr.toIntOrNull() ?: 8
                    if (amPm == "pm" && hour < 12) hour += 12
                    if (amPm == "am" && hour == 12) hour = 0
                    timeStr = String.format(java.util.Locale.US, "%02d:%02d", hour, mStr.toIntOrNull() ?: 0)
                }

                val cleanGoal = goal.replace(Regex("(?i)remind me to|schedule routine|create routine|add routine|at \\d+.*", RegexOption.IGNORE_CASE), "").trim().ifBlank { "Daily Reminder" }
                AgentAction(
                    type = "ROUTINE_MANAGE",
                    params = mapOf("action" to "create", "name" to cleanGoal, "time" to timeStr, "speech" to "Reminder: $cleanGoal")
                )
            }
            lower.contains("delete") || lower.contains("remove") || lower.contains("hatao") -> {
                val name = goal.replace(Regex("(?i)delete routine|remove routine|hatao", RegexOption.IGNORE_CASE), "").trim()
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "delete", "name" to name))
            }
            lower.contains("bedtime") || lower.contains("night") || lower.contains("sleep") || lower.contains("so jao") -> {
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "run", "routine_id" to "routine_bedtime"))
            }
            lower.contains("work") || lower.contains("office") || lower.contains("kaam") -> {
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "run", "routine_id" to "routine_work_mode"))
            }
            lower.contains("home") || lower.contains("ghar") -> {
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "run", "routine_id" to "routine_home_arrival"))
            }
            lower.contains("battery") -> {
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "run", "routine_id" to "routine_low_battery"))
            }
            lower.contains("list") || lower.contains("show") || lower.contains("batao") || lower.contains("kya") -> {
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "list"))
            }
            else -> {
                AgentAction(type = "ROUTINE_MANAGE", params = mapOf("action" to "run", "name" to goal))
            }
        }

        return SkillResult(
            handled = true,
            proposedAction = action,
            explanation = "Handling contextual automation routine: $goal"
        )
    }
}
