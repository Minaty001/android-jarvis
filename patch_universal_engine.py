import sys

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "r") as f:
    content = f.read()

content = content.replace("class WhatsAppMessagingEngine", "class UniversalMessagingEngine")
content = content.replace("WhatsAppMessagingEngine", "UniversalMessagingEngine")
content = content.replace("var instance: UniversalMessagingEngine?", "var instance: UniversalMessagingEngine?")

# Update resolveTargetPackage
old_resolve = """        fun resolveTargetPackage(platform: String?): String {
            return when (platform?.lowercase()?.trim()) {
                "business", "whatsapp business", "wa business", "w4b" -> PKG_WHATSAPP_BIZ
                else -> PKG_WHATSAPP
            }
        }"""

new_resolve = """        fun resolveTargetPackage(platform: String?): String {
            return when (platform?.lowercase()?.trim()) {
                "business", "whatsapp business", "wa business", "w4b" -> PKG_WHATSAPP_BIZ
                "telegram", "tg" -> "org.telegram.messenger"
                "signal" -> "org.thoughtcrime.securesms"
                "instagram", "ig" -> "com.instagram.android"
                else -> PKG_WHATSAPP
            }
        }"""
content = content.replace(old_resolve, new_resolve)

# Update readRecentWhatsApp to readRecentMessages
content = content.replace("fun readRecentWhatsApp(", "fun readRecentMessages(")

# Update notification filtering
old_notif = """        val allNotifs = NotificationStore.getRecentNotifications(limit = 25, appFilter = "whatsapp")
        if (allNotifs.isEmpty()) {
            return ToolResult(true, "You have no unread WhatsApp messages right now.")
        }

        // Filter by platform if specified
        val platformFiltered = when (platformFilter?.lowercase()?.trim()) {
            "standard", "whatsapp" -> allNotifs.filter { it.packageName == PKG_WHATSAPP }
            "business", "w4b", "whatsapp business" -> allNotifs.filter { it.packageName == PKG_WHATSAPP_BIZ }
            else -> allNotifs
        }"""

new_notif = """        val targetPkg = resolveTargetPackage(platformFilter)
        val allNotifs = NotificationStore.getRecentNotifications(limit = 25, appFilter = targetPkg)
        if (allNotifs.isEmpty()) {
            return ToolResult(true, "You have no unread messages right now.")
        }
        val platformFiltered = allNotifs"""
content = content.replace(old_notif, new_notif)

with open("app/src/main/java/com/jarvis/messaging/UniversalMessagingEngine.kt", "w") as f:
    f.write(content)

