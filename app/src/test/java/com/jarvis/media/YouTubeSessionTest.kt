package com.jarvis.media

import com.jarvis.agent.skills.MediaSkill
import com.jarvis.agent.skills.SkillContext
import com.jarvis.tools.YouTubePlayTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeSessionTest {

    @Before
    fun setUp() {
        YouTubeSessionManager.clear()
    }

    @Test
    fun testYouTubeSessionManagerTracking() {
        YouTubeSessionManager.recordVideoPlay("Lofi Hip Hop", videoId = "jfKfPfyJRdk")
        assertEquals("Lofi Hip Hop", YouTubeSessionManager.getCurrentVideo()?.query)
        assertEquals("jfKfPfyJRdk", YouTubeSessionManager.getCurrentVideo()?.videoId)
        assertTrue(YouTubeSessionManager.isPlaying())

        YouTubeSessionManager.recordVideoPlay("Coke Studio Season 14", videoId = "5Eqb_-j3FDA")
        assertEquals("Coke Studio Season 14", YouTubeSessionManager.getCurrentVideo()?.query)
        assertEquals(1, YouTubeSessionManager.getHistory().size)
        assertEquals("Lofi Hip Hop", YouTubeSessionManager.getPreviousVideo()?.query)

        val playedPrev = YouTubeSessionManager.playPreviousVideo()
        assertNotNull(playedPrev)
        assertEquals("Lofi Hip Hop", playedPrev?.query)
        assertEquals("Lofi Hip Hop", YouTubeSessionManager.getCurrentVideo()?.query)
    }

    @Test
    fun testYouTubeSessionPauseAndResume() {
        YouTubeSessionManager.recordVideoPlay("Bollywood Chill Beats")
        assertTrue(YouTubeSessionManager.isPlaying())

        YouTubeSessionManager.setPlaybackState(false)
        assertEquals(false, YouTubeSessionManager.isPlaying())

        YouTubeSessionManager.setPlaybackState(true)
        assertTrue(YouTubeSessionManager.isPlaying())
    }

    @Test
    fun testYouTubePlayToolSessionActions() = runBlocking {
        val tool = YouTubePlayTool(context = null)
        
        // Play video 1
        tool.execute(mapOf("query" to "Arijit Singh Hits"))
        assertEquals("Arijit Singh Hits", YouTubeSessionManager.getCurrentVideo()?.query)

        // Play video 2
        tool.execute(mapOf("query" to "Atif Aslam Hits"))
        assertEquals("Atif Aslam Hits", YouTubeSessionManager.getCurrentVideo()?.query)

        // Query status
        val currentRes = tool.execute(mapOf("action" to "current"))
        assertTrue(currentRes.success)
        assertTrue(currentRes.message.contains("Atif Aslam Hits"))

        // Query history
        val historyRes = tool.execute(mapOf("action" to "history"))
        assertTrue(historyRes.success)

        // Pause
        val pauseRes = tool.execute(mapOf("action" to "pause"))
        assertTrue(pauseRes.success)
        assertEquals(false, YouTubeSessionManager.isPlaying())

        // Previous
        val prevRes = tool.execute(mapOf("action" to "previous"))
        assertTrue(prevRes.success)
        assertEquals("Arijit Singh Hits", YouTubeSessionManager.getCurrentVideo()?.query)
    }

    @Test
    fun testMediaSkillYouTubeTriggers() = runBlocking {
        val skill = MediaSkill()

        // YouTube History trigger
        val historyResult = skill.execute("show youtube history", SkillContext("show youtube history", com.jarvis.agent.AgentWorkingMemory("show youtube history")))
        assertTrue(historyResult.handled)
        assertEquals("YOUTUBE_PLAY", historyResult.proposedAction?.type)
        assertEquals("history", historyResult.proposedAction?.params?.get("action"))

        // YouTube Current trigger
        val currentResult = skill.execute("kya chal raha hai youtube pe", SkillContext("kya chal raha hai youtube pe", com.jarvis.agent.AgentWorkingMemory("kya chal raha hai youtube pe")))
        assertTrue(currentResult.handled)
        assertEquals("YOUTUBE_PLAY", currentResult.proposedAction?.type)
        assertEquals("current", currentResult.proposedAction?.params?.get("action"))

        // YouTube Previous trigger
        val prevResult = skill.execute("pichla youtube video chalao", SkillContext("pichla youtube video chalao", com.jarvis.agent.AgentWorkingMemory("pichla youtube video chalao")))
        assertTrue(prevResult.handled)
        assertEquals("YOUTUBE_PLAY", prevResult.proposedAction?.type)
        assertEquals("previous", prevResult.proposedAction?.params?.get("action"))

        // YouTube Pause trigger
        val pauseResult = skill.execute("youtube pause karo", SkillContext("youtube pause karo", com.jarvis.agent.AgentWorkingMemory("youtube pause karo")))
        assertTrue(pauseResult.handled)
        assertEquals("YOUTUBE_PLAY", pauseResult.proposedAction?.type)
        assertEquals("pause", pauseResult.proposedAction?.params?.get("action"))
    }
}
