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
     * Proactively analyzes a sequence of recent user commands to detect automation opportunities
     * and propose new compound skills with structured clarification prompts.
     */
    suspend fun analyzeForSkillOpportunity(
        recentGoals: List<String>,
        llm: com.jarvis.ai.LlmClient
    ): ProposedSkillClarification? {
        if (recentGoals.isEmpty()) return null
        val prompt = """
            You are the JARVIS Skill Discovery Engine.
            Analyze the following recent user requests and identify if there is an opportunity to extract
            a repeatable composite multi-step skill or routine:
            ${recentGoals.takeLast(10).mapIndexed { i, g -> "${i + 1}. $g" }.joinToString("\n")}

            If a clear pattern or composite workflow exists, return strict JSON:
            {
              "skill_id": "unique_skill_id",
              "proposed_name": "Short Name",
              "description": "What this skill accomplishes",
              "triggers": ["trigger 1", "trigger 2"],
              "clarification_prompt": "Would you like me to remember this routine?",
              "steps": [
                {"step_index": 1, "action_type": "ACTION_TYPE", "params": {}}
              ]
            }
            If no worthwhile skill opportunity is found, return strict JSON: {"skill_id": null}
        """.trimIndent()

        val messages = listOf(
            com.jarvis.ai.Message("system", prompt),
            com.jarvis.ai.Message("user", "Analyze recent goals for skill discovery")
        )

        val raw = llm.chat(messages).getOrNull() ?: return null
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return runCatching {
            val json = org.json.JSONObject(trimmed.substring(start, end + 1))
            val id = json.optString("skill_id", "")
            if (id.isBlank() || id == "null") return@runCatching null
            val name = json.optString("proposed_name", id)
            val desc = json.optString("description", "")
            val promptQ = json.optString("clarification_prompt", "Would you like to save this skill?")

            val triggersList = mutableListOf<String>()
            val triggersArr = json.optJSONArray("triggers")
            if (triggersArr != null) {
                for (i in 0 until triggersArr.length()) {
                    triggersList.add(triggersArr.getString(i))
                }
            }

            val stepsList = mutableListOf<PersistedSkillStep>()
            val stepsArr = json.optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(i)
                    val sIdx = sObj.optInt("step_index", i + 1)
                    val actType = sObj.optString("action_type", "")
                    val paramsMap = mutableMapOf<String, String>()
                    val pObj = sObj.optJSONObject("params")
                    if (pObj != null) {
                        for (k in pObj.keys()) {
                            paramsMap[k] = pObj.optString(k, "")
                        }
                    }
                    if (actType.isNotBlank()) {
                        stepsList.add(PersistedSkillStep(sIdx, actType, paramsMap))
                    }
                }
            }

            if (stepsList.isEmpty()) return@runCatching null

            ProposedSkillClarification(
                skillId = id,
                proposedName = name,
                description = desc,
                suggestedTriggers = triggersList,
                steps = stepsList,
                clarificationPrompt = promptQ
            )
        }.getOrNull()
    }

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
