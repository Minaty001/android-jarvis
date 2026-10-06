package com.jarvis.agent

import android.content.Context
import android.util.Log
import com.jarvis.agent.skills.DynamicPersistedSkill
import com.jarvis.agent.skills.SkillRegistry
import com.jarvis.foundation.RiskLevel
import com.jarvis.skills.db.PersistedSkill
import com.jarvis.skills.db.PersistedSkillStep
import com.jarvis.skills.db.PersistedSkillType
import com.jarvis.skills.db.PersistentSkillStore

/**
 * Proposed new skill discovered from long commands or explicit user teaching requests.
 */
data class ProposedSkillClarification(
    val skillId: String,
    val proposedName: String,
    val description: String,
    val suggestedTriggers: List<String>,
    val steps: List<PersistedSkillStep>,
    val clarificationPrompt: String
)

/**
 * Interactive Brain Skill Discovery & Expansion Engine.
 *
 * Proactively identifies new capabilities, routines, or workflows inside user commands,
 * formulates structured clarification queries to ask the user, and persists new skills
 * dynamically into the persistent skill database and runtime registry.
 */
class SkillDiscoveryEngine(
    private val context: Context? = null,
    private val skillStore: PersistentSkillStore? = null,
    private val skillRegistry: SkillRegistry? = null
) {
    private val TAG = "SkillDiscoveryEngine"



    /**
     * Persists the user-approved skill and registers it live in SkillRegistry.
     */
    fun persistDiscoveredSkill(
        proposal: ProposedSkillClarification,
        userApproved: Boolean = true,
        customTrigger: String? = null
    ): PersistedSkill? {
        if (!userApproved) {
            Log.i(TAG, "Skill proposal '${proposal.proposedName}' was declined by user.")
            return null
        }

        val triggers = if (customTrigger != null && customTrigger.isNotBlank()) {
            (proposal.suggestedTriggers + customTrigger.lowercase().trim()).distinct()
        } else {
            proposal.suggestedTriggers
        }

        val persisted = PersistedSkill(
            id = proposal.skillId,
            name = proposal.proposedName,
            description = proposal.description,
            triggers = triggers,
            type = PersistedSkillType.DYNAMIC,
            version = 1,
            confidence = 1.0,
            status = "ACTIVE",
            riskLevel = RiskLevel.LOW,
            steps = proposal.steps
        )

        skillStore?.saveSkill(persisted)
        skillRegistry?.register(DynamicPersistedSkill(persisted))

        Log.i(TAG, "Successfully learned and activated new brain skill '${persisted.name}' (${persisted.id}) with ${persisted.steps.size} steps.")
        return persisted
    }
}
