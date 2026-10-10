package com.jarvis.memory

import android.util.Log

/**
 * Smart Voice Memory Notes Engine.
 * Parses spoken notes into structured records, extracts action items and entities,
 * and tags notes into contextual buckets (IDEA, ACTION_ITEM, MEETING_NOTE, GENERAL).
 */
class VoiceMemoryNotesEngine {

    private val TAG = "VoiceMemoryNotes"

    enum class NoteCategory {
        ACTION_ITEM,
        IDEA,
        MEETING_NOTE,
        GENERAL
    }

    data class StructuredNote(
        val originalText: String,
        val title: String,
        val category: NoteCategory,
        val keywords: List<String>,
        val hasDeadline: Boolean
    )

    fun processVoiceNote(rawText: String, requestedTitle: String? = null): StructuredNote {
        val lower = rawText.lowercase().trim()

        val isActionItem = lower.startsWith("todo") ||
                lower.startsWith("task") ||
                lower.contains("need to") ||
                lower.contains("have to") ||
                lower.contains("remember to")

        val isMeeting = lower.contains("meeting") ||
                lower.contains("discussed with") ||
                lower.contains("sync with") ||
                lower.contains("call with")

        val isIdea = lower.contains("idea") ||
                lower.contains("what if") ||
                lower.contains("concept") ||
                lower.contains("brainstorm")

        val category = when {
            isActionItem -> NoteCategory.ACTION_ITEM
            isMeeting -> NoteCategory.MEETING_NOTE
            isIdea -> NoteCategory.IDEA
            else -> NoteCategory.GENERAL
        }

        val hasDeadline = lower.contains("tomorrow") ||
                lower.contains("today") ||
                lower.contains("by monday") ||
                lower.contains("by friday") ||
                lower.contains("at ") ||
                lower.contains("pm") ||
                lower.contains("am")

        val title = requestedTitle?.ifBlank { null } ?: when (category) {
            NoteCategory.ACTION_ITEM -> "Action: " + rawText.take(30).trim()
            NoteCategory.MEETING_NOTE -> "Meeting: " + rawText.take(30).trim()
            NoteCategory.IDEA -> "Idea: " + rawText.take(30).trim()
            NoteCategory.GENERAL -> rawText.take(30).trim()
        }

        val words = lower.split("\\s+".toRegex())
            .filter { it.length > 3 }
            .distinct()
            .take(5)

        Log.i(TAG, "Processed voice note: title='$title', category=$category")

        return StructuredNote(
            originalText = rawText,
            title = title,
            category = category,
            keywords = words,
            hasDeadline = hasDeadline
        )
    }
}
