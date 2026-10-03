package com.jarvis

import com.jarvis.agent.AgentWorkingMemory
import com.jarvis.agent.skills.AppControlSkill
import com.jarvis.agent.skills.MediaSkill
import com.jarvis.agent.skills.SkillContext
import com.jarvis.agent.skills.SongSearchAndPlaySkill
import com.jarvis.tools.AppRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreInstallTest {

    @Test
    fun testOpenPlayStoreDoesNotTriggerMediaOrSongSearchSkills() = runBlocking {
        val mediaSkill = MediaSkill()
        val songSkill = SongSearchAndPlaySkill()
        val appSkill = AppControlSkill()

        val context = SkillContext(
            goal = "open play store",
            workingMemory = AgentWorkingMemory(goal = "open play store")
        )

        assertFalse("MediaSkill should NOT handle 'open play store'", mediaSkill.canHandle("open play store", context))
        assertFalse("SongSearchAndPlaySkill should NOT handle 'open play store'", songSkill.canHandle("open play store", context))

        assertTrue("AppControlSkill SHOULD handle 'open play store'", appSkill.canHandle("open play store", context))

        val result = appSkill.execute("open play store", context)
        assertTrue(result.handled)
        assertNotNull(result.proposedAction)
        assertEquals("OPEN_APP", result.proposedAction?.type)
        assertEquals("play store", result.proposedAction?.params?.get("app"))
    }

    @Test
    fun testInstallAndDownloadAppTriggersInstallAppAction() = runBlocking {
        val appSkill = AppControlSkill()

        val installContext = SkillContext(
            goal = "install telegram",
            workingMemory = AgentWorkingMemory(goal = "install telegram")
        )

        assertTrue(appSkill.canHandle("install telegram", installContext))
        val resultInstall = appSkill.execute("install telegram", installContext)
        assertTrue(resultInstall.handled)
        assertEquals("INSTALL_APP", resultInstall.proposedAction?.type)
        assertEquals("telegram", resultInstall.proposedAction?.params?.get("app"))

        val downloadContext = SkillContext(
            goal = "download whatsapp from play store",
            workingMemory = AgentWorkingMemory(goal = "download whatsapp from play store")
        )
        assertTrue(appSkill.canHandle("download whatsapp from play store", downloadContext))
        val resultDownload = appSkill.execute("download whatsapp from play store", downloadContext)
        assertTrue(resultDownload.handled)
        assertEquals("INSTALL_APP", resultDownload.proposedAction?.type)
        assertEquals("whatsapp", resultDownload.proposedAction?.params?.get("app"))
    }

    @Test
    fun testAppRegistryResolvesPlayStorePackages() {
        assertEquals("com.android.vending", AppRegistry.ALLOWED_PACKAGES["play store"])
        assertEquals("com.android.vending", AppRegistry.ALLOWED_PACKAGES["playstore"])
        assertEquals("com.android.vending", AppRegistry.ALLOWED_PACKAGES["google play"])
        assertEquals("com.android.vending", AppRegistry.ALLOWED_PACKAGES["google play store"])
    }
}
