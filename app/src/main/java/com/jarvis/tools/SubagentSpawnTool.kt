package com.jarvis.tools

import com.jarvis.agent.SwarmOrchestrator
import com.jarvis.execution.VerificationResult

class SubagentSpawnTool(
    private val orchestrator: SwarmOrchestrator
) : Tool {
    override val name = "SPAWN_AGENT"
    override val description = "Delegates a long-running or complex task to a specialized background subagent. Params: role, task"

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val role = params["role"] ?: return ToolResult.Failed("Parameter 'role' is required")
        val task = params["task"] ?: return ToolResult.Failed("Parameter 'task' is required")

        val agentId = orchestrator.spawnAgent(role, task)
        return ToolResult.Success("Background subagent '$agentId' spawned for role: '$role'. It will report back when finished.")
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        return if (result.success) {
            VerificationResult.success(result.message)
        } else {
            VerificationResult.failure(result.message)
        }
    }
}
