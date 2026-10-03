package com.jarvis.agent.skills

import com.jarvis.agent.AgentAction

class AppLauncherSkill : Skill {
    override val id: String = "app_launcher_scanner"
    override val name: String = "Dynamic App Launcher & Scanner Skill"
    override val description: String =
        "Scans installed applications, lists launchable device apps, searches for specific apps by keyword, and counts installed apps."

    override val triggers: List<String> = listOf(
        // English
        "list my apps", "list installed apps", "show all apps", "my apps", "installed apps", "count apps", "how many apps",
        "find app", "search app", "search installed app", "app scanner", "scan apps",
        // Hindi / Hinglish
        "apps dikhao", "kitne apps hain", "saare apps dikhao", "mobile ke apps", "app dhundho", "app search karo"
    )

    override suspend fun canHandle(goal: String, context: SkillContext): Boolean {
        val lower = goal.lowercase().trim()
        return triggers.any { lower.contains(it) } ||
                ((lower.contains("app") || lower.contains("apps")) &&
                 (lower.contains("list") || lower.contains("show") || lower.contains("count") ||
                  lower.contains("find") || lower.contains("search") || lower.contains("dikhao") || lower.contains("dhundho")))
    }

    override suspend fun execute(goal: String, context: SkillContext): SkillResult {
        val lower = goal.lowercase().trim()

        val action = when {
            lower.contains("find") || lower.contains("search") || lower.contains("dhundho") -> {
                val query = goal.replace(Regex("(?i)find app|search app|search installed app for|dhundho", RegexOption.IGNORE_CASE), "").trim()
                AgentAction(type = "APPS_LIST", params = mapOf("action" to "search", "query" to query))
            }
            lower.contains("count") || lower.contains("how many") || lower.contains("kitne") -> {
                AgentAction(type = "APPS_LIST", params = mapOf("action" to "count"))
            }
            lower.contains("scan") || lower.contains("refresh") -> {
                AgentAction(type = "APPS_LIST", params = mapOf("action" to "scan"))
            }
            else -> AgentAction(type = "APPS_LIST", params = mapOf("action" to "list"))
        }

        return SkillResult(
            handled = true,
            proposedAction = action,
            explanation = "Handling installed app scanner action: $goal"
        )
    }
}
