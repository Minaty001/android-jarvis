package com.jarvis.ai

import com.jarvis.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SoundDetectorIntentTest {

    private val intentModel = IntentModel()

    @Test
    fun `test English general sound monitoring triggers`() {
        val queries = listOf(
            "start sound monitoring",
            "enable sound detection",
            "detect ambient sounds",
            "listen for sounds"
        )
        for (q in queries) {
            val resolved = intentModel.classify(q)
            assertNotNull("Query '$q' should resolve", resolved)
            assertEquals(AssistantIntent.SOUND_DETECTION, resolved?.intent)
            assertEquals("start", resolved?.params?.get("action"))
            assertEquals("all", resolved?.params?.get("type"))
            assertEquals("SOUND_DETECTOR", resolved?.directPlan?.actions?.firstOrNull()?.type)
        }
    }

    @Test
    fun `test English specific sound triggers`() {
        // Baby crying
        val baby = intentModel.classify("listen for baby crying")
        assertNotNull(baby)
        assertEquals(AssistantIntent.SOUND_DETECTION, baby?.intent)
        assertEquals("start", baby?.params?.get("action"))
        assertEquals("baby_crying", baby?.params?.get("type"))

        val baby2 = intentModel.classify("detect baby crying")
        assertEquals("baby_crying", baby2?.params?.get("type"))

        // Smoke alarm
        val smoke = intentModel.classify("detect smoke alarm")
        assertNotNull(smoke)
        assertEquals(AssistantIntent.SOUND_DETECTION, smoke?.intent)
        assertEquals("start", smoke?.params?.get("action"))
        assertEquals("smoke_alarm", smoke?.params?.get("type"))

        val smoke2 = intentModel.classify("listen for smoke alarm")
        assertEquals("smoke_alarm", smoke2?.params?.get("type"))

        // Doorbell
        val doorbell = intentModel.classify("listen for doorbell")
        assertNotNull(doorbell)
        assertEquals("doorbell", doorbell?.params?.get("type"))

        // Glass break
        val glass = intentModel.classify("detect glass break")
        assertNotNull(glass)
        assertEquals("glass_break", glass?.params?.get("type"))

        // Car honk
        val honk = intentModel.classify("detect car honk")
        assertNotNull(honk)
        assertEquals("car_honk", honk?.params?.get("type"))
    }

    @Test
    fun `test stop sound monitoring triggers`() {
        val queries = listOf(
            "stop sound monitoring",
            "stop sound detection",
            "turn off sound detector"
        )
        for (q in queries) {
            val resolved = intentModel.classify(q)
            assertNotNull("Query '$q' should resolve", resolved)
            assertEquals(AssistantIntent.SOUND_DETECTION, resolved?.intent)
            assertEquals("stop", resolved?.params?.get("action"))
        }
    }

    @Test
    fun `test sound detector status triggers`() {
        val queries = listOf(
            "sound detector status",
            "is sound detector running"
        )
        for (q in queries) {
            val resolved = intentModel.classify(q)
            assertNotNull("Query '$q' should resolve", resolved)
            assertEquals(AssistantIntent.SOUND_DETECTION, resolved?.intent)
            assertEquals("status", resolved?.params?.get("action"))
        }
    }

    @Test
    fun `test Hindi sound detection voice triggers`() {
        val startAll = intentModel.classify("sound detector chalu karo")
        assertNotNull(startAll)
        assertEquals(AssistantIntent.SOUND_DETECTION, startAll?.intent)
        assertEquals("start", startAll?.params?.get("action"))

        val ambientHindi = intentModel.classify("ambient sounds suno")
        assertNotNull(ambientHindi)
        assertEquals("start", ambientHindi?.params?.get("action"))

        val babyHindi = intentModel.classify("bacha ro raha hai suno")
        assertNotNull(babyHindi)
        assertEquals("start", babyHindi?.params?.get("action"))
        assertEquals("baby_crying", babyHindi?.params?.get("type"))

        val smokeHindi = intentModel.classify("smoke alarm suno")
        assertNotNull(smokeHindi)
        assertEquals("start", smokeHindi?.params?.get("action"))
        assertEquals("smoke_alarm", smokeHindi?.params?.get("type"))

        val stopHindi = intentModel.classify("sound detector band karo")
        assertNotNull(stopHindi)
        assertEquals("stop", stopHindi?.params?.get("action"))

        val statusHindi = intentModel.classify("sound detector status batao")
        assertNotNull(statusHindi)
        assertEquals("status", statusHindi?.params?.get("action"))
    }

    @Test
    fun `test ToolRegistry canonical resolution for sound detection aliases`() {
        val registry = ToolRegistry()
        assertEquals("SOUND_DETECTOR", registry.resolveCanonicalType("SOUND_MONITOR"))
        assertEquals("SOUND_DETECTOR", registry.resolveCanonicalType("START_SOUND_DETECTION"))
        assertEquals("SOUND_DETECTOR", registry.resolveCanonicalType("STOP_SOUND_DETECTION"))
        assertEquals("SOUND_DETECTOR", registry.resolveCanonicalType("DETECT_SOUND"))
        assertEquals("SOUND_DETECTOR", registry.resolveCanonicalType("LISTEN_SOUNDS"))
    }
}
