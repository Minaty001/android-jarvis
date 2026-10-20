package com.jarvis.cognitive.memory

enum class JciMemoryCategory(val maxEntries: Int) {
    USER_PREFERENCE(50),     // Confirmed user preferences
    TASK_TEMPLATE(20),       // Successful reusable task plans
    USER_CORRECTION(30)      // Explicit corrections provided by user
}

data class JciMemoryEntry(
    val id: String,
    val category: JciMemoryCategory,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val confirmedByUser: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0
)

enum class MemoryWriteStatus {
    STORED,
    UPDATED,
    REJECTED_UNCONFIRMED,
    REJECTED_SENSITIVE,
    REJECTED_BLANK,
    DISABLED
}

data class MemoryWriteResult(
    val status: MemoryWriteStatus,
    val entryId: String? = null,
    val message: String
)
