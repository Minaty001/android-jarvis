package com.jarvis.agent

import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SubagentTask(
    val id: String,
    val role: String,
    val task: String
)

class SwarmOrchestrator(
    private val llmClient: LlmClient,
    private val onTaskCompleted: (String, String) -> Unit
) {
    private val TAG = "SwarmOrchestrator"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeAgents = ConcurrentHashMap<String, Job>()

    fun spawnAgent(role: String, task: String): String {
        val taskId = UUID.randomUUID().toString().substring(0, 8)
        val agentJob = scope.launch {
            Log.i(TAG, "Spawned subagent '\$taskId' with role: \$role. Task: \$task")
            
            val prompt = """
                You are a background Jarvis subagent.
                Your Role: \$role
                Your Task: \$task
                
                Execute the task to the best of your ability and return a final summary of your findings or actions. 
                Do not include any pleasantries, just the final result.
            """.trimIndent()

            val response = llmClient.chat(listOf(Message("user", prompt)))
            
            val result = if (response.isSuccess) {
                response.getOrNull() ?: "Subagent completed with no output."
            } else {
                "Subagent failed: \${response.exceptionOrNull()?.message}"
            }
            
            Log.i(TAG, "Subagent '\$taskId' completed.")
            onTaskCompleted(taskId, result)
            activeAgents.remove(taskId)
        }
        
        activeAgents[taskId] = agentJob
        return taskId
    }
    
    fun cancelAgent(taskId: String) {
        activeAgents[taskId]?.cancel()
        activeAgents.remove(taskId)
        Log.i(TAG, "Cancelled subagent '\$taskId'")
    }
}
