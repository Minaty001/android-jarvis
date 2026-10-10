package com.jarvis.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Automated Storage Janitor Engine.
 * Safely analyzes and purges app cache files, stale temporary downloads,
 * and leftover installation artifacts to keep storage lean and prevent degradation.
 */
class AutomatedStorageJanitorEngine(private val context: Context? = null) {

    private val TAG = "StorageJanitor"

    data class StorageReport(
        val totalFreeBytes: Long,
        val reclaimableCacheBytes: Long,
        val reclaimableTempFilesCount: Int,
        val summary: String
    )

    data class CleanResult(
        val freedBytes: Long,
        val filesDeleted: Int,
        val message: String
    )

    fun analyzeStorage(): StorageReport {
        val ctx = context ?: return StorageReport(0L, 0L, 0, "No context available.")

        var cacheBytes = 0L
        var tempCount = 0

        // 1. App internal cache
        ctx.cacheDir?.let {
            val (bytes, count) = calculateDirSize(it)
            cacheBytes += bytes
            tempCount += count
        }

        // 2. External cache
        ctx.externalCacheDir?.let {
            val (bytes, count) = calculateDirSize(it)
            cacheBytes += bytes
            tempCount += count
        }

        val freeSpace = ctx.cacheDir?.freeSpace ?: 0L
        val freeMb = freeSpace / (1024 * 1024)
        val reclaimMb = cacheBytes / (1024 * 1024)

        return StorageReport(
            totalFreeBytes = freeSpace,
            reclaimableCacheBytes = cacheBytes,
            reclaimableTempFilesCount = tempCount,
            summary = "Free storage: ${freeMb}MB. Found $tempCount temporary cache item(s) totalling ${reclaimMb}MB reclaimable."
        )
    }

    fun cleanStaleStorage(): CleanResult {
        val ctx = context ?: return CleanResult(0L, 0, "No context available.")

        var freedBytes = 0L
        var deletedCount = 0

        // Clean internal cache
        ctx.cacheDir?.let { dir ->
            val (bytes, count) = purgeDir(dir)
            freedBytes += bytes
            deletedCount += count
        }

        // Clean external cache
        ctx.externalCacheDir?.let { dir ->
            val (bytes, count) = purgeDir(dir)
            freedBytes += bytes
            deletedCount += count
        }

        val freedMb = freedBytes / (1024 * 1024)
        val msg = "Storage Janitor purged $deletedCount cache files, freeing approximately ${freedMb}MB of storage."
        Log.i(TAG, msg)

        return CleanResult(
            freedBytes = freedBytes,
            filesDeleted = deletedCount,
            message = msg
        )
    }

    private fun calculateDirSize(dir: File): Pair<Long, Int> {
        var size = 0L
        var count = 0
        val files = dir.listFiles() ?: return Pair(0L, 0)
        for (f in files) {
            if (f.isDirectory) {
                val (s, c) = calculateDirSize(f)
                size += s
                count += c
            } else {
                size += f.length()
                count++
            }
        }
        return Pair(size, count)
    }

    private fun purgeDir(dir: File): Pair<Long, Int> {
        var freed = 0L
        var count = 0
        val files = dir.listFiles() ?: return Pair(0L, 0)
        for (f in files) {
            if (f.isDirectory) {
                val (b, c) = purgeDir(f)
                freed += b
                count += c
                f.delete()
            } else {
                val len = f.length()
                if (f.delete()) {
                    freed += len
                    count++
                }
            }
        }
        return Pair(freed, count)
    }
}
