package com.jarvis.wakeword

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WakeWordSetupTest {

    private lateinit var tempDir: File
    private lateinit var store: WakeWordTemplateStore

    @Before
    fun setUp() {
        tempDir = File.createTempFile("test_wake_word_templates_", "").apply {
            delete()
            mkdirs()
        }
        store = WakeWordTemplateStore(customDir = tempDir)
        store.clearTemplates()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testInitialTemplateStoreState() {
        assertEquals(0, store.getSavedTemplateCount())
        assertFalse(store.hasThreeTemplates())
        assertTrue(store.getSavedTemplateFiles().isEmpty())
    }

    @Test
    fun testSaveAndLoadSingleTemplate() {
        // 2 seconds at 16 kHz = 32000 short samples
        val pcm = ShortArray(32000) { (it % 5000).toShort() }
        val saved = store.saveTemplate(1, pcm)
        assertTrue("Template 1 should save successfully", saved)

        assertEquals(1, store.getSavedTemplateCount())
        assertFalse(store.hasThreeTemplates())

        val loaded = store.loadTemplate(1)
        assertNotNull(loaded)
        assertEquals(32000, loaded!!.size)
        assertEquals(pcm[0], loaded[0])
        assertEquals(pcm[100], loaded[100])
    }

    @Test
    fun testSaveThreeAudioTemplates() {
        for (i in 1..3) {
            val pcm = ShortArray(32000) { (i * 1000 + it % 500).toShort() }
            val saved = store.saveTemplate(i, pcm)
            assertTrue("Template $i should save successfully", saved)
        }

        assertEquals(3, store.getSavedTemplateCount())
        assertTrue(store.hasThreeTemplates())
        assertEquals(3, store.getSavedTemplateFiles().size)

        // Clear and verify empty
        store.clearTemplates()
        assertEquals(0, store.getSavedTemplateCount())
        assertFalse(store.hasThreeTemplates())
    }

    @Test
    fun testRejectedRecordingIsNotSaved() {
        val quietPcm = ShortArray(32000)
        val validation = VoiceEnrollmentCalibrator().validateSample(quietPcm)

        assertTrue(validation is VoiceEnrollmentCalibrator.ValidationResult.Rejected)
        assertFalse(store.saveValidatedTemplate(1, quietPcm, validation))
        assertEquals(0, store.getSavedTemplateCount())
    }
}
