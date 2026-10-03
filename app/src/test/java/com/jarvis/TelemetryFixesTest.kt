package com.jarvis

import com.jarvis.agent.AgentWorkingMemory
import com.jarvis.agent.skills.AppControlSkill
import com.jarvis.agent.skills.BrowserSkill
import com.jarvis.agent.skills.MediaSkill
import com.jarvis.agent.skills.SkillContext
import com.jarvis.tools.AppRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryFixesTest {

    @Test
    fun testMediaSkillDoesNotTriggerOnHindiVerbBola() = runBlocking {
        val skill = MediaSkill()
        val goal = "Jo file maine abhi banane ke liye bola tha usko open karo"
        val context = SkillContext(goal, AgentWorkingMemory(goal))

        assertFalse("MediaSkill should NOT trigger on 'bola'", skill.canHandle(goal, context))
    }

    @Test
    fun testBrowserSkillDoesNotInterceptFileCreationWithDotInFilename() = runBlocking {
        val skill = BrowserSkill()
        val goal = "Mere bare mein jitna bhi Tum jante ho usko Ek file mein banakar usmein add karo aur file ka naam personal.in Rakhna"
        val context = SkillContext(goal, AgentWorkingMemory(goal))

        assertFalse("BrowserSkill should NOT intercept prompt creating file personal.in", skill.canHandle(goal, context))
    }

    @Test
    fun testAppRegistryResolvesKotakGalaxyStoreMobikwikAndDocuments() {
        assertEquals("com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge", AppRegistry.ALLOWED_PACKAGES["kotak 811"])
        assertEquals("com.sec.android.app.samsungapps", AppRegistry.ALLOWED_PACKAGES["galaxy store"])
        assertEquals("com.mobikwik_new", AppRegistry.ALLOWED_PACKAGES["movie quick"])
        assertEquals("com.mobikwik_new", AppRegistry.ALLOWED_PACKAGES["mobikwik"])
        assertEquals("com.sec.android.app.myfiles", AppRegistry.ALLOWED_PACKAGES["documents"])
    }

    @Test
    fun testAppControlSkillHandlesSlideGestures() = runBlocking {
        val skill = AppControlSkill()
        val goal = "niche slide karo"
        val context = SkillContext(goal, AgentWorkingMemory(goal))

        assertTrue("AppControlSkill should handle 'niche slide karo'", skill.canHandle(goal, context))
        val result = skill.execute(goal, context)
        assertTrue(result.handled)
        assertNotNull(result.proposedAction)
        assertEquals("UI_SCROLL", result.proposedAction?.type)
        assertEquals("down", result.proposedAction?.params?.get("direction"))
    }
}
