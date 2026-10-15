package com.jarvis.memory

import com.jarvis.util.MockContext
import org.junit.Assert.*
import org.junit.Test

class MemoryStoreLowRamTest {

    @Test
    fun testMemoryStoreAppliesCapOnLowRam() {
        val context = MockContext()
        val store = MemoryStore(context, useInMemory = true)
        
        // Save 70 memories
        for (i in 1..70) {
            store.saveMemory(content = "Memory item $i about user preferences", tags = listOf("test"))
        }

        // On constrained device, default query should be bounded to 50
        val results = store.searchMemories("preferences")
        assertTrue("Results size (${results.size}) must not exceed safe low-RAM limit (50)", results.size <= 50)
    }

    @Test
    fun testTrimCacheDoesNotThrow() {
        val store = MemoryStore(MockContext(), useInMemory = true)
        store.trimCache(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }
}
