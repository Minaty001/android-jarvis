package com.jarvis.cognitive.memory

import com.jarvis.cognitive.CognitiveConfig
import com.jarvis.cognitive.CognitiveEvent
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface JciMemoryBackend {
    fun getEntries(category: JciMemoryCategory): List<JciMemoryEntry>
    fun saveEntry(entry: JciMemoryEntry)
    fun removeEntry(id: String)
    fun clearCategory(category: JciMemoryCategory)
}

class InMemoryJciMemoryBackend : JciMemoryBackend {
    private val store = ConcurrentHashMap<JciMemoryCategory, MutableMap<String, JciMemoryEntry>>()

    override fun getEntries(category: JciMemoryCategory): List<JciMemoryEntry> {
        val map = store[category] ?: return emptyList()
        return synchronized(map) { map.values.toList() }
    }

    override fun saveEntry(entry: JciMemoryEntry) {
        val map = store.computeIfAbsent(entry.category) { ConcurrentHashMap() }
        map[entry.id] = entry
    }

    override fun removeEntry(id: String) {
        for (map in store.values) {
            map.remove(id)
        }
    }

    override fun clearCategory(category: JciMemoryCategory) {
        store[category]?.clear()
    }
}

class JciMemoryManager(
    private val backend: JciMemoryBackend = InMemoryJciMemoryBackend(),
    var config: CognitiveConfig = CognitiveConfig(enabled = true)
) {
    /**
     * Stores an explicit confirmed user preference (e.g. "always play music on Spotify").
     * Enforces confirmation, deduplication, sensitive data redaction, and category bounds.
     */
    fun storePreference(
        key: String,
        value: String,
        confirmedByUser: Boolean = true,
        confidence: Float = 1.0f
    ): MemoryWriteResult {
        return writeMemory(
            category = JciMemoryCategory.USER_PREFERENCE,
            key = key,
            value = value,
            confirmedByUser = confirmedByUser,
            confidence = confidence
        )
    }

    /**
     * Stores an explicit user correction (e.g. "no, always call John Doe, not John Smith").
     */
    fun storeCorrection(
        key: String,
        value: String
    ): MemoryWriteResult {
        return writeMemory(
            category = JciMemoryCategory.USER_CORRECTION,
            key = key,
            value = value,
            confirmedByUser = true,
            confidence = 1.0f
        )
    }

    /**
     * Stores a validated reusable task template.
     */
    fun storeTaskTemplate(
        goal: String,
        template: String,
        confirmedByUser: Boolean = true
    ): MemoryWriteResult {
        return writeMemory(
            category = JciMemoryCategory.TASK_TEMPLATE,
            key = goal,
            value = template,
            confirmedByUser = confirmedByUser,
            confidence = 1.0f
        )
    }

    private fun writeMemory(
        category: JciMemoryCategory,
        key: String,
        value: String,
        confirmedByUser: Boolean,
        confidence: Float
    ): MemoryWriteResult {
        // Feature flag invariant: when JCI is disabled, no JCI memory updates are performed
        if (!config.enabled) {
            return MemoryWriteResult(
                status = MemoryWriteStatus.DISABLED,
                message = "JCI is disabled. Memory write ignored."
            )
        }

        // Rule 1: Never store unconfirmed speculative model inferences
        if (!confirmedByUser) {
            return MemoryWriteResult(
                status = MemoryWriteStatus.REJECTED_UNCONFIRMED,
                message = "Speculative or unconfirmed statements cannot be written to long-term memory."
            )
        }

        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isBlank() || cleanValue.isBlank()) {
            return MemoryWriteResult(
                status = MemoryWriteStatus.REJECTED_BLANK,
                message = "Memory key or value cannot be blank."
            )
        }

        // Rule 2: Redact sensitive data before writing
        val sanitizedValue = CognitiveEvent.redactSensitiveData(cleanValue)
        val sanitizedKey = CognitiveEvent.redactSensitiveData(cleanKey)

        val normalizedKey = sanitizedKey.lowercase(Locale.ROOT)
        val existingEntries = backend.getEntries(category)

        // Rule 3: Deduplication — update existing record rather than append duplicate
        val existingMatch = existingEntries.find { it.key.lowercase(Locale.ROOT) == normalizedKey }
        if (existingMatch != null) {
            val updated = existingMatch.copy(
                value = sanitizedValue,
                confidence = confidence,
                lastAccessedAt = System.currentTimeMillis(),
                accessCount = existingMatch.accessCount + 1
            )
            backend.saveEntry(updated)
            return MemoryWriteResult(
                status = MemoryWriteStatus.UPDATED,
                entryId = updated.id,
                message = "Existing memory for '$sanitizedKey' updated."
            )
        }

        // Rule 4: Hard storage bounds & LRU pruning
        if (existingEntries.size >= category.maxEntries) {
            pruneCategory(category, existingEntries)
        }

        val newEntry = JciMemoryEntry(
            id = "mem_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            category = category,
            key = sanitizedKey,
            value = sanitizedValue,
            confidence = confidence,
            confirmedByUser = true,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = 0
        )
        backend.saveEntry(newEntry)

        return MemoryWriteResult(
            status = MemoryWriteStatus.STORED,
            entryId = newEntry.id,
            message = "Memory saved successfully."
        )
    }

    private fun pruneCategory(category: JciMemoryCategory, entries: List<JciMemoryEntry>) {
        // Prune lowest accessed and oldest lastAccessedAt
        val entryToPrune = entries.minWithOrNull(
            compareBy<JciMemoryEntry> { it.accessCount }
                .thenBy { it.lastAccessedAt }
                .thenBy { it.createdAt }
        )
        if (entryToPrune != null) {
            backend.removeEntry(entryToPrune.id)
        }
    }

    /**
     * Retrieves relevant memories matching the query, bounded by top-K and max token budget (~4 chars per token).
     */
    fun retrieveRelevant(
        query: String,
        maxItems: Int = 3,
        maxTokenBudget: Int = 200
    ): List<JciMemoryEntry> {
        val queryTokens = query.lowercase(Locale.ROOT).split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length > 2 }

        val allMemories = JciMemoryCategory.values().flatMap { backend.getEntries(it) }
        if (allMemories.isEmpty()) return emptyList()

        // Score each memory by lexical overlap + recency
        val scored = allMemories.map { entry ->
            val entryText = "${entry.key} ${entry.value}".lowercase(Locale.ROOT)
            var score = 0
            for (token in queryTokens) {
                if (entryText.contains(token)) {
                    score += 10
                }
            }
            if (entry.category == JciMemoryCategory.USER_PREFERENCE) {
                score += 5 // Slight preference bias
            }
            Pair(entry, score)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }

        val selected = mutableListOf<JciMemoryEntry>()
        var accumulatedChars = 0
        val maxChars = maxTokenBudget * 4 // Rough estimate: 1 token ~ 4 characters

        for ((entry, _) in scored) {
            val entryLength = entry.key.length + entry.value.length + 10
            if (accumulatedChars + entryLength <= maxChars && selected.size < maxItems) {
                selected.add(entry)
                accumulatedChars += entryLength
                // Bump access count
                backend.saveEntry(entry.copy(accessCount = entry.accessCount + 1, lastAccessedAt = System.currentTimeMillis()))
            }
        }

        return selected
    }

    fun getAll(category: JciMemoryCategory): List<JciMemoryEntry> {
        return backend.getEntries(category)
    }

    fun clear(category: JciMemoryCategory? = null) {
        if (category != null) {
            backend.clearCategory(category)
        } else {
            JciMemoryCategory.values().forEach { backend.clearCategory(it) }
        }
    }
}
