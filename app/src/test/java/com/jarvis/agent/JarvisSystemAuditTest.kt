package com.jarvis.agent

import com.jarvis.agent.skills.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * System Audit Test Suite for Jarvis
 *
 * This suite automatically tests the entire SkillRegistry and Fallback rules pipeline
 * to ensure that all documented features translate into the correct AgentAction
 * without needing an LLM (using the deterministic local engine).
 */
class JarvisSystemAuditTest {

    private val registry = SkillRegistry().apply {
        register(SongSearchAndPlaySkill())
        register(MediaSkill())
        register(AppControlSkill())
        register(AppAutopilotSkill())
        register(VisionSkill())
        register(TelecomSkill())
        register(CommunicationSkill())
        register(ResearchSkill())
        register(BrowserSkill())
        register(AutonomousSkill())
        register(CalendarSkill())
        register(FileSkill())
        register(LocationSkill())
        register(LogSkill())
        register(MacroSkill())
        register(ProactiveSkill())
        register(RagSkill())
        register(RoutineSkill())
        register(SecuritySkill())
        register(TimeSkill())
        register(DeviceHardwareSkill())
        register(AppLauncherSkill())
    }

    private fun assertAction(goal: String, expectedActionType: String) = runBlocking {
        val memory = AgentWorkingMemory(goal = goal)
        val context = SkillContext(goal = goal, workingMemory = memory)
        
        // 1. Try SkillRegistry
        val matchedSkill = registry.findSkill(goal, context)
        if (matchedSkill != null) {
            val result = matchedSkill.execute(goal, context)
            if (result.handled && result.proposedAction != null) {
                assertEquals("Goal: \"$goal\" (Skill: ${matchedSkill.name})", expectedActionType, result.proposedAction.type)
                return@runBlocking
            }
        }
        
        // 2. Try Fallback Rules (Static)
        val fallback = AgentKernel.matchFallbackRules(goal)
        if (fallback.reasonCode != "UNRECOGNIZED_FALLBACK") {
            assertEquals("Goal: \"$goal\" (Fallback Rule)", expectedActionType, fallback.action?.type)
            return@runBlocking
        }
        
        // 3. Fail if neither handled it locally
        org.junit.Assert.fail("Goal: \"$goal\" was not handled by any local skill or rule. Expected: $expectedActionType")
    }

    // ─── 1. Hardware & System Skills ──────────────────────────────────────────

    @Test
    fun testHardwareControl() {
        assertAction("turn on flashlight", "FLASHLIGHT")
        assertAction("turn off bluetooth", "SYSTEM_SWITCHBOARD")
        assertAction("turn on wifi", "SYSTEM_SWITCHBOARD")
        assertAction("set volume to 50", "DEVICE_SETTINGS")
        assertAction("check battery", "BATTERY_CHECK")
        assertAction("where am i", "LOCATION")
    }

    // ─── 2. Media & Entertainment ─────────────────────────────────────────────

    @Test
    fun testMediaSkills() {
        assertAction("play blinding lights on spotify", "SPOTIFY_PLAY")
        assertAction("pause music", "MEDIA_CONTROL")
        assertAction("resume music", "MEDIA_CONTROL")
    }

    // ─── 3. Telephony & Communication ─────────────────────────────────────────

    @Test
    fun testTelephonySkills() {
        assertAction("call mom", "TELEPHONY_CONTROL")
        assertAction("whatsapp john hello", "WHATSAPP_SEND")
        assertAction("read my messages", "MESSAGE_READER")
        assertAction("answer the call", "TELEPHONY_CONTROL")
        assertAction("decline the call", "TELEPHONY_CONTROL")
    }

    // ─── 4. App & Autonomy Control ────────────────────────────────────────────

    @Test
    fun testAppAutopilot() {
        assertAction("open instagram", "OPEN_APP")
        assertAction("close whatsapp", "CLOSE_APP")
        assertAction("close all apps", "APPS_CLOSE_ALL")
        assertAction("scroll down", "UI_SCROLL")
        assertAction("click on settings", "UI_CLICK")
        assertAction("go back", "UI_GLOBAL")
    }

    // ─── 5. Web & Search ──────────────────────────────────────────────────────

    @Test
    fun testWebResearch() {
        assertAction("search the web for python tutorial", "SEARCH_WEB")
        assertAction("open google.com", "OPEN_APP")
    }

    // ─── 6. Automation & Routines ─────────────────────────────────────────────

    @Test
    fun testRoutinesAndMacros() {
        assertAction("run morning routine", "MACRO_WORKFLOW")
        assertAction("run macro", "MACRO_WORKFLOW")
    }
}
