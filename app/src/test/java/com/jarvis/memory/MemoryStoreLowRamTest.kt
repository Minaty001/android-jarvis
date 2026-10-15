package com.jarvis.memory

import com.jarvis.util.MockContext
import org.junit.Assert.*
import org.junit.Test

class MemoryStoreLowRamTest {

    @Test
    fun testMemoryStoreAppliesCapOnLowRam() {
        val context = MockContext()
        val store = MemoryStore(context, useInMemory = true)
        
        // On constrained/low-RAM or mock device, default query limit should be bounded to safe limit (<= 150)
        assertTrue("Query limit (${store.maxQueryLimit}) must be <= 150", store.maxQueryLimit <= 150)
    }

    @Test
    fun testTrimCacheDoesNotThrow() {
        val store = MemoryStore(MockContext(), useInMemory = true)
        store.trimCache(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }
}
