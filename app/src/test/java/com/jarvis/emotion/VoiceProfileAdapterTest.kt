package com.jarvis.emotion

import com.jarvis.voice.TtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VoiceProfileAdapterTest {

    private lateinit var adapter: VoiceProfileAdapter
    private lateinit var fakeTts: FakeTtsEngine

    class FakeTtsEngine : TtsEngine {
        var appliedRate: Float = 0f
        var appliedPitch: Float = 0f
        var applyCount: Int = 0

        override fun start(ttsDir: File) {}
        override fun synthesize(text: String, onAudio: (ShortArray) -> Unit) {}
        override suspend fun speak(text: String): Boolean = true
        override fun stop() {}
        override fun cancel() {}

        override fun applyVoiceProfile(rate: Float, pitch: Float) {
            appliedRate = rate
            appliedPitch = pitch
            applyCount++
        }
    }

    @Before
    fun setUp() {
        adapter = VoiceProfileAdapter()
        fakeTts = FakeTtsEngine()
    }

    @Test
    fun testAllStatesProfileValues() {
        val prefs = EmotionalPreferences(voiceExpressionEnabled = true)

        adapter.applyProfile(fakeTts, AssistantState.HAPPY, prefs)
        assertEquals(1.04f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.03f, fakeTts.appliedPitch, 0.001f)

        adapter.applyProfile(fakeTts, AssistantState.SAD, prefs)
        assertEquals(0.94f, fakeTts.appliedRate, 0.001f)
        assertEquals(0.97f, fakeTts.appliedPitch, 0.001f)

        adapter.applyProfile(fakeTts, AssistantState.KIDDING, prefs)
        assertEquals(1.02f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.02f, fakeTts.appliedPitch, 0.001f)

        adapter.applyProfile(fakeTts, AssistantState.PANIC, prefs)
        assertEquals(1.04f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.01f, fakeTts.appliedPitch, 0.001f)

        adapter.applyProfile(fakeTts, AssistantState.THINKING, prefs)
        assertEquals(0.98f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.00f, fakeTts.appliedPitch, 0.001f)

        adapter.applyProfile(fakeTts, AssistantState.FOCUSED, prefs)
        assertEquals(1.00f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.00f, fakeTts.appliedPitch, 0.001f)
    }

    @Test
    fun testDisabledVoiceExpressionForcesBaselineExceptPanic() {
        val prefsDisabled = EmotionalPreferences(voiceExpressionEnabled = false)

        adapter.applyProfile(fakeTts, AssistantState.HAPPY, prefsDisabled)
        assertEquals(1.00f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.00f, fakeTts.appliedPitch, 0.001f)

        adapter.applyProfile(fakeTts, AssistantState.SAD, prefsDisabled)
        assertEquals(1.00f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.00f, fakeTts.appliedPitch, 0.001f)

        // PANIC safety warning must preserve urgent delivery profile even if subtle expression is off
        adapter.applyProfile(fakeTts, AssistantState.PANIC, prefsDisabled)
        assertEquals(1.04f, fakeTts.appliedRate, 0.001f)
        assertEquals(1.01f, fakeTts.appliedPitch, 0.001f)
    }
}
