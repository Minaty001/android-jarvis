package com.jarvis.agent.skills

import com.jarvis.agent.AgentWorkingMemory
import com.jarvis.foundation.worldstate.ForegroundAppSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControlSkillTest {
    @Test
    fun foregroundScrollAndSearchUseAccessibilityAutopilotAcrossSupportedApps() = runBlocking {
        val skill = AppControlSkill()

        for (app in ForegroundAppSession.sharedUiApps) {
            val context = SkillContext("", AgentWorkingMemory(""), activeAppSession = app)

            assertTrue("scroll in $app", skill.canHandle("scroll down", context))
            val scroll = skill.execute("scroll down", context).proposedAction!!
            assertEquals("APP_AUTOPILOT", scroll.type)
            assertEquals("scroll", scroll.params["action"])
            assertEquals("down", scroll.params["direction"])

            assertTrue("search in $app", skill.canHandle("search for hiking boots", context))
            val search = skill.execute("search for hiking boots", context).proposedAction!!
            assertEquals("APP_AUTOPILOT", search.type)
            assertEquals("search", search.params["action"])
            assertEquals("hiking boots", search.params["text"])
        }
    }

    @Test
    fun explicitWebSearchAndUnscopedScrollKeepTheirExistingRoutes() = runBlocking {
        val skill = AppControlSkill()
        val chrome = SkillContext("", AgentWorkingMemory(""), activeAppSession = "Chrome")
        val noApp = SkillContext("", AgentWorkingMemory(""))

        assertFalse(skill.canHandle("search web for hiking boots", chrome))
        assertEquals("UI_SCROLL", skill.execute("scroll down", noApp).proposedAction?.type)
    }

    @Test
    fun readScreenUsesScreenVisionForEverySupportedForegroundApp() = runBlocking {
        val skill = VisionSkill()

        for (app in ForegroundAppSession.sharedUiApps) {
            val context = SkillContext("", AgentWorkingMemory(""), activeAppSession = app)
            assertTrue("read screen in $app", skill.canHandle("read the screen", context))
            assertEquals("SCREEN_VISION", skill.execute("read the screen", context).proposedAction?.type)
        }
    }

    @Test
    fun youtubePlaybackStillUsesTheDedicatedPlaybackAction() = runBlocking {
        val skill = MediaSkill()
        val youtube = SkillContext("", AgentWorkingMemory(""), activeAppSession = "YouTube")

        assertTrue(skill.canHandle("play lo-fi beats", youtube))
        assertEquals("YOUTUBE_PLAY", skill.execute("play lo-fi beats", youtube).proposedAction?.type)
    }
}
