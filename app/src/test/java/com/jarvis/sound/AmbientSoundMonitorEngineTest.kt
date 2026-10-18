package com.jarvis.sound

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AmbientSoundMonitorEngineTest {

    @Before
    fun setUp() {
        AmbientSoundMonitorEngine.resetStats()
        AmbientSoundMonitorEngine.clearListeners()
        AmbientSoundMonitorEngine.stopMonitoring()
    }

    @After
    fun tearDown() {
        AmbientSoundMonitorEngine.stopMonitoring()
        AmbientSoundMonitorEngine.clearListeners()
        AmbientSoundMonitorEngine.resetStats()
    }

    @Test
    fun `test startMonitoring and stopMonitoring lifecycle`() {
        assertFalse(AmbientSoundMonitorEngine.isMonitoring())

        val started = AmbientSoundMonitorEngine.startMonitoring(
            categories = setOf(SoundCategory.SMOKE_ALARM, SoundCategory.BABY_CRYING),
            dutyCycleMode = AmbientSoundMonitorEngine.DutyCycleMode.BATTERY_SAVER
        )
        assertTrue(started)
        assertTrue(AmbientSoundMonitorEngine.isMonitoring())
        assertEquals(2, AmbientSoundMonitorEngine.getActiveCategories().size)
        assertTrue(AmbientSoundMonitorEngine.getActiveCategories().contains(SoundCategory.SMOKE_ALARM))

        AmbientSoundMonitorEngine.stopMonitoring()
        assertFalse(AmbientSoundMonitorEngine.isMonitoring())
    }

    @Test
    fun `test listener receives event and simulation works`() {
        val count = AtomicInteger(0)
        var lastEvent: SoundDetectionEvent? = null

        val listener = SoundDetectionListener { event ->
            count.incrementAndGet()
            lastEvent = event
        }

        AmbientSoundMonitorEngine.addListener(listener)
        AmbientSoundMonitorEngine.startMonitoring()

        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.BABY_CRYING, confidence = 0.92f)

        assertEquals(1, count.get())
        assertEquals(SoundCategory.BABY_CRYING, lastEvent?.category)
        assertEquals(0.92f, lastEvent?.confidence ?: 0f, 0.01f)
        assertEquals(1, AmbientSoundMonitorEngine.getDetectionCount(SoundCategory.BABY_CRYING))
    }

    @Test
    fun `test category filtering ignores unmonitored sound`() {
        val count = AtomicInteger(0)
        AmbientSoundMonitorEngine.addListener { count.incrementAndGet() }

        // Only listen for SMOKE_ALARM
        AmbientSoundMonitorEngine.startMonitoring(
            categories = setOf(SoundCategory.SMOKE_ALARM)
        )

        // Simulate BABY_CRYING -> should be ignored by filter
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.BABY_CRYING)
        assertEquals(0, count.get())

        // Simulate SMOKE_ALARM -> should be processed
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.SMOKE_ALARM)
        assertEquals(1, count.get())
    }

    @Test
    fun `test debounce cooldown suppresses rapid duplicate alerts`() {
        val count = AtomicInteger(0)
        AmbientSoundMonitorEngine.addListener { count.incrementAndGet() }
        AmbientSoundMonitorEngine.setCooldownMs(10000L) // 10 second cooldown
        AmbientSoundMonitorEngine.startMonitoring()

        // First detection
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.SMOKE_ALARM)
        assertEquals(1, count.get())

        // Immediate duplicate detection within 10s cooldown
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.SMOKE_ALARM)
        assertEquals(1, count.get()) // Still 1, suppressed by cooldown

        // Different category is not blocked by smoke alarm cooldown
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.DOORBELL)
        assertEquals(2, count.get())
    }

    @Test
    fun `test removeListener and clearListeners`() {
        val count = AtomicInteger(0)
        val listener = SoundDetectionListener { count.incrementAndGet() }

        AmbientSoundMonitorEngine.addListener(listener)
        AmbientSoundMonitorEngine.startMonitoring()
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.CAR_HONK)
        assertEquals(1, count.get())

        AmbientSoundMonitorEngine.removeListener(listener)
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.CAR_HONK)
        assertEquals(1, count.get()) // Not incremented

        AmbientSoundMonitorEngine.addListener(listener)
        AmbientSoundMonitorEngine.clearListeners()
        AmbientSoundMonitorEngine.simulateDetection(SoundCategory.CAR_HONK)
        assertEquals(1, count.get()) // Still 1
    }

    @Test
    fun `test onAudioFrame as AudioFrameConsumer processes real acoustic frame`() {
        val count = AtomicInteger(0)
        var detectedCategory: SoundCategory? = null
        AmbientSoundMonitorEngine.addListener { event ->
            count.incrementAndGet()
            detectedCategory = event.category
        }

        // Start in CONTINUOUS mode to ensure frame is not dropped by duty cycle
        AmbientSoundMonitorEngine.startMonitoring(
            dutyCycleMode = AmbientSoundMonitorEngine.DutyCycleMode.CONTINUOUS
        )

        // Generate 3000 Hz smoke alarm frame
        val smokeAlarmSamples = ShortArray(1024) { i ->
            val angle = 2.0 * Math.PI * 3000.0 * i / 16000.0
            (22000 * Math.sin(angle)).toInt().toShort()
        }

        // Invoke via AudioFrameConsumer interface
        AmbientSoundMonitorEngine.onAudioFrame(smokeAlarmSamples, smokeAlarmSamples.size)

        assertEquals(1, count.get())
        assertEquals(SoundCategory.SMOKE_ALARM, detectedCategory)
    }

    @Test
    fun `test updatePowerState dynamically updates dutyCycleMode`() {
        AmbientSoundMonitorEngine.startMonitoring(
            dutyCycleMode = AmbientSoundMonitorEngine.DutyCycleMode.BATTERY_SAVER
        )
        assertEquals(AmbientSoundMonitorEngine.DutyCycleMode.BATTERY_SAVER, AmbientSoundMonitorEngine.getDutyCycleMode())

        // Charger connected
        AmbientSoundMonitorEngine.updatePowerState(isCharging = true)
        assertEquals(AmbientSoundMonitorEngine.DutyCycleMode.CONTINUOUS, AmbientSoundMonitorEngine.getDutyCycleMode())

        // Charger disconnected
        AmbientSoundMonitorEngine.updatePowerState(isCharging = false)
        assertEquals(AmbientSoundMonitorEngine.DutyCycleMode.BATTERY_SAVER, AmbientSoundMonitorEngine.getDutyCycleMode())
    }
}
