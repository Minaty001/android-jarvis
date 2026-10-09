import sys

with open("app/src/main/java/com/jarvis/tools/MessageReaderTool.kt", "r") as f:
    content = f.read()

content = content.replace(
    'if (action !in setOf("read_sms", "read_whatsapp", "read_all")) {',
    'if (action !in setOf("read_sms", "read_whatsapp", "read_telegram", "read_signal", "read_instagram", "read_all")) {'
)

content = content.replace(
    'val waLines = mutableListOf<String>()',
    'val waLines = mutableListOf<String>()\n        val platform = params["platform"]?.trim()?.lowercase()'
)

content = content.replace(
    'if (action == "read_whatsapp" || action == "read_all") {',
    'if (action.startsWith("read_") && action != "read_sms" || action == "read_all") {'
)

content = content.replace(
    'val engine = WhatsAppMessagingEngine.instance ?: context?.let { WhatsAppMessagingEngine(it) } ?: return',
    'val engine = com.jarvis.messaging.UniversalMessagingEngine.instance ?: context?.let { com.jarvis.messaging.UniversalMessagingEngine(it) } ?: return'
)

content = content.replace(
    'import com.jarvis.messaging.WhatsAppMessagingEngine',
    'import com.jarvis.messaging.UniversalMessagingEngine'
)

with open("app/src/main/java/com/jarvis/tools/MessageReaderTool.kt", "w") as f:
    f.write(content)
