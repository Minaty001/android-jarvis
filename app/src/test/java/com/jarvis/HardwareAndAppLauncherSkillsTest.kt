package com.jarvis

import com.jarvis.agent.AgentWorkingMemory
import com.jarvis.agent.skills.AppLauncherSkill
import com.jarvis.agent.skills.DeviceHardwareSkill
import com.jarvis.agent.skills.SkillContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HardwareAndAppLauncherSkillsTest {

    private lateinit var hardwareSkill: DeviceHardwareSkill
    private lateinit var appLauncherSkill: AppLauncherSkill

    @Before
    fun setUp() {
        hardwareSkill = DeviceHardwareSkill()
        appLauncherSkill = AppLauncherSkill()
    }

    @Test
    fun testDeviceHardwareSkillFlashlightTriggers() = runBlocking {
        val ctx1 = SkillContext("turn on flashlight", AgentWorkingMemory("turn on flashlight"))
        assertTrue(hardwareSkill.canHandle(ctx1.goal, ctx1))
        val res1 = hardwareSkill.execute(ctx1.goal, ctx1)
        assertTrue(res1.handled)
        assertEquals("FLASHLIGHT", res1.proposedAction?.type)
        assertEquals("on", res1.proposedAction?.params?.get("mode"))

        val ctx2 = SkillContext("torch band karo", AgentWorkingMemory("torch band karo"))
        assertTrue(hardwareSkill.canHandle(ctx2.goal, ctx2))
        val res2 = hardwareSkill.execute(ctx2.goal, ctx2)
        assertTrue(res2.handled)
        assertEquals("FLASHLIGHT", res2.proposedAction?.type)
        assertEquals("off", res2.proposedAction?.params?.get("mode"))
    }

    @Test
    fun testDeviceHardwareSkillWifiAndBluetoothTriggers() = runBlocking {
        val ctx1 = SkillContext("turn on wifi", AgentWorkingMemory("turn on wifi"))
        assertTrue(hardwareSkill.canHandle(ctx1.goal, ctx1))
        val res1 = hardwareSkill.execute(ctx1.goal, ctx1)
        assertEquals("SYSTEM_SWITCHBOARD", res1.proposedAction?.type)
        assertEquals("wifi", res1.proposedAction?.params?.get("action"))
        assertEquals("on", res1.proposedAction?.params?.get("target"))

        val ctx2 = SkillContext("bluetooth off karo", AgentWorkingMemory("bluetooth off karo"))
        assertTrue(hardwareSkill.canHandle(ctx2.goal, ctx2))
        val res2 = hardwareSkill.execute(ctx2.goal, ctx2)
        assertEquals("SYSTEM_SWITCHBOARD", res2.proposedAction?.type)
        assertEquals("bt", res2.proposedAction?.params?.get("action"))
        assertEquals("off", res2.proposedAction?.params?.get("target"))
    }

    @Test
    fun testDeviceHardwareSkillVolumeAndBrightnessTriggers() = runBlocking {
        val ctx1 = SkillContext("set volume to 80%", AgentWorkingMemory("set volume to 80%"))
        assertTrue(hardwareSkill.canHandle(ctx1.goal, ctx1))
        val res1 = hardwareSkill.execute(ctx1.goal, ctx1)
        assertEquals("DEVICE_SETTINGS", res1.proposedAction?.type)
        assertEquals("80", res1.proposedAction?.params?.get("volume_percent"))

        val ctx2 = SkillContext("set brightness to 50%", AgentWorkingMemory("set brightness to 50%"))
        assertTrue(hardwareSkill.canHandle(ctx2.goal, ctx2))
        val res2 = hardwareSkill.execute(ctx2.goal, ctx2)
        assertEquals("SYSTEM_SWITCHBOARD", res2.proposedAction?.type)
        assertEquals("brightness", res2.proposedAction?.params?.get("action"))
        assertEquals("50", res2.proposedAction?.params?.get("value"))
    }

    @Test
    fun testAppLauncherSkillTriggers() = runBlocking {
        val ctx1 = SkillContext("list my installed apps", AgentWorkingMemory("list my installed apps"))
        assertTrue(appLauncherSkill.canHandle(ctx1.goal, ctx1))
        val res1 = appLauncherSkill.execute(ctx1.goal, ctx1)
        assertEquals("APPS_LIST", res1.proposedAction?.type)
        assertEquals("list", res1.proposedAction?.params?.get("action"))

        val ctx2 = SkillContext("find app whatsapp", AgentWorkingMemory("find app whatsapp"))
        assertTrue(appLauncherSkill.canHandle(ctx2.goal, ctx2))
        val res2 = appLauncherSkill.execute(ctx2.goal, ctx2)
        assertEquals("APPS_LIST", res2.proposedAction?.type)
        assertEquals("search", res2.proposedAction?.params?.get("action"))
        assertEquals("whatsapp", res2.proposedAction?.params?.get("query"))
    }
}
