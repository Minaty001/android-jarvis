package com.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySummarizerTest {
    @Test
    fun transcriptContainsTheStoredDialogue() {
        val transcript = formatSessionTranscript(
            listOf(
                ConversationRecord(1, "session_1", "user", "Remember that I prefer jazz.", 1),
                ConversationRecord(2, "session_1", "assistant", "I will remember that.", 2)
            )
        )

        assertTrue(transcript.contains("user: Remember that I prefer jazz."))
        assertTrue(transcript.contains("assistant: I will remember that."))
    }

    @Test
    fun emptyAndNoSummaryResponsesAreNotStored() {
        assertNull(usableSessionSummary(null))
        assertNull(usableSessionSummary("  "))
        assertNull(usableSessionSummary("NONE"))
        assertEquals("Keep this preference.", usableSessionSummary("  Keep this preference.  "))
    }
}
