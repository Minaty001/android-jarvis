package com.jarvis.ai

import android.content.Context
import java.io.File
import dev.langchain4j.data.document.Document
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.data.document.splitter.DocumentSplitters

/**
 * Interface that AiServices will implement to act as our Assistant.
 */
interface Assistant {
    fun chat(userMessage: String): String
}

class LangChainAssistant(
    private val context: Context,
    private val apiKey: String,
    private val modelName: String = "gpt-3.5-turbo",
    private val baseUrl: String? = null // For Groq or OpenRouter
) {
    private val storeFile: String by lazy { File(context.filesDir, "langchain_rag.json").absolutePath }

    // 1. Setup Chat Memory (Retains last 10 messages)
    private val chatMemory = MessageWindowChatMemory.withMaxMessages(10)

    // 2. Setup Embedding Store for RAG (Persistent)
    private val embeddingStore: InMemoryEmbeddingStore<TextSegment> = try {
        InMemoryEmbeddingStore.fromFile(storeFile)
    } catch (e: Exception) {
        InMemoryEmbeddingStore<TextSegment>()
    }

    // 3. Setup Embedding Model (to convert text to vectors)
    private val embeddingModel: EmbeddingModel = OpenAiEmbeddingModel.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl ?: "https://api.openai.com/v1")
        .build()

    // 4. Setup Content Retriever
    private val contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(3)
        .minScore(0.7)
        .build()

    // 5. Setup LLM
    private val chatModel: ChatLanguageModel = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .baseUrl(baseUrl ?: "https://api.openai.com/v1")
        .build()

    // 6. Build the Assistant
    val assistant: Assistant = AiServices.builder(Assistant::class.java)
        .chatLanguageModel(chatModel)
        .chatMemory(chatMemory)
        .contentRetriever(contentRetriever)
        .build()

    /**
     * Call this function to add personal notes or documents to the RAG store.
     */
    fun ingestDocument(text: String) {
        try {
            val document = Document.from(text)
            val documentSplitter = DocumentSplitters.recursive(500, 50)
            val segments = documentSplitter.split(document)
            
            // Generate embeddings and store them
            val embeddings = embeddingModel.embedAll(segments).content()
            embeddingStore.addAll(embeddings, segments)
            
            // Persist to disk
            embeddingStore.serializeToFile(storeFile)
        } catch (e: Throwable) {
            android.util.Log.e("LangChainAssistant", "Failed to ingest document into RAG: ${e.message}", e)
        }
    }

    /**
     * Ingests and indexes user profile & personality facts into RAG vector memory.
     */
    fun ingestUserProfile(profile: com.jarvis.ui.model.UserProfile) {
        val summary = StringBuilder().apply {
            append("User Profile & Personal Facts:\n")
            append("- User Name: ${profile.userName}\n")
            append("- User Title/Preferred Address: ${profile.userTitle}\n")
            append("- User Occupation: ${profile.occupation}\n")
            append("- Personal Facts & Notes: ${profile.personalNotes}\n")
            append("- Preferred AI Persona Name: ${profile.aiPersonaName}\n")
            append("- AI Personality Style: ${profile.aiPersonalityStyle.name}\n")
            append("- Custom Instructions: ${profile.customSystemInstructions}\n")
        }.toString()

        ingestDocument(summary)
    }

    /**
     * Talk to the assistant using memory and RAG context.
     */
    fun chat(message: String): String {
        return assistant.chat(message)
    }
}
