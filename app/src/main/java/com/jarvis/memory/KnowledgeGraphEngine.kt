package com.jarvis.memory

import android.content.Context
import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.foundation.TaskStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class KnowledgeGraphEngine(
    private val context: Context,
    private val llmClient: LlmClient,
    private val memoryStore: MemoryStore
) {
    private val TAG = "KnowledgeGraphEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun processInteraction(source: String, text: String) {
        scope.launch {
            try {
                // Ignore small talk or very short messages
                if (text.length < 15) return@launch

                val prompt = """
                    You are Jarvis, extracting structured Knowledge Graph entities from the following text.
                    Source: \$source
                    Text: "\$text"
                    
                    Extract nodes (entities like people, places, facts) and edges (relationships).
                    Format your response STRICTLY as a JSON array of objects with keys 'subject', 'predicate', 'object'.
                    Example: [{"subject": "Mom", "predicate": "lives in", "object": "New York"}]
                    If no clear facts are found, return [].
                """.trimIndent()
                
                val response = llmClient.chat(listOf(
                    com.jarvis.ai.Message("system", "You are a structured data extractor."),
                    com.jarvis.ai.Message("user", prompt)
                )).getOrNull()?.trim() ?: return@launch
                
                // Parse JSON array and store in MemoryStore or log
                try {
                    val startIndex = response.indexOf("[")
                    val endIndex = response.lastIndexOf("]")
                    if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                        val jsonStr = response.substring(startIndex, endIndex + 1)
                        val array = JSONArray(jsonStr)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val subj = obj.getString("subject")
                            val pred = obj.getString("predicate")
                            val objValue = obj.getString("object")
                            
                            val fact = "\$subj \$pred \$objValue"
                            Log.d(TAG, "Knowledge Graph Fact Extracted: \$fact")
                            
                                memoryStore.saveMemory(
                                type = MemoryType.LEARNED_PATTERN,
                                key = "kg_\${fact.hashCode()}",
                                content = fact,
                                provenance = source,
                                importance = 0.9,
                                namespace = "knowledge_graph"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse KG JSON: \${e.message}")
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error in Knowledge Graph extraction: \${e.message}")
            }
        }
    }
}
