package com.jarvis.memory

interface IMemoryStore {
    fun queryByKey(key: String, namespace: String = "default"): List<MemoryItem>
    fun getAllActiveMemories(namespace: String = "default"): List<MemoryItem>
    fun archiveMemory(id: Long)
    fun saveMemoryItem(item: MemoryItem): Long
    fun rebuildFtsIndex()
    fun setConfirmed(id: Long, confirmed: Boolean) {}
}
