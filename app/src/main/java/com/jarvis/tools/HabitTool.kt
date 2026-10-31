package com.jarvis.tools

import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.habit.HabitManager

class HabitTool(
    private val habitManager: HabitManager
) : Tool {

    override val name: String = "HABIT_LIST"
    override val description: String =
        "Inspects and suggests learned user habits. Actions: 'list' (shows all learned habits), 'suggest' (provides proactive suggestions based on current context)."

    override val policy: ToolPolicy = ToolPolicy(
        idempotent = true,
        retryable = true,
        riskLevel = RiskLevel.LOW
    )

    val metadata = ToolMetadata(
        name = "HABIT_LIST",
        description = "Provides status and suggestions over user habit profiles.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action: list, suggest", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "list"

        return when (action) {
            "suggest", "recommendation" -> {
                val suggestions = habitManager.getProactiveSuggestions()
                if (suggestions.isEmpty()) {
                    ToolResult.Success(
                        message = "No high-confidence habit suggestions at this time.",
                        data = mapOf("count" to 0)
                    )
                } else {
                    val summary = buildString {
                        append("Proactive Habit Suggestions (${suggestions.size}):\n")
                        suggestions.forEachIndexed { idx, habit ->
                            append("${idx + 1}. ${habit.description} (Category: ${habit.category.name})\n")
                        }
                    }.trim()
                    ToolResult.Success(
                        message = summary,
                        data = mapOf("count" to suggestions.size, "suggestions" to suggestions.map { it.description })
                    )
                }
            }

            else -> {
                val habits = habitManager.getAllHabits()
                if (habits.isEmpty()) {
                    ToolResult.Success(
                        message = "No learned user habits recorded yet. As you use your device, Jarvis automatically learns your routines.",
                        data = mapOf("count" to 0)
                    )
                } else {
                    val summary = buildString {
                        append("Learned User Habits (${habits.size}):\n")
                        habits.take(10).forEachIndexed { idx, habit ->
                            val confidencePct = (habit.confidenceScore * 100).toInt()
                            append("${idx + 1}. ${habit.description} [${confidencePct}% confidence, freq=${habit.frequency}]\n")
                        }
                    }.trim()
                    ToolResult.Success(
                        message = summary,
                        data = mapOf("count" to habits.size, "habits" to habits.map { it.description })
                    )
                }
            }
        }
    }
}
