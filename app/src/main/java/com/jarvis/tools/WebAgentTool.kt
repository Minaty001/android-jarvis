package com.jarvis.tools

import android.content.Context
import android.util.Log
import com.jarvis.ai.LlmClient
import com.jarvis.ai.Message
import com.jarvis.web.InvisibleWebBrowserEngine
import kotlinx.coroutines.delay
import org.json.JSONObject
import com.jarvis.execution.VerificationResult

class WebAgentTool(
    private val context: Context,
    private val llmClient: LlmClient?
) : Tool {
    override val name = "WEB_AGENT"
    override val description = "Navigates the web invisibly to complete tasks like searching, booking, or extracting information. Params: objective"
    
    private val TAG = "WebAgentTool"

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val objective = params["objective"] ?: return ToolResult.Failed("Objective is required")
        if (llmClient == null) return ToolResult.Failed("LLM Client is required")

        val browserEngine = InvisibleWebBrowserEngine(context)
        browserEngine.initialize()
        
        var currentStep = 0
        var lastAction = "started"
        var lastResult = "None"
        
        try {
            while (currentStep < 10) {
                currentStep++
                delay(3000) // Wait for network and DOM to settle
                
                val jsExtractor = """
                    (function() {
                        let elements = [];
                        let idCounter = 1;
                        document.querySelectorAll('a, button, input, textarea, select, h1, h2, p').forEach(el => {
                            let text = (el.innerText || el.value || el.placeholder || '').trim().substring(0, 80).replace(/\n/g, ' ');
                            if (text && el.offsetWidth > 0 && el.offsetHeight > 0) {
                                if (el.tagName !== 'P' && el.tagName !== 'H1' && el.tagName !== 'H2') {
                                    el.setAttribute('data-jarvis-id', idCounter);
                                    elements.push(idCounter + ': [' + el.tagName + '] ' + text);
                                    idCounter++;
                                } else {
                                    elements.push('TEXT: ' + text);
                                }
                            }
                        });
                        return elements.join('\n');
                    })();
                """.trimIndent()
                
                val urlRaw = browserEngine.evaluateJavascript("window.location.href") ?: "about:blank"
                val url = urlRaw.replace("\"", "")
                val domRaw = browserEngine.evaluateJavascript(jsExtractor) ?: "Empty DOM"
                // The result from evaluateJavascript comes back JSON-encoded (with \n as \\n and double quotes),
                // so we do some light cleanup for the LLM.
                val domSummary = domRaw.replace("\\n", "\n").replace("\"", "")
                
                val prompt = """
                    You are JARVIS Autonomous Web Agent.
                    Objective: "$objective"
                    Current URL: $url
                    Step: $currentStep/10
                    Last Action Taken: $lastAction
                    Result of Last Action: $lastResult
                    
                    Simplified DOM (Interactive Elements have numerical IDs):
                    $domSummary
                    
                    Decide the IMMEDIATE NEXT ACTION to progress towards the objective.
                    Output ONLY valid JSON in this exact format, with NO markdown formatting:
                    {
                      "thought": "reasoning",
                      "action": "goto" | "click" | "type" | "done" | "fail",
                      "url": "https://...",
                      "element_id": 123,
                      "text": "text to type",
                      "reason": "final summary"
                    }
                """.trimIndent()
                
                val llmResponse = llmClient.chat(messages = listOf(Message("user", prompt)))
                if (!llmResponse.isSuccess) {
                    return ToolResult.Failed("LLM reasoning failed at step $currentStep: ${llmResponse.exceptionOrNull()?.message}")
                }
                
                val rawJson = llmResponse.getOrNull() ?: ""
                Log.d(TAG, "Web Agent Output: $rawJson")
                
                val actionData = try {
                    val jsonString = if (rawJson.contains("{")) {
                        rawJson.substring(rawJson.indexOf("{"), rawJson.lastIndexOf("}") + 1)
                    } else rawJson
                    JSONObject(jsonString)
                } catch (e: Exception) {
                    lastAction = "parsing error"
                    lastResult = "Failed to parse JSON: ${e.message}"
                    continue
                }
                
                val action = actionData.optString("action")
                val elementId = actionData.optInt("element_id", -1)
                val textToType = actionData.optString("text")
                val targetUrl = actionData.optString("url")
                val reason = actionData.optString("reason")
                
                lastAction = action
                
                when (action) {
                    "done" -> return ToolResult.Success("Objective achieved: $reason")
                    "fail" -> return ToolResult.Failed("Web Agent failed: $reason")
                    "goto" -> {
                        browserEngine.loadUrl(targetUrl)
                        lastResult = "Navigated to $targetUrl"
                    }
                    "click" -> {
                        val js = "document.querySelector('[data-jarvis-id=\"$elementId\"]').click(); 'success';"
                        browserEngine.evaluateJavascript(js)
                        lastResult = "Clicked element $elementId"
                    }
                    "type" -> {
                        val js = """
                            var el = document.querySelector('[data-jarvis-id=\"$elementId\"]');
                            el.value = '$textToType';
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                            'success';
                        """.trimIndent()
                        browserEngine.evaluateJavascript(js)
                        lastResult = "Typed '$textToType' into element $elementId"
                    }
                    else -> lastResult = "Unknown action type: $action"
                }
            }
            return ToolResult.Failed("Max steps reached before objective was completed.")
        } finally {
            browserEngine.destroy()
        }
    }
    
    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        return if (result.success) {
            VerificationResult.success(result.message, mapOf("outcome" to "WEB_TASK_COMPLETED"))
        } else {
            VerificationResult.failure(result.message)
        }
    }
}
