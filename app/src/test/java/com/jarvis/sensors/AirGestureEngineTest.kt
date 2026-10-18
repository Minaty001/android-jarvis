package com.jarvis.sensors

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AirGestureEngineTest {

    @Test
    fun testIntentResolverAirGestures() {
        val enableUtterances = listOf("turn on air gestures", "enable air gestures", "touchless control on", "air gestures on")
        for (u in enableUtterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be AIR_GESTURES for '$u'", AssistantIntent.AIR_GESTURES, resolved?.intent)
            assertEquals("Action should be enable for '$u'", "enable", resolved?.params?.get("action"))
        }

        val disableUtterances = listOf("turn off air gestures", "disable air gestures", "touchless control off", "air gestures off")
        for (u in disableUtterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be AIR_GESTURES for '$u'", AssistantIntent.AIR_GESTURES, resolved?.intent)
            assertEquals("Action should be disable for '$u'", "disable", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testAirGestureWaveDetectionTiming() {
        val engine = AirGestureEngine(context = null)
        val waveCount = AtomicInteger(0)
        engine.onWaveDetected = { waveCount.incrementAndGet() }
        engine.setEnabled(true)

        // Simulate hand wave: near at t=1000, far at t=1300 (dwell 300ms: valid wave)
        engine.processProximityTransition(isNear = true, timestamp = 1000L)
        engine.processProximityTransition(isNear = false, timestamp = 1300L)
        assertEquals("Valid 300ms wave should trigger gesture callback", 1, waveCount.get())

        // Simulate lingering obstruction: near at t=2000, far at t=3500 (dwell 1500ms: pocket/table)
        engine.processProximityTransition(isNear = true, timestamp = 2000L)
        engine.processProximityTransition(isNear = false, timestamp = 3500L)
        assertEquals("Lingering obstruction should NOT trigger gesture callback", 1, waveCount.get())

        // Simulate too fast glitch: dwell 20ms
        engine.processProximityTransition(isNear = true, timestamp = 4000L)
        engine.processProximityTransition(isNear = false, timestamp = 4020L)
        assertEquals("Sub-50ms glitch should NOT trigger gesture callback", 1, waveCount.get())
    }

    @Test
    fun testAirGestureDisabledIgnoresWaves() {
        val engine = AirGestureEngine(context = null)
        val waveCount = AtomicInteger(0)
        engine.onWaveDetected = { waveCount.incrementAndGet() }
        engine.setEnabled(false)

        engine.processProximityTransition(isNear = true, timestamp = 1000L)
        engine.processProximityTransition(isNear = false, timestamp = 1300L)
        assertEquals("Disabled engine should not fire wave callback", 0, waveCount.get())
    }
}
