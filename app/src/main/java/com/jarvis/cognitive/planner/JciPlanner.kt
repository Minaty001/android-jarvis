package com.jarvis.cognitive.planner

import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import com.jarvis.cognitive.capability.JciCapabilityRegistry
import com.jarvis.cognitive.capability.JciEvidenceLevel
import com.jarvis.cognitive.policy.JciPolicyEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class InvalidPlanException(message: String) : IllegalArgumentException(message)

class JciPlanner(
    private val llmClient: LlmClient? = null,
    private val policyEngine: JciPolicyEngine = JciPolicyEngine(),
    private val plannerTimeoutSeconds: Long = 20L
) {
    private val TAG = "JciPlanner"

    /**
     * Strictly validates a plan before execution begins.
     * Rejects plans referencing nonexistent capabilities or policy violations.
     */
    fun validatePlan(plan: JciPlan): PlanValidationResult {
        if (plan.steps.isEmpty()) {
            return PlanValidationResult(valid = false, error = "Plan contains no execution steps.")
        }

        val confirmationList = mutableListOf<String>()

        for (step in plan.steps) {
            val capability = JciCapabilityRegistry.getCapability(step.toolName)
                ?: return PlanValidationResult(
                    valid = false,
                    error = "Plan references unregistered or invented capability: '${step.toolName}'"
                )

            val decision = policyEngine.evaluateCapability(
                capability = capability,
                params = step.args,
                userConfirmed = false,
                isExecutionDispatch = false
            )
            if (decision.requiresConfirmation) {
                confirmationList.add(step.stepId)
            } else if (!decision.allowed) {
                return PlanValidationResult(
                    valid = false,
                    error = "Step '${step.stepId}' (${capability.id}) rejected by policy: ${decision.reason}"
                )
            }
        }

        return PlanValidationResult(valid = true, stepsNeedingConfirmation = confirmationList)
    }

    /**
     * Generates a validated multi-step execution plan for the requested goal.
     */
    suspend fun createPlan(
        goal: String,
        failedHistory: List<StepExecutionRecord> = emptyList()
    ): Result<JciPlan> {
        if (llmClient == null) {
            return Result.failure(IllegalStateException("No LLM client configured for planner."))
        }

        return try {
            val messages = buildPlannerPrompt(goal, failedHistory)
            val responseResult = withTimeoutOrNull(plannerTimeoutSeconds * 1000L) {
                llmClient.chat(messages)
            } ?: return Result.failure(IllegalStateException("Planner timed out after ${plannerTimeoutSeconds}s"))

            if (!responseResult.isSuccess) {
                val err = responseResult.exceptionOrNull()?.message ?: "Unknown planner error"
                return Result.failure(IllegalStateException("Planner LLM error: $err"))
            }

            val rawOutput = responseResult.getOrNull().orEmpty()
            val parsedPlan = parsePlanJson(rawOutput, goal)
                ?: return Result.failure(InvalidPlanException("Failed to parse plan from LLM output."))

            val validation = validatePlan(parsedPlan)
            if (!validation.valid) {
                return Result.failure(InvalidPlanException(validation.error ?: "Plan validation failed."))
            }

            Result.success(parsedPlan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Exception during plan generation: ${e.message}")
            Result.failure(e)
        }
    }

    fun buildPlannerPrompt(
        goal: String,
        failedHistory: List<StepExecutionRecord>
    ): List<Message> {
        val capabilitiesList = JciCapabilityRegistry.getAllCapabilities()
            .joinToString("\n") { cap ->
                "- ${cap.id}: ${cap.description} (Risk: ${cap.riskLevel}, Evidence: ${cap.expectedEvidenceLevel})"
            }

        val systemPrompt = """
You are the multi-step planner for J.A.R.V.I.S.
Your mission is to decompose the user's goal into a sequential linear plan of tool execution steps.

Available System Capabilities (Use ONLY these names, DO NOT invent tools):
$capabilitiesList

Rules:
1. Every step must reference a tool from the Available System Capabilities list above.
2. Provide all required arguments for each step.
3. Keep the plan small, concise, and minimal (maximum 8 steps).
4. Do NOT include redundant or speculative steps.

Output Format:
You MUST output ONLY a valid JSON object matching this schema (no markdown formatting, no code blocks):
{
  "goal": "summary of goal",
  "reason": "explanation of planning strategy",
  "steps": [
    {
      "step_id": "step_1",
      "tool_name": "CAPABILITY_ID",
      "args": {"param1": "val1"},
      "description": "what this step achieves",
      "retryable": true
    }
  ]
}
""".trimIndent()

        val failedContext = if (failedHistory.isNotEmpty()) {
            "Previous attempts and failures:\n" + failedHistory.joinToString("\n") {
                "- Step ${it.step.stepId} (${it.step.toolName}): ${it.error ?: it.toolOutput}"
            } + "\n\nPlease revise your plan to avoid these failures.\n\n"
        } else ""

        val userMessage = """
${failedContext}User Goal:
$goal
""".trimIndent()

        return listOf(
            Message("system", systemPrompt),
            Message("user", userMessage)
        )
    }

    fun parsePlanJson(rawOutput: String, originalGoal: String): JciPlan? {
        val cleanJson = extractJson(rawOutput)
        if (cleanJson.isBlank()) return null

        return try {
            val root = JSONObject(cleanJson)
            val goal = root.optString("goal", originalGoal)
            val reason = root.optString("reason", "Multi-step plan")
            val stepsArray = root.optJSONArray("steps") ?: JSONArray()

            val stepsList = mutableListOf<PlanStep>()
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val stepId = stepObj.optString("step_id", "step_${i + 1}")
                val rawToolName = stepObj.optString("tool_name", "").trim()
                val canonicalToolName = JciCapabilityRegistry.getCanonicalName(rawToolName) ?: rawToolName

                val argsMap = mutableMapOf<String, String>()
                val argsObj = stepObj.optJSONObject("args")
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = argsObj.optString(k, "")
                    }
                }

                val desc = stepObj.optString("description", "")
                val retryable = stepObj.optBoolean("retryable", true)
                val expectedEvidence = JciCapabilityRegistry.getCapability(canonicalToolName)?.expectedEvidenceLevel
                    ?: JciEvidenceLevel.TOOL_RETURN

                stepsList.add(
                    PlanStep(
                        stepId = stepId,
                        toolName = canonicalToolName,
                        args = argsMap,
                        expectedEvidence = expectedEvidence,
                        description = desc,
                        retryable = retryable
                    )
                )
            }

            JciPlan(goal = goal, steps = stepsList, reason = reason)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing plan JSON: ${e.message}")
            null
        }
    }

    private fun extractJson(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json")
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```")
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```")
        }
        trimmed = trimmed.trim()

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }
}
