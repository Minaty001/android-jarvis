package com.jarvis.tools

import android.content.Context
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.memory.MemoryStore
import com.jarvis.memory.MemoryType

class SmartVoiceMemoTool(
    private val context: Context? = null,
    private val memoryStore: MemoryStore? = null
) : Tool {
    override val name: String = "VOICE_MEMO"
    override val description: String =
        "Records, indexes, retrieves, and summarizes voice memos and audio notes. Parameters: action ('record', 'list', 'summarize', 'search'), title (memo topic/title), query (search query or note text)."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "VOICE_MEMO",
        description = "Voice memo recorder, local transcript indexer, and audio note summarizer.",
        parameters = emptyList(),
        riskLevel = RiskLevel.LOW
    )

    private val inMemoryMemos = mutableListOf<VoiceMemoEntry>()

    data class VoiceMemoEntry(
        val id: String,
        val title: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "record"
        val title = params["title"]?.trim() ?: params["topic"]?.trim() ?: "Voice Memo"
        val query = params["query"]?.trim() ?: params["content"]?.trim() ?: params["note"]?.trim().orEmpty()

        when (action) {
            "list", "get_all" -> {
                val registeredMemos = getRecordedMemos()
                if (registeredMemos.isEmpty()) {
                    return ToolResult.Success("No voice memos currently recorded.", mapOf("count" to 0))
                }
                val summaryList = registeredMemos.joinToString("\n") { "• [${it.title}]: ${it.content.take(80)}..." }
                return ToolResult.Success(
                    "Recorded Voice Memos (${registeredMemos.size}):\n$summaryList",
                    mapOf("count" to registeredMemos.size, "memos" to registeredMemos.map { it.title })
                )
            }
            "search", "find" -> {
                if (query.isBlank()) {
                    return ToolResult.Failed("Search query missing for voice memos.")
                }
                val registeredMemos = getRecordedMemos()
                val matches = registeredMemos.filter {
                    it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
                }
                if (matches.isEmpty()) {
                    return ToolResult.Success("No voice memos matching '$query' were found.", mapOf("query" to query, "matchCount" to 0))
                }
                val matchText = matches.joinToString("\n") { "• [${it.title}]: ${it.content}" }
                return ToolResult.Success("Found ${matches.size} voice memo(s) matching '$query':\n$matchText", mapOf("matchCount" to matches.size))
            }
            "summarize" -> {
                val registeredMemos = getRecordedMemos()
                if (registeredMemos.isEmpty()) {
                    return ToolResult.Success("No voice memos available to summarize.")
                }
                val targetMemo = registeredMemos.firstOrNull { it.title.contains(title, ignoreCase = true) } ?: registeredMemos.first()
                val summary = "Summary of '${targetMemo.title}': ${targetMemo.content}"
                return ToolResult.Success(summary, mapOf("memoId" to targetMemo.id, "title" to targetMemo.title))
            }
            else -> {
                // Default: record memo with structured semantic processing
                val memoContent = if (query.isNotBlank()) query else "Audio recording transcribed: '$title'"
                val structuredEngine = com.jarvis.memory.VoiceMemoryNotesEngine()
                val processed = structuredEngine.processVoiceNote(memoContent, title)
                val memoId = "memo_${System.currentTimeMillis()}"
                val entry = VoiceMemoEntry(memoId, processed.title, memoContent)
                
                inMemoryMemos.add(0, entry)

                // Persist to MemoryStore if available
                memoryStore?.saveMemory(
                    type = MemoryType.TASK_ARTIFACT,
                    key = "voice_memo:${processed.title}",
                    content = "[${processed.category}] ${processed.originalText}",
                    provenance = "SmartVoiceMemoTool"
                )

                return ToolResult.Success(
                    "Voice memo '${processed.title}' recorded and indexed under category ${processed.category.name}.",
                    mapOf(
                        "id" to memoId,
                        "title" to processed.title,
                        "category" to processed.category.name,
                        "hasDeadline" to processed.hasDeadline,
                        "saved" to true
                    )
                )
            }
        }
    }

    private fun getRecordedMemos(): List<VoiceMemoEntry> {
        val storedFacts = memoryStore?.getAllActiveMemories().orEmpty().filter { it.key.startsWith("voice_memo:") || it.provenance == "SmartVoiceMemoTool" }
        val persistedEntries = storedFacts.map { item ->
            VoiceMemoEntry(
                id = "persisted_${item.id}",
                title = item.key.removePrefix("voice_memo:"),
                content = item.content,
                timestamp = item.createdAt
            )
        }
        return (inMemoryMemos + persistedEntries).distinctBy { it.id }
    }
}
