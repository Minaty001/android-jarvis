package com.jarvis.agent

import com.jarvis.agent.skills.SkillRegistry
import com.jarvis.skills.db.PersistedSkillStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillDiscoveryEngineTest {

    @Test
    fun approvedProposalIsRegisteredAsDynamicSkill() {
        val registry = SkillRegistry()
        val engine = SkillDiscoveryEngine(skillRegistry = registry)
        val proposal = ProposedSkillClarification(
            skillId = "workout_mode",
            proposedName = "Workout Mode",
            description = "Open Spotify and play a workout playlist",
            suggestedTriggers = listOf("workout mode"),
            steps = listOf(PersistedSkillStep(0, "OPEN_APP", mapOf("app" to "spotify"))),
            clarificationPrompt = "Save Workout Mode?"
        )

        val persisted = engine.persistDiscoveredSkill(proposal)

        assertEquals("Workout Mode", persisted?.name)
        assertTrue(registry.all().any { it.name == "Workout Mode" })
    }

    @Test
    fun declinedProposalIsNotPersistedOrRegistered() {
        val registry = SkillRegistry()
        val engine = SkillDiscoveryEngine(skillRegistry = registry)
        val proposal = ProposedSkillClarification(
            skillId = "test_routine",
            proposedName = "Test Routine",
            description = "A test routine",
            suggestedTriggers = listOf("test routine"),
            steps = emptyList(),
            clarificationPrompt = "Save Test Routine?"
        )

        assertNull(engine.persistDiscoveredSkill(proposal, userApproved = false))
        assertTrue(registry.all().isEmpty())
    }
}
