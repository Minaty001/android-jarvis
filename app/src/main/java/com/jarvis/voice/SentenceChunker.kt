package com.jarvis.voice

/**
 * Ultra-low latency sentence and phrase chunker.
 * Buffers incoming streaming LLM tokens and yields complete phrases
 * upon encountering clause/sentence boundaries (., ,, ?, !, \n) or
 * reaching maxWordsBeforeSplit. This allows Text-to-Speech synthesis
 * to begin on the first spoken chunk within ~350-400ms without waiting
 * for full generation.
 */
class SentenceChunker(
    private val minTokensForPunctuationSplit: Int = 2,
    private val maxWordsBeforeSplit: Int = 10,
    private val onChunk: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private val punctuationRegex = Regex("(?<=[.!?,;\\n])\\s+")

    @Synchronized
    fun appendToken(token: String) {
        buffer.append(token)
        val currentText = buffer.toString()

        // Check if there's a punctuation boundary
        val lastChar = currentText.trimEnd().lastOrNull()
        val hasPunctuation = lastChar in listOf('.', '!', '?', ',', ';', '\n')

        val words = currentText.trim().split(Regex("\\s+"))

        if (hasPunctuation && words.size >= minTokensForPunctuationSplit) {
            val chunk = currentText.trim()
            if (chunk.isNotBlank()) {
                buffer.clear()
                onChunk(chunk)
            }
        } else if (words.size >= maxWordsBeforeSplit) {
            // Split at the nearest word boundary
            val splitIndex = currentText.lastIndexOf(' ')
            if (splitIndex > 0) {
                val chunk = currentText.substring(0, splitIndex).trim()
                val remainder = currentText.substring(splitIndex + 1)
                buffer.clear()
                buffer.append(remainder)
                if (chunk.isNotBlank()) {
                    onChunk(chunk)
                }
            }
        }
    }

    @Synchronized
    fun flush() {
        val remaining = buffer.toString().trim()
        if (remaining.isNotBlank()) {
            buffer.clear()
            onChunk(remaining)
        }
    }

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
