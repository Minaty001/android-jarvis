package com.jarvis

import com.jarvis.conversation.ConversationManager
import com.jarvis.habit.HabitCategory
import com.jarvis.habit.HabitManager
import com.jarvis.habit.TimeOfDay
import com.jarvis.voice.WakeWordEngine
import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.WakeWordConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceReliabilityPlanVerificationTest {

    @Test
    fun testExitPhrasesRecognition() {
        val manager = ConversationManager()
        assertTrue(manager.isExitUtterance("exit"))
        assertTrue(manager.isExitUtterance("bye"))
        assertTrue(manager.isExitUtterance("good bye"))
        assertTrue(manager.isExitUtterance("alvida"))
        assertTrue(manager.isExitUtterance("band karo"))
        assertTrue(manager.isExitUtterance("niklo"))
        assertTrue(manager.isExitUtterance("chhod do"))
        assertFalse(manager.isExitUtterance("what is the weather today"))
    }

    @Test
    fun testInterruptionControlKeywords() {
        val manager = ConversationManager()
        assertTrue(manager.isInterruptionControl("mute"))
        assertTrue(manager.isInterruptionControl("stop"))
        assertTrue(manager.isInterruptionControl("pause"))
        assertTrue(manager.isInterruptionControl("wait"))
        assertTrue(manager.isInterruptionControl("chup"))
        assertTrue(manager.isInterruptionControl("ruko"))
        assertFalse(manager.isInterruptionControl("mute phone volume"))
        assertFalse(manager.isInterruptionControl("stop playing music"))
    }

    @Test
    fun testEnrollmentProfileCannotCreateOrOverrideModelThreshold() {
        val engine = WakeWordEngine(config = WakeWordConfig())
        assertNull(engine.getEffectiveThreshold())

        val customProfile = UserVoiceProfile(
            isEnrolled = true,
            sampleCount = 3,
            calibratedThreshold = 0.72f,
            calibratedMinEnergyRms = 25.0
        )
        engine.applyVoiceProfile(customProfile)
        assertNull(engine.getEffectiveThreshold())
        assertEquals(customProfile, engine.getVoiceProfile())

        engine.applyVoiceProfile(UserVoiceProfile.DEFAULT)
        assertNull(engine.getEffectiveThreshold())
    }

    @Test
    fun testLocalRoutineLearningRepeatedObservationThreshold() {
        val habitManager = HabitManager()
        val timeOfDay = TimeOfDay.MORNING

        // 1st observation -> lower confidence (unconfirmed)
        habitManager.recordWifiTransition("Home-WiFi", timeOfDay)
        val habits1 = habitManager.getAllHabits()
        val habit1 = habits1.first { it.category == HabitCategory.WIFI_NETWORK }
        assertEquals(1, habit1.frequency)
        assertTrue(habit1.confidenceScore < 0.5f)

        // 2nd observation -> still below 0.5f
        habitManager.recordWifiTransition("Home-WiFi", timeOfDay)
        val habit2 = habitManager.getAllHabits().first { it.category == HabitCategory.WIFI_NETWORK }
        assertEquals(2, habit2.frequency)
        assertTrue(habit2.confidenceScore < 0.5f)

        // 3rd observation -> repeated pattern promoted with confidence >= 0.6f
        habitManager.recordWifiTransition("Home-WiFi", timeOfDay)
        val habit3 = habitManager.getAllHabits().first { it.category == HabitCategory.WIFI_NETWORK }
        assertEquals(3, habit3.frequency)
        assertTrue(habit3.confidenceScore >= 0.6f)
    }

    @Test
    fun testChargingEventHabitLearning() {
        val habitManager = HabitManager()
        habitManager.recordChargingEvent(isCharging = true, timeOfDay = TimeOfDay.NIGHT)
        habitManager.recordChargingEvent(isCharging = true, timeOfDay = TimeOfDay.NIGHT)
        habitManager.recordChargingEvent(isCharging = true, timeOfDay = TimeOfDay.NIGHT)

        val chargingHabit = habitManager.getAllHabits().first { it.category == HabitCategory.CHARGING_EVENT }
        assertEquals("charging_start", chargingHabit.target)
        assertEquals(3, chargingHabit.frequency)
        assertTrue(chargingHabit.confidenceScore >= 0.6f)
    }
}
