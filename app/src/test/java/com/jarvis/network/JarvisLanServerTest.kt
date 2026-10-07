package com.jarvis.network

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class JarvisLanServerTest {

    @Test
    fun testPairingPinFormat() {
        val server = JarvisLanServer(context = ContextWrapperDummy(), port = 8888)
        val pin = server.pairingPin
        assertNotNull(pin)
        assertEquals(4, pin.length)
        assertTrue("PIN must be 4 digits", pin.all { it.isDigit() })
    }

    @Test
    fun testLocalIpAddressResolution() {
        val server = JarvisLanServer(context = ContextWrapperDummy(), port = 8888)
        val ip = server.getLocalIpAddress()
        assertNotNull(ip)
        assertTrue(ip.isNotEmpty())
    }

    @Test
    fun testServerLifecycleStartStop() {
        val server = JarvisLanServer(context = ContextWrapperDummy(), port = 8889)
        assertFalse(JarvisLanServer.isServerRunning)

        server.start()
        assertTrue(JarvisLanServer.isServerRunning)

        server.stop()
        assertFalse(JarvisLanServer.isServerRunning)
    }

    @Test
    fun webAppResponsePreservesBinaryFileBytes() {
        val bytes = byteArrayOf(0, 0xFF.toByte(), 0xFE.toByte(), 0x89.toByte(), 0x50, 0x4E, 0x47)
        val file = Files.createTempFile("jarvis-webapp", ".png").toFile()
        try {
            file.writeBytes(bytes)
            val response = ByteArrayOutputStream()

            writeWebAppFileResponse(response, file, "image/png")

            assertArrayEquals(bytes, response.toByteArray().takeLast(bytes.size).toByteArray())
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    @Test
    fun requestBodyUsesUtf8ByteLength() {
        val body = "\"command\":\"खोलो\"".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(body + byteArrayOf(0x7F))

        val read = readHttpBody(input, body.size)

        assertEquals(String(body, Charsets.UTF_8), String(read!!, Charsets.UTF_8))
        assertEquals(0x7F, input.read())
    }

    // Dummy context for unit testing without full Android system service mocks
    private class ContextWrapperDummy : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
}
