import sys

with open("app/src/main/java/com/jarvis/messaging/WhatsAppMessagingEngine.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "fun readRecentWhatsApp(" in line:
        insert_idx = i
        break

code_to_insert = """
    suspend fun readChatVisually(contactName: String, platform: String? = null): ToolResult {
        val trimmedRecipient = contactName.trim()
        val targetPkg = resolveTargetPackage(platform)
        val platformLabel = if (targetPkg == PKG_WHATSAPP_BIZ) "WhatsApp Business" else "WhatsApp"

        val ctx = context ?: return ToolResult(false, "Context required for UI automation.")
        ensureScreenAwake()

        val isPhoneNumber = trimmedRecipient.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        var resolvedNumber: String? = if (isPhoneNumber) {
            trimmedRecipient.filter { it.isDigit() || it == '+' }
        } else null

        if (resolvedNumber == null) {
            val contact = contactResolver.resolveContact(trimmedRecipient)
            if (contact != null && contact.phoneNumber.isNotBlank()) {
                resolvedNumber = contact.phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }

        return runCatching {
            if (resolvedNumber != null) {
                val cleanDigits = formatWhatsAppNumber(resolvedNumber)
                val waUri = Uri.parse("https://wa.me/$cleanDigits")
                val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } else {
                val shareUri = Uri.parse("whatsapp://send")
                val intent = Intent(Intent.ACTION_VIEW, shareUri).apply {
                    setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                
                if (UiAutomationManager.isEnabled()) {
                    val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                    if (arrived) {
                        kotlinx.coroutines.delay(700)
                        val service = JarvisAccessibilityService.instance
                        val contactNode = service?.findNodeByText(trimmedRecipient, exact = false)
                        if (contactNode != null) {
                            service.clickNode(contactNode)
                        } else {
                            return ToolResult(false, "Could not find chat with '$trimmedRecipient' on screen.")
                        }
                    }
                }
            }
            
            if (UiAutomationManager.isEnabled()) {
                val arrived = UiAutomationManager.waitForPackage(targetPkg, timeoutMs = 3000)
                if (arrived) {
                    kotlinx.coroutines.delay(1200) // Wait for chat to load
                    val nodes = UiAutomationManager.inspectScreen()
                    val chatNodes = nodes.filter { it.className == "android.widget.TextView" && !it.text.isNullOrBlank() }
                    val chatText = chatNodes.joinToString("\\n") { it.text!! }
                    autoReturnToHome()
                    if (chatText.isBlank()) return ToolResult(false, "Opened chat, but could not extract any text.")
                    return ToolResult(true, "Visually read chat with '$trimmedRecipient':\\n$chatText")
                }
            }
            ToolResult(false, "Accessibility automation must be enabled to read chat visually.")
        }.getOrElse { e ->
            ToolResult(false, "Failed to launch $platformLabel chat for reading: ${e.message}")
        }
    }

"""

lines.insert(insert_idx - 5, code_to_insert)

with open("app/src/main/java/com/jarvis/messaging/WhatsAppMessagingEngine.kt", "w") as f:
    f.writelines(lines)
