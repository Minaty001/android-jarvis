package com.jarvis.wakeword

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages local disk storage for custom wake-word 2-second PCM audio templates.
 *
 * Saves raw 16 kHz 16-bit Mono PCM audio samples into internal app storage
 * as 3 separate audio template files (`wake_word_template_1.pcm` to `wake_word_template_3.pcm`).
 */
class WakeWordTemplateStore(
    private val context: Context? = null,
    private val customDir: File? = null
) {
    private val TAG = "WakeWordTemplateStore"

    fun getTemplatesDir(): File {
        val tmpDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val dir = customDir ?: File(context?.filesDir ?: File(tmpDir), "wake_word_templates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getTemplateFile(index: Int): File {
        require(index in 1..MAX_TEMPLATES) { "Template index must be between 1 and $MAX_TEMPLATES" }
        return File(getTemplatesDir(), "wake_word_template_$index.pcm")
    }

    fun saveTemplate(index: Int, pcm: ShortArray): Boolean {
        require(index in 1..MAX_TEMPLATES) { "Invalid template index: $index" }
        if (pcm.isEmpty()) return false

        return try {
            val file = getTemplateFile(index)
            val byteBuffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcm) {
                byteBuffer.putShort(sample)
            }

            FileOutputStream(file).use { out ->
                out.write(byteBuffer.array())
            }
            Log.d(TAG, "Saved audio template $index (${file.length()} bytes) to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio template $index", e)
            false
        }
    }

    fun saveValidatedTemplate(
        index: Int,
        pcm: ShortArray,
        validation: VoiceEnrollmentCalibrator.ValidationResult
    ): Boolean = validation is VoiceEnrollmentCalibrator.ValidationResult.Valid && saveTemplate(index, pcm)

    fun loadTemplate(index: Int): ShortArray? {
        val file = getTemplateFile(index)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val pcm = ShortArray(shortBuffer.remaining())
            shortBuffer.get(pcm)
            pcm
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio template $index", e)
            null
        }
    }

    fun getSavedTemplateFiles(): List<File> {
        val list = mutableListOf<File>()
        for (i in 1..MAX_TEMPLATES) {
            val f = getTemplateFile(i)
            if (f.exists() && f.length() > 0L) {
                list.add(f)
            }
        }
        return list
    }

    fun getSavedTemplateCount(): Int {
        return getSavedTemplateFiles().size
    }

    fun hasThreeTemplates(): Boolean {
        return getSavedTemplateCount() >= MAX_TEMPLATES
    }

    fun clearTemplates() {
        for (i in 1..MAX_TEMPLATES) {
            val file = getTemplateFile(i)
            if (file.exists()) {
                file.delete()
            }
        }
        Log.d(TAG, "Cleared all custom wake word audio templates.")
    }

    companion object {
        const val MAX_TEMPLATES = 3
    }
}
