package com.jarvis.conversation

import android.util.Log
import com.jarvis.ai.Message
import com.jarvis.voice.VoiceState
import com.jarvis.wakeword.RaphaelPhoneticMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationManager(private val memoryStore: com.jarvis.memory.MemoryStore? = null) {
    private val TAG = "ConversationManager"
    private val _stateFlow = MutableStateFlow(VoiceState.IDLE)
    val stateFlow: StateFlow<VoiceState> = _stateFlow.asStateFlow()

    private var activeSessionId = "session_${System.currentTimeMillis()}"
    private var lastMessageTime = System.currentTimeMillis()
    private val recentMessages = mutableListOf<Message>()

    val currentStateValue: VoiceState
        get() = _stateFlow.value

    @Synchronized
    fun transitionTo(newState: VoiceState): Boolean {
        val oldState = _stateFlow.value
        if (isValidTransition(oldState, newState)) {
            _stateFlow.value = newState
            Log.i(TAG, "State transition: $oldState -> $newState")
            return true
        } else {
            Log.w(TAG, "Invalid state transition attempted: $oldState -> $newState")
            return false
        }
    }

    private fun isValidTransition(from: VoiceState, to: VoiceState): Boolean {
        if (to == VoiceState.ERROR || from == VoiceState.ERROR) return true
        if (from == to) return true
        return when (from) {
            VoiceState.IDLE -> to == VoiceState.WAITING_FOR_WAKE || to == VoiceState.OFFLINE
            VoiceState.WAITING_FOR_WAKE -> true
            VoiceState.WAKE_DETECTED -> to == VoiceState.LISTENING || to == VoiceState.WAITING_FOR_WAKE || to == VoiceState.UNDERSTANDING || to == VoiceState.THINKING || to == VoiceState.EXECUTING || to == VoiceState.SPEAKING
            VoiceState.LISTENING -> to == VoiceState.THINKING || to == VoiceState.UNDERSTANDING || to == VoiceState.WAITING_FOR_WAKE || to == VoiceState.EXECUTING || to == VoiceState.SPEAKING || to == VoiceState.IDLE
            VoiceState.UNDERSTANDING,
            VoiceState.CHECKING_MEMORY,
            VoiceState.RESEARCHING,
            VoiceState.THINKING,
            VoiceState.EXECUTING,
            VoiceState.VERIFYING,
            VoiceState.RECOVERING,
            VoiceState.WAITING_FOR_USER,
            VoiceState.COMPLETED,
            VoiceState.CANCELLED -> true
            VoiceState.SPEAKING -> to == VoiceState.WAITING_FOR_WAKE || to == VoiceState.INTERRUPTED || to == VoiceState.LISTENING || to == VoiceState.EXECUTING || to == VoiceState.IDLE || to == VoiceState.THINKING || to == VoiceState.COMPLETED
            VoiceState.INTERRUPTED -> to == VoiceState.LISTENING || to == VoiceState.WAITING_FOR_WAKE
            VoiceState.OFFLINE -> to == VoiceState.IDLE || to == VoiceState.WAITING_FOR_WAKE
            VoiceState.ERROR -> to == VoiceState.WAITING_FOR_WAKE || to == VoiceState.IDLE
        }
    }

    /**
     * Checks if the transcript is a wake-word-only utterance ("Jarvis" or custom wake word).
     * Returns cleaned command text, or null if it was wake-only.
     */
    fun processUtterance(rawTranscript: String, customWakeWord: String? = null): String? {
        val clean = rawTranscript.trim()
        if (clean.isBlank()) return null

        val stripped = RaphaelPhoneticMatcher.stripWakeWordPrefix(clean, customWakeWord)
        if (stripped.isBlank() || RaphaelPhoneticMatcher.isWakeWord(clean, customWakeWord)) {
            Log.i(TAG, "Utterance is wake-word-only ('$clean'). Skipping LLM dispatch.")
            return null
        }
        return stripped
    }

    @Synchronized
    fun addMessage(role: String, content: String) {
        val now = System.currentTimeMillis()
        if (now - lastMessageTime > 2 * 60 * 60 * 1000L) {
            activeSessionId = "session_$now"
            recentMessages.clear()
        }
        lastMessageTime = now
        recentMessages.add(Message(role, content))
        if (recentMessages.size > 10) recentMessages.removeAt(0)
        val sessionId = activeSessionId

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                memoryStore?.saveConversation(sessionId, role, content)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save conversation message", e)
            }
        }
    }

    @Synchronized
    fun getHistory(): List<Message> = recentMessages.toList()

    @Synchronized
    fun clearHistory() {
        activeSessionId = "session_${System.currentTimeMillis()}"
        recentMessages.clear()
    }

    /**
     * Checks if the user utterance is an explicit conversational closing phrase.
     */
    fun isExitUtterance(rawTranscript: String, customWakeWord: String? = null): Boolean {
        val stripped = RaphaelPhoneticMatcher.stripWakeWordPrefix(rawTranscript, customWakeWord)
        val lower = stripped.lowercase().trim()
            .replace(Regex("^(?:hey|hi|hello|ok|okay)?\\s*[,:]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("[,\\s]+(?:hey\\s+)?${Regex.escape((customWakeWord ?: "Jarvis").substringAfterLast(' ').lowercase())}[.!?]*$"), "")
            .trim()
        val exitPhrases = setOf(
            "exit", "bye", "goodbye", "good bye", "bye bye", "good night", "tata", "alvida",
            "band karo", "chhod do", "niklo",
            "thank you", "thanks", "thank you so much", "thanks jarvis",
            "that's all", "thats all", "that is all", "that's it", "thats it",
            "nothing", "kuch nahi", "kuch nahi chahiye", "bas", "bas itna hi",
            "shukriya", "dhanyawad", "stop listening", "band karo sunna",
            "sleep", "go to sleep", "jarvis sleep", "jarvis go to sleep", "so jao",
            "stop open mic", "exit open mic", "close open mic", "open mic band karo",
            "turn off open mic", "chup ho jao"
        )
        return lower in exitPhrases || exitPhrases.any { lower == it }
    }

    fun isInterruptionControl(rawTranscript: String, customWakeWord: String? = null): Boolean {
        val stripped = RaphaelPhoneticMatcher.stripWakeWordPrefix(rawTranscript, customWakeWord)
        val lower = stripped.lowercase().trim()
            .replace(Regex("^(?:hey|hi|hello|ok|okay)?\\s*[,:]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        val interruptionWords = setOf(
            "mute", "pause", "stop", "wait", "hold on",
            "chup", "ruko", "thamo", "shant", "shant ho jao", "chup karo"
        )
        return lower in interruptionWords
    }

    fun getExitResponse(utterance: String): String {
        val lower = utterance.lowercase().trim()
        return when {
            lower.contains("thank") || lower.contains("shukriya") || lower.contains("dhanyawad") ->
                "You're welcome, boss. Let me know if you need anything else."
            lower.contains("open mic") ->
                "Open mic disabled. Returning to standby."
            lower.contains("sleep") || lower.contains("so jao") ->
                "Entering standby mode. Sleep well, boss."
            lower.contains("night") ->
                "Good night, boss. Sleep well."
            else ->
                "Goodbye boss. Have a great day!"
        }
    }
}
