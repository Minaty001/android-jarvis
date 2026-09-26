package com.jarvis.tools

import com.jarvis.ai.LangChainAssistant

class LangChainIngestTool(
    private val langChainAssistant: LangChainAssistant?
) : Tool {
    override val name: String = "REMEMBER"
    override val description: String = "Saves facts, notes, or long-term memories using LangChain vector storage"
    override val policy = ToolPolicy(idempotent = true)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val fact = params["fact"] ?: params.values.firstOrNull() ?: return ToolResult.Failed("Missing fact to remember.")
        if (langChainAssistant == null) {
            return ToolResult.Failed("LangChainAssistant is not initialized.")
        }

        return try {
            langChainAssistant.ingestDocument(fact)
            ToolResult.Success("Successfully remembered: $fact")
        } catch (e: Exception) {
            ToolResult.Failed("Failed to remember fact: ${e.message}")
        }
    }
}
