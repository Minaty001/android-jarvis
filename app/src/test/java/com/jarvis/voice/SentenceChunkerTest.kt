package com.jarvis.voice

import org.junit.Assert.*
import org.junit.Test

class SentenceChunkerTest {

    @Test
    fun testSplitsAtPunctuationImmediately() {
        val emittedChunks = mutableListOf<String>()
        val chunker = SentenceChunker { chunk ->
            emittedChunks.add(chunk)
        }

        // Stream tokens one by one
        chunker.appendToken("Haan ")
        chunker.appendToken("sir, ")
        assertEquals(1, emittedChunks.size)
        assertEquals("Haan sir,", emittedChunks[0])

        chunker.appendToken("maine ")
        chunker.appendToken("torch ")
        chunker.appendToken("on ")
        chunker.appendToken("kar ")
        chunker.appendToken("diya.")
        assertEquals(2, emittedChunks.size)
        assertEquals("maine torch on kar diya.", emittedChunks[1])

        chunker.flush()
        assertEquals(2, emittedChunks.size)
    }

    @Test
    fun testFlushEmitsTrailingTokensWithoutPunctuation() {
        val emittedChunks = mutableListOf<String>()
        val chunker = SentenceChunker { chunk ->
            emittedChunks.add(chunk)
        }

        chunker.appendToken("I am ")
        chunker.appendToken("online now")
        assertEquals(0, emittedChunks.size) // No punctuation yet

        chunker.flush()
        assertEquals(1, emittedChunks.size)
        assertEquals("I am online now", emittedChunks[0])
    }

    @Test
    fun testChunkerSplitsOnLongPhrasesEvenWithoutPunctuation() {
        val emittedChunks = mutableListOf<String>()
        val chunker = SentenceChunker(maxWordsBeforeSplit = 5) { chunk ->
            emittedChunks.add(chunk)
        }

        val tokens = listOf("This ", "is ", "a ", "long ", "sentence ", "without ", "any ", "period")
        tokens.forEach { chunker.appendToken(it) }

        assertTrue(emittedChunks.isNotEmpty())
        chunker.flush()
        val allText = emittedChunks.joinToString(" ")
        assertTrue(allText.contains("This is a long sentence"))
    }
}
