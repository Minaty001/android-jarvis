package com.jarvis.ui.model

object ProviderRegistry {
    val providers = mapOf(
        "Groq" to listOf("llama3-70b-8192", "llama3-8b-8192", "mixtral-8x7b-32768", "gemma2-9b-it"),
        "Gemini" to listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b"),
        "OpenRouter" to listOf("anthropic/claude-3.5-sonnet", "deepseek/deepseek-r1", "openai/gpt-4o", "meta-llama/llama-3.1-405b-instruct", "google/gemini-pro-1.5")
    )

    fun getConfigFor(provider: String): ApiConfig {
        return when (provider) {
            "Groq" -> ApiConfig(name = "Groq", baseUrl = "https://api.groq.com/openai/v1", apiKeyHeader = "Authorization", authMethod = AuthMethod.BEARER)
            "Gemini" -> ApiConfig(name = "Gemini", baseUrl = "https://generativelanguage.googleapis.com", apiKeyHeader = "x-goog-api-key", authMethod = AuthMethod.API_KEY)
            "OpenRouter" -> ApiConfig(name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", apiKeyHeader = "Authorization", authMethod = AuthMethod.BEARER)
            else -> ApiConfig()
        }
    }
}
