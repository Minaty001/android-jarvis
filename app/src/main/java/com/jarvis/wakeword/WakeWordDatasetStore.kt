package com.jarvis.wakeword

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Stores real, uncompressed PCM16 WAV recordings in app-private filesDir. */
class WakeWordDatasetStore private constructor(private val datasetDir: File) {
    constructor(context: Context) : this(File(context.filesDir, DATASET_DIRECTORY))
    constructor(filesDir: File, datasetName: String = DATASET_DIRECTORY) : this(File(filesDir, datasetName))

    @Synchronized
    fun getDatasetDir(): File = ensureDataset()

    @Synchronized
    fun getPositiveSampleCount(): Int = positiveDir().listFiles { f -> f.isFile && f.extension.equals("wav", true) }?.size ?: 0

    @Synchronized
    fun getPositiveSampleFiles(): List<File> =
        positiveDir().listFiles { f -> f.isFile && f.extension.equals("wav", true) }
            ?.sortedBy { it.name } ?: emptyList()

    @Synchronized
    fun getPositiveDatasetSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        getPositiveSampleFiles().forEach { file ->
            digest.update(file.name.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun savePositive(pcm: ShortArray): File {
        require(pcm.size in SAMPLE_RATE..MAX_SAMPLE_COUNT) { "Wake-word WAV must contain 1.0–1.5 seconds of PCM" }
        ensureDataset()
        val hash = sha256(pcm)
        val manifest = readManifest()
        val existing = manifest.getJSONArray("samples")
        require((0 until existing.length()).none { existing.getJSONObject(it).optString("pcmSha256") == hash }) {
            "Duplicate recording was not added to the dataset"
        }

        val nextIndex = (getPositiveSampleFiles().maxOfOrNull { it.nameWithoutExtension.substringAfter('_').toIntOrNull() ?: 0 } ?: 0) + 1
        val file = File(positiveDir(), "positive_${nextIndex.toString().padStart(4, '0')}.wav")
        val temp = File(file.parentFile, "${file.name}.tmp")
        WavWriter.writeMonoPcm16(temp, pcm)
        replaceFile(temp, file)

        existing.put(JSONObject().apply {
            put("file", file.name)
            put("sampleCount", pcm.size)
            put("durationMs", pcm.size * 1000L / SAMPLE_RATE)
            put("pcmSha256", hash)
            put("wavSha256", sha256File(file))
            put("createdAt", System.currentTimeMillis())
        })
        manifest.put("sampleCount", existing.length())
        manifest.put("positiveDatasetSha256", getPositiveDatasetSha256())
        writeManifest(manifest)
        return file
    }

    @Synchronized
    fun loadPositive(file: File): ShortArray {
        require(file.parentFile?.canonicalFile == positiveDir().canonicalFile) { "File is outside the positive dataset" }
        val wav = WavReader.readMonoPcm16(file)
        return wav.samples
    }

    @Synchronized
    fun loadLatestPositive(): Pair<File, ShortArray>? {
        val file = getPositiveSampleFiles().lastOrNull() ?: return null
        return file to loadPositive(file)
    }

    @Synchronized
    fun getManifestFile(): File {
        ensureDataset()
        return File(datasetDir, MANIFEST_FILE)
    }

    @Synchronized
    fun exportZip(destination: File): File {
        ensureDataset()
        destination.parentFile?.let { check(it.exists() || it.mkdirs()) { "Could not create export directory" } }
        val temp = File(destination.parentFile, "${destination.name}.tmp")
        ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
            getPositiveSampleFiles().forEach { sample ->
                zip.putNextEntry(ZipEntry("positive/${sample.name}"))
                sample.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MANIFEST_FILE))
            getManifestFile().inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        replaceFile(temp, destination)
        return destination
    }

    private fun ensureDataset(): File {
        val root = datasetDir
        listOf("positive", "unknown", "background").forEach { name ->
            val dir = File(root, name)
            check(dir.isDirectory || dir.mkdirs()) { "Could not create dataset directory: $name" }
        }
        val manifest = File(root, MANIFEST_FILE)
        if (!manifest.exists()) writeManifest(defaultManifest())
        return root
    }

    private fun positiveDir(): File = File(ensureDataset(), "positive")

    private fun defaultManifest() = JSONObject().apply {
        put("phrase", WakeWordModelMetadata.SUPPORTED_PHRASE)
        put("sampleRate", SAMPLE_RATE)
        put("channels", 1)
        put("encoding", "PCM_16BIT")
        put("datasetVersion", DATASET_VERSION)
        put("sampleCount", 0)
        put("positiveDatasetSha256", sha256Empty())
        put("samples", JSONArray())
    }

    private fun readManifest(): JSONObject = JSONObject(getManifestFile().readText())

    private fun writeManifest(manifest: JSONObject) {
        val file = File(datasetDir, MANIFEST_FILE)
        val temp = File(datasetDir, "$MANIFEST_FILE.tmp")
        temp.writeText(manifest.toString(2))
        replaceFile(temp, file)
    }

    private fun replaceFile(temp: File, destination: File) {
        try {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(pcm: ShortArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        pcm.forEach { sample ->
            digest.update((sample.toInt() and 0xff).toByte())
            digest.update((sample.toInt() ushr 8).toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Empty(): String =
        MessageDigest.getInstance("SHA-256").digest().joinToString("") { "%02x".format(it) }

    companion object {
        const val DATASET_DIRECTORY = "wake_word_dataset"
        const val MANIFEST_FILE = "manifest.json"
        const val DATASET_VERSION = 1
        const val TARGET_SAMPLES = 50
        const val SAMPLE_RATE = 16_000
        const val MAX_SAMPLE_COUNT = SAMPLE_RATE * 3 / 2
    }
}
