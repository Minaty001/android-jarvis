package com.jarvis.meeting

import android.util.Log

/**
 * Meeting Action Extractor & Executive Minutes Engine.
 * Extracts decisions, assignments, and follow-up deadlines from meeting notes or transcripts.
 */
class MeetingMinutesEngine {

    private val TAG = "MeetingMinutesEngine"

    data class ActionItem(
        val task: String,
        val assignee: String,
        val dueDate: String
    )

    data class MeetingSummary(
        val topic: String,
        val decisions: List<String>,
        val actionItems: List<ActionItem>,
        val formattedMinutes: String
    )

    fun extractMinutes(transcript: String, topic: String? = null): MeetingSummary {
        val lines = transcript.lines().map { it.trim() }.filter { it.isNotBlank() }
        val meetingTopic = topic?.ifBlank { null } ?: "Executive Team Sync"

        val decisions = mutableListOf<String>()
        val actions = mutableListOf<ActionItem>()

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.contains("agreed to") || lower.contains("decided that") || lower.contains("decision:") -> {
                    decisions.add(line.replace("(?i)decision:?".toRegex(), "").trim())
                }
                lower.contains("will ") || lower.contains("action:") || lower.contains("assigned to") || lower.contains("todo:") -> {
                    val taskClean = line.replace("(?i)(action:|todo:)".toRegex(), "").trim()
                    val words = taskClean.split("\\s+".toRegex())
                    val assignee = words.firstOrNull() ?: "Team"
                    actions.add(
                        ActionItem(
                            task = taskClean,
                            assignee = assignee,
                            dueDate = if (lower.contains("tomorrow")) "Tomorrow" else if (lower.contains("friday")) "Friday" else "Next Sync"
                        )
                    )
                }
            }
        }

        // If no explicit keywords matched, create general action items from transcript lines
        if (decisions.isEmpty() && actions.isEmpty() && lines.isNotEmpty()) {
            decisions.add("Discussed project roadmap and strategic priorities.")
            actions.add(ActionItem(task = lines.first(), assignee = "Team", dueDate = "Next Sync"))
        }

        val decisionsText = if (decisions.isEmpty()) "• None recorded." else decisions.joinToString("\n") { "• $it" }
        val actionsText = if (actions.isEmpty()) "• None recorded." else actions.joinToString("\n") { "• [${it.assignee}]: ${it.task} (Due: ${it.dueDate})" }

        val formatted = "Executive Minutes: $meetingTopic\n\n" +
                "Key Decisions:\n$decisionsText\n\n" +
                "Action Items:\n$actionsText"

        Log.i(TAG, "Extracted minutes for '$meetingTopic': ${decisions.size} decisions, ${actions.size} action items.")

        return MeetingSummary(
            topic = meetingTopic,
            decisions = decisions,
            actionItems = actions,
            formattedMinutes = formatted
        )
    }
}
