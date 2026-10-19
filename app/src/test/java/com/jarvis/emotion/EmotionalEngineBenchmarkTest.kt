package com.jarvis.emotion

import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionalEngineBenchmarkTest {

    @Test
    fun benchmarkEmotionalStateReducerLatency() {
        val reducer = EmotionalStateReducer()
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.FOCUSED)
        val event = EmotionalEvent(
            taskPhase = TaskPhase.SUCCEEDED,
            verifiedSuccess = true,
            source = SignalSource.TASK_MANAGER
        )
        val prefs = EmotionalPreferences()

        // Warm up
        for (i in 0 until 500) {
            reducer.reduce(snapshot, event, prefs)
        }

        val iterations = 5000
        val startTime = System.nanoTime()
        for (i in 0 until iterations) {
            reducer.reduce(snapshot, event, prefs)
        }
        val durationNs = System.nanoTime() - startTime
        val avgLatencyMs = (durationNs / iterations) / 1_000_000.0

        println("BENCHMARK: Average EmotionalStateReducer latency: $avgLatencyMs ms")
        // Target: < 50ms (strictly < 1ms in pure JVM)
        assertTrue("Reducer latency too high: $avgLatencyMs ms", avgLatencyMs < 5.0)
    }

    @Test
    fun benchmarkJsonSerializationLatency() {
        val snapshot = EmotionalSnapshot.defaultForState(AssistantState.HAPPY)

        // Warm up
        for (i in 0 until 500) {
            EmotionalJsonContract.toJson(snapshot)
        }

        val iterations = 5000
        val startTime = System.nanoTime()
        for (i in 0 until iterations) {
            EmotionalJsonContract.toJson(snapshot)
        }
        val durationNs = System.nanoTime() - startTime
        val avgLatencyMs = (durationNs / iterations) / 1_000_000.0

        println("BENCHMARK: Average JSON serialization latency: $avgLatencyMs ms")
        // Target: < 20ms (strictly < 1ms in pure JVM)
        assertTrue("JSON serialization latency too high: $avgLatencyMs ms", avgLatencyMs < 5.0)
    }

    @Test
    fun benchmarkSentimentAnalyzerLatency() {
        val analyzer = DeterministicSentimentAnalyzer()
        val text = "I am feeling really overwhelmed and need your help with this task"

        // Warm up
        for (i in 0 until 500) {
            analyzer.analyze(text)
        }

        val iterations = 5000
        val startTime = System.nanoTime()
        for (i in 0 until iterations) {
            analyzer.analyze(text)
        }
        val durationNs = System.nanoTime() - startTime
        val avgLatencyMs = (durationNs / iterations) / 1_000_000.0

        println("BENCHMARK: Average SentimentAnalyzer latency: $avgLatencyMs ms")
        // Target: < 50ms (strictly < 1ms in pure JVM)
        assertTrue("SentimentAnalyzer latency too high: $avgLatencyMs ms", avgLatencyMs < 5.0)
    }
}
