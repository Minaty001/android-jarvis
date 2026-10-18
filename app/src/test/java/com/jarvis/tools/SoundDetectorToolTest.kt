package com.jarvis.tools

import com.jarvis.foundation.LockScreenSecurityPolicy
import com.jarvis.foundation.PolicyEngine
import com.jarvis.sound.AmbientSoundMonitorEngine
import com.jarvis.sound.SoundAlertDispatcher
import com.jarvis.sound.SoundCategory
import com.jarvis.sound.SoundDetectionEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoundDetectorToolTest {

    private lateinit var tool: SoundDetectorTool

    @Before
    fun setUp() {
        tool = SoundDetectorTool(context = null)
        AmbientSoundMonitorEngine.stopMonitoring()
        AmbientSoundMonitorEngine.clearListeners()
        AmbientSoundMonitorEngine.resetStats()
    }

    @After
    fun tearDown() {
        AmbientSoundMonitorEngine.stopMonitoring()
        AmbientSoundMonitorEngine.clearListeners()
        AmbientSoundMonitorEngine.resetStats()
    }

    @Test
    fun `test formatAlertAnnouncement for all sound categories`() {
        val smokeMsg = SoundAlertDispatcher.formatAlertAnnouncement(SoundCategory.SMOKE_ALARM)
        assertTrue(smokeMsg.contains("Smoke alarm", ignoreCase = true))

        val babyMsg = SoundAlertDispatcher.formatAlertAnnouncement(SoundCategory.BABY_CRYING)
        assertTrue(babyMsg.contains("Baby crying", ignoreCase = true))

        val doorbellMsg = SoundAlertDispatcher.formatAlertAnnouncement(SoundCategory.DOORBELL)
        assertTrue(doorbellMsg.contains("Doorbell", ignoreCase = true))

        val glassMsg = SoundAlertDispatcher.formatAlertAnnouncement(SoundCategory.GLASS_BREAK)
        assertTrue(glassMsg.contains("Glass", ignoreCase = true))

        val hornMsg = SoundAlertDispatcher.formatAlertAnnouncement(SoundCategory.CAR_HONK)
        assertTrue(hornMsg.contains("Car horn", ignoreCase = true))
    }

    @Test
    fun `test vibration patterns exist and are distinct`() {
        val smokeVibe = SoundAlertDispatcher.getVibrationPattern(SoundCategory.SMOKE_ALARM)
        val babyVibe = SoundAlertDispatcher.getVibrationPattern(SoundCategory.BABY_CRYING)
        val glassVibe = SoundAlertDispatcher.getVibrationPattern(SoundCategory.GLASS_BREAK)

        assertTrue(smokeVibe.isNotEmpty())
        assertTrue(babyVibe.isNotEmpty())
        assertTrue(glassVibe.isNotEmpty())
        assertFalse(smokeVibe.contentEquals(babyVibe))
    }

    @Test
    fun `test dispatchAlert triggers TTS speaker and returns true`() = runBlocking {
        var spokenText: String? = null
        val event = SoundDetectionEvent(
            category = SoundCategory.SMOKE_ALARM,
            confidence = 0.95f,
            dominantFrequencyHz = 3000f
        )

        val dispatched = SoundAlertDispatcher.dispatchAlert(
            context = null,
            event = event,
            ttsSpeaker = { spokenText = it }
        )

        assertTrue(dispatched)
        assertTrue(spokenText?.contains("Smoke alarm", ignoreCase = true) == true)
    }

    @Test
    fun `test tool execute start stop and status actions`() = runBlocking {
        // 1. Status when idle
        val statusRes1 = tool.execute(mapOf("action" to "status"))
        assertTrue(statusRes1.success)
        assertEquals("false", statusRes1.data["is_monitoring"])

        // 2. Start monitoring
        val startRes = tool.execute(mapOf("action" to "start", "type" to "baby_crying"))
        assertTrue(startRes.success)
        assertTrue(AmbientSoundMonitorEngine.isMonitoring())
        assertTrue(AmbientSoundMonitorEngine.getActiveCategories().contains(SoundCategory.BABY_CRYING))

        // 3. Status when active
        val statusRes2 = tool.execute(mapOf("action" to "status"))
        assertTrue(statusRes2.success)
        assertEquals("true", statusRes2.data["is_monitoring"])

        // 4. Simulate sound
        val simRes = tool.execute(mapOf("action" to "simulate", "type" to "baby_crying"))
        assertTrue(simRes.success)

        // 5. Stop monitoring
        val stopRes = tool.execute(mapOf("action" to "stop"))
        assertTrue(stopRes.success)
        assertFalse(AmbientSoundMonitorEngine.isMonitoring())
    }

    @Test
    fun `test LockScreenSecurityPolicy allows sound detector actions on lock screen`() {
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SOUND_DETECTOR", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("SOUND_MONITOR", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("START_SOUND_DETECTION", emptyMap()))
        assertTrue(LockScreenSecurityPolicy.isActionAllowedOnLockScreen("STOP_SOUND_DETECTION", emptyMap()))
    }

    @Test
    fun `test PolicyEngine exempts sound detector from manual confirmation`() {
        val policy = PolicyEngine.evaluateAction("SOUND_DETECTOR", emptyMap())
        assertTrue(policy.allowed)
        assertFalse("Sound detector must not block for confirmation", policy.requiresApproval)
    }
}
