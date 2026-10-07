package com.jarvis

import com.jarvis.agent.AgentWorkingMemory
import com.jarvis.agent.skills.SkillContext
import com.jarvis.agent.skills.SkillRegistry
import com.jarvis.agent.skills.SongSearchAndPlaySkill
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SongSearchAndPlaySkillTest {

    @Test
    fun testSongSearchAndPlaySkillIntentMatching() = runBlocking {
        val skill = SongSearchAndPlaySkill()
        val context = SkillContext(
            goal = "search and play song",
            workingMemory = AgentWorkingMemory(goal = "search and play song")
        )

        assertTrue(skill.canHandle("search and play song", context))
        assertTrue(skill.canHandle("research and play song", context))
        assertTrue(skill.canHandle("reseach and play song", context))
        assertTrue(skill.canHandle("search and plan song", context))
        assertTrue(skill.canHandle("search and play believer", context))
        assertTrue(skill.canHandle("find and play song from animal movie", context))
        assertTrue(skill.canHandle("gaana search karke chalao", context))
        assertTrue(skill.canHandle("song research karke play karo", context))
        assertTrue(skill.canHandle("pehle search karo fir gana chalao", context))
        assertTrue(skill.canHandle("dhundo aur chalao kesariya", context))
    }

    @Test
    fun testSongSearchAndPlayWithSpecificTrack() = runBlocking {
        val skill = SongSearchAndPlaySkill()
        val goal = "search and play believer song"
        val context = SkillContext(
            goal = goal,
            workingMemory = AgentWorkingMemory(goal = goal)
        )

        val result = skill.execute(goal, context)
        assertTrue(result.handled)
        assertNotNull(result.proposedAction)
        assertEquals("YOUTUBE_PLAY", result.proposedAction?.type)
        val query = result.proposedAction?.params?.get("query") ?: ""
        assertTrue("Query should contain track name: $query", query.contains("believer", ignoreCase = true))
        assertTrue("Candidate queries must not be empty", result.candidateQueries.isNotEmpty())
        assertTrue("Candidates should contain video/audio variants", result.candidateQueries.any { it.contains("video") || it.contains("audio") || it.contains("lyrics") })
    }

    @Test
    fun testSongSearchAndPlayGenericWithPreferences() = runBlocking {
        val skill = SongSearchAndPlaySkill()
        val goal = "search and play song"
        val context = SkillContext(
            goal = goal,
            workingMemory = AgentWorkingMemory(goal = goal),
            userPreferences = mapOf("favorite_artist" to "Arijit Singh")
        )

        val result = skill.execute(goal, context)
        assertTrue(result.handled)
        assertNotNull(result.proposedAction)
        val query = result.proposedAction?.params?.get("query") ?: ""
        assertTrue("Generic search with preference should select artist: $query", query.contains("Arijit Singh", ignoreCase = true))
        assertTrue("Should provide multiple candidate tracks for research", result.candidateQueries.size >= 2)
    }

    @Test
    fun testSongSearchAndPlayGenericWithoutPreferences() = runBlocking {
        val skill = SongSearchAndPlaySkill()
        val goal = "search and plan song"
        val context = SkillContext(
            goal = goal,
            workingMemory = AgentWorkingMemory(goal = goal),
            userPreferences = emptyMap()
        )

        val result = skill.execute(goal, context)
        assertTrue(result.handled)
        assertNotNull(result.proposedAction)
        val query = result.proposedAction?.params?.get("query") ?: ""
        assertTrue("Generic search without preference should pick trending music: $query", query.contains("trending", ignoreCase = true) || query.contains("top", ignoreCase = true))
        assertTrue("Candidate queries must include alternatives", result.candidateQueries.isNotEmpty())
    }

    @Test
    fun testSkillRegistryPrioritizesSongSearchAndPlayOverMediaSkill() = runBlocking {
        val registry = SkillRegistry().apply {
            register(SongSearchAndPlaySkill())
            register(com.jarvis.agent.skills.MediaSkill())
        }

        val goal = "search and play song from animal movie"
        val context = SkillContext(goal, AgentWorkingMemory(goal))
        val matched = registry.findSkill(goal, context)

        assertNotNull(matched)
        assertTrue("SongSearchAndPlaySkill should be selected for search and play request", matched is SongSearchAndPlaySkill)
    }
}
