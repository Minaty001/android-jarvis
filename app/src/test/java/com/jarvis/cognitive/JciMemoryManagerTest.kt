package com.jarvis.cognitive

import com.jarvis.cognitive.memory.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JciMemoryManagerTest {

    private lateinit var memoryManager: JciMemoryManager

    @Before
    fun setUp() {
        memoryManager = JciMemoryManager(
            config = CognitiveConfig(enabled = true)
        )
        memoryManager.clear()
    }

    @Test
    fun testExplicitPreferenceStoredAndRetrieved() {
        val result = memoryManager.storePreference(
            key = "music_service",
            value = "Always use Spotify for playing songs",
            confirmedByUser = true
        )

        assertEquals(MemoryWriteStatus.STORED, result.status)
        assertNotNull(result.entryId)

        val retrieved = memoryManager.retrieveRelevant("play some music on spotify")
        assertEquals(1, retrieved.size)
        assertEquals("music_service", retrieved.first().key)
        assertEquals("Always use Spotify for playing songs", retrieved.first().value)
    }

    @Test
    fun testSpeculativeUnconfirmedStatementNotStored() {
        val result = memoryManager.storePreference(
            key = "favorite_restaurant",
            value = "User might prefer Italian food based on location",
            confirmedByUser = false // Speculative model inference!
        )

        assertEquals(MemoryWriteStatus.REJECTED_UNCONFIRMED, result.status)
        assertNull(result.entryId)
        assertTrue("Memory must remain empty", memoryManager.getAll(JciMemoryCategory.USER_PREFERENCE).isEmpty())
    }

    @Test
    fun testSensitiveDataRedactedBeforeStorage() {
        val result = memoryManager.storePreference(
            key = "wifi_config",
            value = "Office wifi password is supersecretpass and pin is 1234",
            confirmedByUser = true
        )

        assertEquals(MemoryWriteStatus.STORED, result.status)

        val entries = memoryManager.getAll(JciMemoryCategory.USER_PREFERENCE)
        val saved = entries.first()

        assertFalse("Raw password must NOT be stored", saved.value.contains("supersecretpass"))
        assertFalse("Raw pin must NOT be stored", saved.value.contains("1234"))
        assertTrue("Value must contain redacted marker", saved.value.contains("[REDACTED]"))
    }

    @Test
    fun testDuplicatePreferenceUpdatesExistingRecord() {
        // First entry
        val res1 = memoryManager.storePreference(
            key = "preferred_browser",
            value = "Google Chrome",
            confirmedByUser = true
        )
        assertEquals(MemoryWriteStatus.STORED, res1.status)
        val originalId = res1.entryId

        // Second entry with same key (case-insensitive)
        val res2 = memoryManager.storePreference(
            key = "PREFERRED_BROWSER",
            value = "Mozilla Firefox",
            confirmedByUser = true
        )
        assertEquals(MemoryWriteStatus.UPDATED, res2.status)
        assertEquals("Entry ID must remain the same on deduplicated update", originalId, res2.entryId)

        val entries = memoryManager.getAll(JciMemoryCategory.USER_PREFERENCE)
        assertEquals("Deduplication must prevent duplicate rows", 1, entries.size)
        assertEquals("Mozilla Firefox", entries.first().value)
    }

    @Test
    fun testStorageCapEnforcedAndLruPruned() {
        // USER_PREFERENCE cap is 50. Let's fill it to 50
        for (i in 1..50) {
            memoryManager.storePreference(
                key = "pref_$i",
                value = "Value for preference $i",
                confirmedByUser = true
            )
        }

        assertEquals(50, memoryManager.getAll(JciMemoryCategory.USER_PREFERENCE).size)

        // Add 51st entry
        val res51 = memoryManager.storePreference(
            key = "pref_51",
            value = "Value for preference 51",
            confirmedByUser = true
        )
        assertEquals(MemoryWriteStatus.STORED, res51.status)

        val entriesAfter = memoryManager.getAll(JciMemoryCategory.USER_PREFERENCE)
        assertEquals("Count must remain capped at maxEntries (50)", 50, entriesAfter.size)
        assertTrue("Newest entry must be present", entriesAfter.any { it.key == "pref_51" })
    }

    @Test
    fun testRetrievalReturnsTopKWithinTokenBudget() {
        // Store 5 relevant entries about music
        for (i in 1..5) {
            memoryManager.storePreference(
                key = "music_pref_$i",
                value = "Detailed music preference setting number $i for jazz and rock tracks",
                confirmedByUser = true
            )
        }

        // Query with maxItems = 2
        val top2 = memoryManager.retrieveRelevant(
            query = "what is my music preference?",
            maxItems = 2,
            maxTokenBudget = 200
        )
        assertEquals(2, top2.size)

        // Query with very small token budget (e.g. 10 tokens ~ 40 chars)
        val budgetConstrained = memoryManager.retrieveRelevant(
            query = "music preference",
            maxItems = 5,
            maxTokenBudget = 10
        )
        assertTrue("Token budget must restrict returned count", budgetConstrained.size <= 1)
    }

    @Test
    fun testDisabledJciDoesNotModifyMemory() {
        memoryManager.config = CognitiveConfig(enabled = false)

        val result = memoryManager.storePreference(
            key = "test_key",
            value = "test_val",
            confirmedByUser = true
        )

        assertEquals(MemoryWriteStatus.DISABLED, result.status)
        assertTrue("No memory should be stored when JCI is disabled", memoryManager.getAll(JciMemoryCategory.USER_PREFERENCE).isEmpty())
    }
}
