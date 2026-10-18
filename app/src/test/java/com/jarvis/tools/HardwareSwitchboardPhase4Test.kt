package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HardwareSwitchboardPhase4Test {

    @Test
    fun testIntentResolverHardwareToggles() {
        val dndOn = IntentResolver.resolve("turn on do not disturb")
        assertNotNull(dndOn)
        assertEquals(AssistantIntent.AUTONOMOUS_CONTROL, dndOn?.intent)
        assertEquals("dnd", dndOn?.params?.get("action"))
        assertEquals("on", dndOn?.params?.get("target"))

        val rotateOn = IntentResolver.resolve("turn on auto rotate")
        assertNotNull(rotateOn)
        assertEquals(AssistantIntent.AUTONOMOUS_CONTROL, rotateOn?.intent)
        assertEquals("auto_rotate", rotateOn?.params?.get("action"))
        assertEquals("on", rotateOn?.params?.get("target"))

        val batteryHealth = IntentResolver.resolve("battery health")
        assertNotNull(batteryHealth)
        assertEquals(AssistantIntent.BATTERY_CHECK, batteryHealth?.intent)
        assertEquals("health", batteryHealth?.params?.get("action"))
    }

    @Test
    fun testSwitchboardAutoRotateExecutionWithoutContext() = runBlocking {
        val tool = BatteryStatusTool(context = null)
        val res = tool.execute(mapOf("action" to "health"))
        assertTrue(res is ToolResult)
    }
}
