package com.jarvis.execution

import com.jarvis.tools.ToolResult

interface AgentVerifier {
    fun verify(actionType: String, params: Map<String, String>, result: ToolResult): VerificationResult
}
