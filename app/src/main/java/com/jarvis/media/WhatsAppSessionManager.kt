package com.jarvis.media

data class WhatsAppChatSession(
    val contactName: String,
    val phoneNumber: String? = null,
    val lastMessage: String? = null,
    val isOutbound: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages WhatsApp chat sessions, active contact, message history stack,
 * and previous chat rewind.
 */
object WhatsAppSessionManager {
    private var currentChat: WhatsAppChatSession? = null
    private val history = mutableListOf<WhatsAppChatSession>()

    @Synchronized
    fun recordChat(contactName: String, phoneNumber: String? = null, message: String? = null, isOutbound: Boolean = true) {
        currentChat?.let { prev ->
            if (history.isEmpty() || history.first().contactName != prev.contactName) {
                history.add(0, prev)
            }
            if (history.size > 50) {
                history.removeAt(history.lastIndex)
            }
        }

        val newSession = WhatsAppChatSession(
            contactName = contactName.trim(),
            phoneNumber = phoneNumber?.trim(),
            lastMessage = message?.trim(),
            isOutbound = isOutbound
        )
        currentChat = newSession
    }

    @Synchronized
    fun getCurrentChat(): WhatsAppChatSession? = currentChat

    @Synchronized
    fun getPreviousChat(): WhatsAppChatSession? {
        if (history.isEmpty()) return null
        val prev = history.removeAt(0)
        currentChat = prev.copy(timestamp = System.currentTimeMillis())
        return currentChat
    }

    @Synchronized
    fun getHistory(): List<WhatsAppChatSession> = history.toList()

    @Synchronized
    fun getHistorySummary(): String {
        if (history.isEmpty() && currentChat == null) {
            return "No WhatsApp chat session history available."
        }
        val sb = StringBuilder()
        currentChat?.let {
            sb.append("Active Chat: ${it.contactName} (${if (it.lastMessage != null) "Last Msg: '${it.lastMessage}'" else "No recent message"})\n")
        }
        if (history.isNotEmpty()) {
            sb.append("Recent WhatsApp Chats:\n")
            history.take(5).forEachIndexed { idx, item ->
                sb.append("${idx + 1}. ${item.contactName}\n")
            }
        }
        return sb.toString().trim()
    }

    @Synchronized
    fun clear() {
        currentChat = null
        history.clear()
    }
}
