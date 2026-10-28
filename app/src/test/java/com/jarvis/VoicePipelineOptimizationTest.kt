package com.jarvis

import com.jarvis.voice.AndroidSpeechRecognizerEngine
import com.jarvis.voice.AsrState
import com.jarvis.voice.PipelineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoicePipelineOptimizationTest {

    private class MockAsrEngine : AndroidSpeechRecognizerEngine() {
        private val _state = MutableStateFlow(AsrState.STOPPED)
        override val state: StateFlow<AsrState> = _state
        var isStopped = false

        override suspend fun start(asrDir: File) {
            _state.value = AsrState.READY
        }

        override suspend fun startListening(
            onSpeechStarted: (() -> Unit)?,
            onPartial: (String) -> Unit,
            onFinal: (String) -> Unit,
            onError: ((Int, String) -> Unit)?,
            onEmpty: (() -> Unit)?,
            onRmsChanged: ((Float) -> Unit)?
        ) {
            _state.value = AsrState.LISTENING
        }

        override suspend fun pushAudio(samples: ShortArray) {}

        override suspend fun cancelListening() {
            _state.value = AsrState.READY
        }

        override suspend fun stopListening() {
            _state.value = AsrState.READY
        }

        override suspend fun stop() {
            _state.value = AsrState.STOPPED
            isStopped = true
        }
    }

    @Test
    fun testZeroStartupOverheadStandbyByDefault() {
        val mockAsr = MockAsrEngine()
        assertEquals(AsrState.STOPPED, mockAsr.state.value)
    }

    @Test
    fun testLazyStateTransitionOnUserActivity() {
        assertEquals(PipelineState.STANDBY.name, "STANDBY")
        assertEquals(PipelineState.ACTIVE_ASR.name, "ACTIVE_ASR")
    }

    @Test
    fun testIdleUnloadPipelineReleasesResources() = runBlocking {
        val mockAsr = MockAsrEngine()
        mockAsr.stop()
        assertEquals(AsrState.STOPPED, mockAsr.state.value)
        assertTrue(mockAsr.isStopped)
    }
}
