package com.jarvis.wakeword

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class WakeWordDatasetStoreTest {
    private val tempDir = File.createTempFile("jarvis-wake-dataset-", "").apply { delete(); mkdirs() }
    private val store = WakeWordDatasetStore(tempDir)

    @After
    fun clean() { tempDir.deleteRecursively() }

    @Test
    fun createsClassDirectoriesAndManifest() {
        val root = store.getDatasetDir()
        listOf("positive", "unknown", "background").forEach { assertTrue(File(root, it).isDirectory) }
        val manifest = JSONObject(store.getManifestFile().readText())
        assertEquals("Jarvis", manifest.getString("phrase"))
        assertEquals(16_000, manifest.getInt("sampleRate"))
        assertEquals(1, manifest.getInt("channels"))
        assertEquals("PCM_16BIT", manifest.getString("encoding"))
    }

    @Test
    fun wavRoundTripsAsMonoPcm16AtSixteenKhz() {
        val pcm = ShortArray(16_000) { ((it * 37) % 20_000 - 10_000).toShort() }
        val file = File(tempDir, "sample.wav")
        WavWriter.writeMonoPcm16(file, pcm)
        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(1, littleShort(bytes, 20))
        assertEquals(1, littleShort(bytes, 22))
        assertEquals(16_000, littleInt(bytes, 24))
        assertEquals(16, littleShort(bytes, 34))
        assertEquals(pcm.size * 2, littleInt(bytes, 40))
        val decoded = WavReader.readMonoPcm16(file)
        assertEquals(16_000, decoded.sampleRate)
        assertEquals(pcm.size, decoded.samples.size)
        assertTrue(decoded.samples.contentEquals(pcm))
    }

    @Test
    fun savesUniquePositiveRecordingsAndExportsExpectedFiles() {
        val first = ShortArray(16_000) { ((it * 17) % 15_000 - 7_500).toShort() }
        val second = ShortArray(16_000) { ((it * 31) % 15_000 - 7_500).toShort() }
        assertEquals("positive_0001.wav", store.savePositive(first).name)
        assertEquals("positive_0002.wav", store.savePositive(second).name)
        assertEquals(2, store.getPositiveSampleCount())
        assertTrue(store.getPositiveDatasetSha256().matches(Regex("[a-f0-9]{64}")))
        assertTrue(store.loadLatestPositive()!!.second.contentEquals(second))
        assertEquals(2, JSONObject(store.getManifestFile().readText()).getInt("sampleCount"))

        val zip = store.exportZip(File(tempDir, "export.zip"))
        ZipFile(zip).use { archive ->
            assertTrue(archive.getEntry("positive/positive_0001.wav") != null)
            assertTrue(archive.getEntry("positive/positive_0002.wav") != null)
            assertTrue(archive.getEntry("manifest.json") != null)
            assertFalse(archive.getEntry("unknown/unknown_0001.wav") != null)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesExactDuplicateRecording() {
        val pcm = ShortArray(16_000) { (it % 10_000).toShort() }
        store.savePositive(pcm)
        store.savePositive(pcm.copyOf())
    }

    private fun littleShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleInt(bytes: ByteArray, offset: Int): Int =
        littleShort(bytes, offset) or (littleShort(bytes, offset + 2) shl 16)
}
