package com.jarvis.agent.skills

import com.jarvis.agent.AgentAction
import com.jarvis.media.BrowserSessionManager

/**
 * Autonomous Deep Web & Browser Agent Skill.
 * Translates spoken natural language into web lookups, Wikipedia queries,
 * direct browser navigation, and session tracking.
 */
class BrowserSkill : Skill {
    override val id: String = "browser_agent"
    override val name: String = "Autonomous Web & Browser Agent Skill"
    override val description: String =
        "Performs real-time web searches, Wikipedia queries, direct browser URL navigation, and session history management."

    override val triggers: List<String> = listOf(
        // English
        "search web for", "search the web for", "search web", "web search",
        "search online for", "search online", "online search", "google this",
        "google search", "browse to", "open website", "open url", "visit website",
        "navigate to website", "look up on web", "find on web", "internet search", "browser history",
        // Hindi / Hinglish
        "internet pe search karo", "net pe search karo", "online search karo",
        "web pe search karo", "google pe dhoondo", "google par search karo",
        "website kholo", "link kholo", "browser me kholo", "chrome me kholo"
    )

    private val urlRegex = Regex("""(https?://\S+|www\.\S+|[a-zA-Z0-9-]+\.(?:com|org|net|io|ai|in|edu|gov)(?:/\S*)?)""", RegexOption.IGNORE_CASE)

    private fun isFileCreationIntent(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("file") || lower.contains("file ka naam") || lower.contains("filename") ||
                lower.contains("banao") || lower.contains("bana do") || lower.contains("create file") ||
                lower.contains("save kar") || lower.contains("rakhna")
    }

    override suspend fun canHandle(goal: String, context: SkillContext): Boolean {
        val lower = goal.lowercase().trim()
        if (triggers.any { lower.contains(it) }) return true
        if (isFileCreationIntent(goal) && !goal.contains("http://", ignoreCase = true) && !goal.contains("https://", ignoreCase = true) && !goal.contains("www.", ignoreCase = true)) {
            return false
        }
        if (urlRegex.containsMatchIn(goal)) return true
        if (lower.startsWith("browse ") || lower.startsWith("visit ") || lower.startsWith("google ")) return true
        return false
    }

    override suspend fun execute(goal: String, context: SkillContext): SkillResult {
        val lower = goal.lowercase().trim()

        if (lower.contains("browser") && lower.contains("history")) {
            val summary = BrowserSessionManager.getHistorySummary()
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("SPEAK", mapOf("text" to summary)),
                explanation = "Retrieving browser session history summary"
            )
        }

        val urlMatch = if (isFileCreationIntent(goal) && !goal.contains("http://", ignoreCase = true) && !goal.contains("https://", ignoreCase = true) && !goal.contains("www.", ignoreCase = true)) {
            null
        } else {
            urlRegex.find(goal)
        }

        if (urlMatch != null) {
            var rawUrl = urlMatch.value.trim()
            if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
                rawUrl = "https://$rawUrl"
            }
            BrowserSessionManager.recordNavigation(rawUrl)
            return SkillResult(
                handled = true,
                proposedAction = AgentAction(
                    type = "SEARCH_WEB",
                    params = mapOf("query" to rawUrl, "openBrowser" to "true"),
                    expectedOutcome = "Navigated to website: $rawUrl"
                ),
                explanation = "Navigating to website: $rawUrl"
            )
        }

        // Clean query extraction
        var cleanQuery = goal
        for (trigger in triggers) {
            if (cleanQuery.contains(trigger, ignoreCase = true)) {
                cleanQuery = cleanQuery.replace(Regex("(?i)" + Regex.escape(trigger)), "")
            }
        }
        cleanQuery = cleanQuery
            .replace(Regex("(?i)^(please|can you|jarvis|hey jarvis|karo|bhai|dhoondo|kholna)\\s+"), "")
            .replace(Regex("(?i)\\s+(please|karo|bhai|kholna)$"), "")
            .trim()

        if (cleanQuery.isBlank()) {
            cleanQuery = goal
        }

        BrowserSessionManager.recordNavigation(cleanQuery)

        val shouldOpenInBrowser = lower.contains("open in browser") ||
                lower.contains("browser") ||
                lower.contains("chrome") ||
                lower.contains("website kholo") ||
                lower.contains("kholo")

        return SkillResult(
            handled = true,
            proposedAction = AgentAction(
                type = "SEARCH_WEB",
                params = mapOf(
                    "query" to cleanQuery,
                    "openBrowser" to shouldOpenInBrowser.toString()
                ),
                expectedOutcome = "Executed web search for: $cleanQuery"
            ),
            explanation = "Executing web search: $cleanQuery"
        )
    }
}
