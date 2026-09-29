package com.jarvis.wakeword

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class MonoPcm16Wav(val sampleRate: Int, val samples: ShortArray)

private fun reverseShortBytes(value: Short): Short {
    val bits = value.toInt()
    return (((bits and 0xff) shl 8) or ((bits ushr 8) and 0xff)).toShort()
}

object WavWriter {
    fun writeMonoPcm16(file: File, pcm: ShortArray, sampleRate: Int = KwsFeatureSpec.DEFAULT.sampleRate) {
        require(pcm.isNotEmpty()) { "WAV audio must not be empty" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        val dataSize = Math.multiplyExact(pcm.size, 2)
        val parent = file.parentFile
        if (parent != null) check(parent.exists() || parent.mkdirs()) { "Could not create WAV directory" }

        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeBytes("RIFF")
            out.writeInt(Integer.reverseBytes(36 + dataSize))
            out.writeBytes("WAVEfmt ")
            out.writeInt(Integer.reverseBytes(16))
            out.writeShort(reverseShortBytes(1.toShort()).toInt())
            out.writeShort(reverseShortBytes(1.toShort()).toInt())
            out.writeInt(Integer.reverseBytes(sampleRate))
            out.writeInt(Integer.reverseBytes(sampleRate * 2))
            out.writeShort(reverseShortBytes(2.toShort()).toInt())
            out.writeShort(reverseShortBytes(16.toShort()).toInt())
            out.writeBytes("data")
            out.writeInt(Integer.reverseBytes(dataSize))
            pcm.forEach { out.writeShort(reverseShortBytes(it).toInt()) }
        }
    }
}

object WavReader {
    fun readMonoPcm16(file: File): MonoPcm16Wav {
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 44) { "WAV file is shorter than its header" }
            require(readAscii(input, 4) == "RIFF") { "Missing RIFF header" }
            input.readInt() // RIFF size is little-endian; chunk bounds use the file length.
            require(readAscii(input, 4) == "WAVE") { "Missing WAVE signature" }

            var format: Int? = null
            var channels: Int? = null
            var sampleRate: Int? = null
            var bitsPerSample: Int? = null
            var payloadOffset = -1L
            var payloadSize = -1L

            while (input.filePointer + 8 <= input.length()) {
                val chunkId = readAscii(input, 4)
                val chunkSize = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()))
                val chunkStart = input.filePointer
                require(chunkSize <= input.length() - chunkStart) { "WAV chunk exceeds file length" }
                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "Invalid WAV format chunk" }
                        format = reverseShortBytes(input.readShort()).toInt() and 0xffff
                        channels = reverseShortBytes(input.readShort()).toInt() and 0xffff
                        sampleRate = Integer.reverseBytes(input.readInt())
                        input.skipBytes(6)
                        bitsPerSample = reverseShortBytes(input.readShort()).toInt() and 0xffff
                    }
                    "data" -> {
                        payloadOffset = chunkStart
                        payloadSize = chunkSize
                    }
                }
                input.seek(chunkStart + chunkSize + (chunkSize and 1L))
            }

            require(format == 1) { "WAV encoding must be PCM" }
            require(channels == 1) { "WAV must be mono" }
            require(bitsPerSample == 16) { "WAV must use PCM16" }
            require(sampleRate == KwsFeatureSpec.DEFAULT.sampleRate) { "WAV must be ${KwsFeatureSpec.DEFAULT.sampleRate} Hz" }
            require(payloadOffset >= 0 && payloadSize > 0 && payloadSize % 2L == 0L) { "WAV data payload is missing or invalid" }
            require(payloadSize <= Int.MAX_VALUE) { "WAV data payload is too large" }

            input.seek(payloadOffset)
            val payload = ByteArray(payloadSize.toInt())
            input.readFully(payload)
            val samples = ShortArray(payload.size / 2)
            ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            return MonoPcm16Wav(sampleRate!!, samples)
        }
    }

    private fun readAscii(input: RandomAccessFile, size: Int): String =
        ByteArray(size).also(input::readFully).toString(Charsets.US_ASCII)
}
