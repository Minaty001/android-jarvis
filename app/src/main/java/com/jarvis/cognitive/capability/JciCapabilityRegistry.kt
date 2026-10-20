package com.jarvis.cognitive.capability

import com.jarvis.tools.ToolRegistry

class UnknownCapabilityException(val capabilityName: String) :
    IllegalArgumentException("Capability '$capabilityName' is not registered in the system.")

object JciCapabilityRegistry {

    private val capabilities = mutableMapOf<String, JciCapability>()
    private val aliasMap = mutableMapOf<String, String>()

    init {
        registerCanonicalCapabilities()
        registerAliases()
    }

    private fun register(cap: JciCapability) {
        val canonicalId = cap.id.uppercase()
        capabilities[canonicalId] = cap
        aliasMap[canonicalId] = canonicalId
        for (alias in cap.aliases) {
            aliasMap[alias.uppercase()] = canonicalId
        }
    }

    fun isRegistered(name: String): Boolean {
        return aliasMap.containsKey(name.uppercase().trim())
    }

    fun getCanonicalName(name: String): String? {
        return aliasMap[name.uppercase().trim()]
    }

    fun getCapability(name: String): JciCapability? {
        val canonical = getCanonicalName(name) ?: return null
        return capabilities[canonical]
    }

    fun requireCapability(name: String): JciCapability {
        return getCapability(name) ?: throw UnknownCapabilityException(name)
    }

    fun getAllCapabilities(): List<JciCapability> {
        return capabilities.values.toList()
    }

    private fun registerCanonicalCapabilities() {
        // 1. Hardware & Core
        register(JciCapability(
            id = "FLASHLIGHT",
            aliases = setOf("TORCH", "FLASH"),
            description = "Toggles camera LED torch",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = true,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = false, allowedValues = setOf("on", "off", "toggle"))
            )
        ))

        register(JciCapability(
            id = "DEVICE_SETTINGS",
            aliases = setOf("VOLUME_CONTROL", "SET_VOLUME", "VOLUME", "MUTE", "UNMUTE"),
            description = "Sets device volume, brightness, and audio settings",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = true,
            parameters = listOf(
                JciParamDefinition("setting", JciParamType.STRING, required = true),
                JciParamDefinition("value", JciParamType.STRING, required = false)
            )
        ))

        register(JciCapability(
            id = "SYSTEM_SWITCHBOARD",
            aliases = setOf("SET_RINGER", "RINGER", "RINGER_MODE", "SILENT_MODE", "VIBRATE_MODE", "BRIGHTNESS", "SET_BRIGHTNESS", "SCREEN_BRIGHTNESS", "BATTERY_SAVER", "POWER_SAVER"),
            description = "Toggles Wi-Fi, Bluetooth, ringer, and system hardware switches",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = true,
            parameters = listOf(
                JciParamDefinition("target", JciParamType.STRING, required = true),
                JciParamDefinition("action", JciParamType.STRING, required = false)
            )
        ))

        // 2. Apps & System Lifecycle
        register(JciCapability(
            id = "OPEN_APP",
            aliases = setOf("LAUNCH_APP", "APP_OPEN"),
            description = "Launches application package",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("app", JciParamType.APP_NAME, required = true)
            )
        ))

        register(JciCapability(
            id = "CLOSE_APP",
            aliases = setOf("APP_CLOSE"),
            description = "Closes active foreground app",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("app", JciParamType.APP_NAME, required = false)
            )
        ))

        register(JciCapability(
            id = "APPS_CLOSE_ALL",
            aliases = setOf("CLOSE_ALL_APPS", "CLOSE_ALL", "ALL_APPS_CLOSE"),
            description = "Closes all background tasks and recents",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            rollbackAvailable = false
        ))

        register(JciCapability(
            id = "APPS_LIST",
            aliases = setOf("SCAN_APPS", "LIST_APPS", "INSTALLED_APPS", "GET_APPS", "ALL_APPS"),
            description = "Queries installed packages on the device",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "INSTALL_APP",
            aliases = setOf("INSTALL", "DOWNLOAD", "DOWNLOAD_APP", "PLAYSTORE_INSTALL"),
            description = "Opens app in Play Store for installation",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("app_name", JciParamType.STRING, required = true)
            )
        ))

        // 3. Media & Playback
        register(JciCapability(
            id = "MUSIC_PLAY",
            aliases = setOf("PLAY_MUSIC", "MUSIC", "PLAY_SONG", "SONG_PLAY", "YMUSIC_PLAY", "YMUSIC"),
            description = "Plays music via local or online audio player",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            rollbackAvailable = true,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "YOUTUBE_PLAY",
            aliases = setOf("YOUTUBE", "PLAY_YOUTUBE", "YOUTUBE_SEARCH", "PLAY_VIDEO", "VIDEO_PLAY"),
            description = "Plays requested video or audio on YouTube",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            rollbackAvailable = true,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "MEDIA_CONTROL",
            aliases = setOf("MEDIA_STOP", "STOP_MEDIA", "STOP_MUSIC", "MUSIC_STOP", "MEDIA_PAUSE", "PAUSE_MUSIC", "PAUSE_MEDIA", "VOLUME_UP", "VOLUME_DOWN", "NEXT_TRACK", "PREVIOUS_TRACK", "RESUME_MUSIC"),
            description = "Controls active media playback (play, pause, stop, skip, volume)",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = true,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "SPOTIFY_PLAY",
            aliases = setOf("SPOTIFY", "PLAY_SPOTIFY", "SPOTIFY_SEARCH"),
            description = "Searches and plays music on Spotify",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true)
            )
        ))

        // 4. Information, Search & Math
        register(JciCapability(
            id = "SEARCH_WEB",
            aliases = setOf("SEARCH", "WEB_SEARCH", "GOOGLE_SEARCH", "GOOGLE"),
            description = "Performs web search and returns relevant information",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "RESEARCH_DEEP",
            aliases = setOf("DEEP_RESEARCH", "BACKGROUND_RESEARCH", "GENERATE_REPORT", "RESEARCH_REPORT", "RESEARCH"),
            description = "Conducts multi-step deep research and synthesizes structured report",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true),
                JciParamDefinition("depth", JciParamType.INT, required = false, minVal = 1.0, maxVal = 5.0)
            )
        ))

        register(JciCapability(
            id = "CALCULATE",
            aliases = setOf("CALCULATOR"),
            description = "Performs mathematical arithmetic and scientific calculations",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("expression", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "WEATHER",
            aliases = setOf("CHECK_WEATHER", "GET_WEATHER", "TEMPERATURE", "MAUSAM"),
            description = "Retrieves live weather conditions and forecasts",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "TRANSLATOR",
            aliases = setOf("TRANSLATE", "TRANSLATE_TEXT", "ANUVAD"),
            description = "Translates text across languages",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("text", JciParamType.STRING, required = true),
                JciParamDefinition("target_lang", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "CLOCK",
            aliases = setOf("TIMER", "SET_TIMER", "WORLD_CLOCK", "TIME_IN"),
            description = "Sets timers, alarms, and queries world clocks",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "UNIT_CONVERTER",
            aliases = setOf("CONVERT", "CURRENCY_CONVERTER", "CURRENCY", "UNIT_CONVERSION", "EXCHANGE_RATE"),
            description = "Converts physical units and currencies",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("from", JciParamType.STRING, required = true),
                JciParamDefinition("to", JciParamType.STRING, required = true),
                JciParamDefinition("value", JciParamType.FLOAT, required = true)
            )
        ))

        register(JciCapability(
            id = "NAVIGATION",
            aliases = setOf("NAVIGATE", "DIRECTIONS", "MAPS", "FIND_PLACE", "FIND_NEARBY", "NEARBY", "ROUTE_TO"),
            description = "Opens maps route navigation for destination",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("destination", JciParamType.STRING, required = true)
            )
        ))

        // 5. Memory, Notes & Vault
        register(JciCapability(
            id = "QUICK_NOTES",
            aliases = setOf("VOICE_NOTE", "ADD_NOTE", "LIST_NOTES", "SEARCH_NOTES"),
            description = "Reads, creates, and queries quick local notes",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "NOTE",
            aliases = setOf("SAVE_MEMORY", "SAVE_NOTE", "REMEMBER"),
            description = "Stores persistent memory facts and user preferences",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("content", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "SEARCH_MEMORY",
            aliases = setOf("FIND_MEMORY", "GET_MEMORY"),
            description = "Queries encrypted persistent memory via FTS",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "RAG_INDEX",
            aliases = setOf("INDEX_DOCUMENT", "INDEX_FILE", "INDEX_NOTES", "ADD_TO_VAULT"),
            description = "Indexes documents and notes into local knowledge vault",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("content", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "RAG_RETRIEVE",
            aliases = setOf("SEARCH_DOCS", "QUERY_KNOWLEDGE", "KNOWLEDGE_SEARCH", "FIND_IN_FILES", "RETRIEVE_KNOWLEDGE"),
            description = "Retrieves semantic passages from knowledge vault",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("query", JciParamType.STRING, required = true)
            )
        ))

        // 6. Communication (High-Risk Actions)
        register(JciCapability(
            id = "WHATSAPP",
            aliases = setOf("WHATSAPP_SEND", "SEND_WHATSAPP", "SEND_MESSAGE", "WHATSAPP_MESSAGE", "WHATSAPP_READ", "READ_WHATSAPP", "WHATSAPP_REPLY", "REPLY_WHATSAPP", "WA_BUSINESS"),
            description = "Sends or reads WhatsApp messages",
            classification = JciToolClassification.EXTERNAL_COMMUNICATION,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("recipient", JciParamType.STRING, required = true),
                JciParamDefinition("message", JciParamType.STRING, required = true)
            ),
            rateLimitMaxPerMinute = 5
        ))

        register(JciCapability(
            id = "TELEPHONY_CONTROL",
            aliases = setOf("CALL", "DIAL", "PHONE", "SMS", "TEXT_MESSAGE", "SEND_SMS", "FIND_CONTACT", "SEARCH_CONTACT", "ANSWER_CALL", "REJECT_CALL", "END_CALL", "READ_SMS"),
            description = "Places phone calls or sends SMS messages",
            classification = JciToolClassification.EXTERNAL_COMMUNICATION,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true, allowedValues = setOf("call", "sms", "find_contact", "answer", "reject", "end")),
                JciParamDefinition("recipient", JciParamType.STRING, required = true),
                JciParamDefinition("phone_number", JciParamType.PHONE_NUMBER, required = false),
                JciParamDefinition("message", JciParamType.STRING, required = false)
            ),
            rateLimitMaxPerMinute = 5
        ))

        register(JciCapability(
            id = "EMERGENCY_SOS",
            aliases = setOf("SOS", "BEACON", "STOP_SOS"),
            description = "Sends SOS distress alerts and dials emergency contact",
            classification = JciToolClassification.EXTERNAL_COMMUNICATION,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = false, allowedValues = setOf("start", "stop"))
            )
        ))

        // 7. Security, Screen Lock & Keyguard
        register(JciCapability(
            id = "SCREEN_LOCK",
            aliases = setOf("LOCK_SCREEN", "LOCK_PHONE", "LOCK_DEVICE", "DEVICE_LOCK", "PHONE_LOCK", "LOCK"),
            description = "Locks screen immediately",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = true
        ))

        register(JciCapability(
            id = "SCREEN_UNLOCK",
            aliases = setOf("UNLOCK_SCREEN", "UNLOCK_PHONE", "UNLOCK_DEVICE", "DEVICE_UNLOCK", "PHONE_UNLOCK", "UNLOCK", "WAKE_SCREEN", "TURN_ON_SCREEN"),
            description = "Wakes screen and unlocks keyguard with provided PIN",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            parameters = listOf(
                JciParamDefinition("pin", JciParamType.STRING, required = false)
            )
        ))

        register(JciCapability(
            id = "KEYGUARD_UNLOCK",
            aliases = setOf("DISMISS_KEYGUARD"),
            description = "Dismisses device keyguard and requests PIN/pattern unlock",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
            parameters = listOf(
                JciParamDefinition("pin", JciParamType.STRING, required = false)
            )
        ))

        register(JciCapability(
            id = "SECURITY_AUDIT",
            aliases = setOf("SECURITY", "PRIVACY_AUDIT", "PERMISSIONS_CHECK", "SCAN_SECURITY", "PRIVACY_SCORE", "SECURITY_AUDITOR"),
            description = "Audits device permissions and security posture",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        // 8. Files, Storage & Databases
        register(JciCapability(
            id = "FILE_MANAGER",
            aliases = setOf("FILES", "FILE_SEARCH", "STORAGE_CHECK", "CLEANUP_STORAGE", "STORAGE_BREAKDOWN", "FILE_LOCATE"),
            description = "Searches files, inspects storage breakdown, and cleans caches",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "FILE_READ",
            description = "Reads contents of local text or data file",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("path", JciParamType.FILE_PATH, required = true)
            )
        ))

        register(JciCapability(
            id = "FILE_WRITE",
            description = "Writes content to local text file",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("path", JciParamType.FILE_PATH, required = true),
                JciParamDefinition("content", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "FILE_DELETE",
            description = "Deletes specified file from device storage",
            classification = JciToolClassification.DESTRUCTIVE,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
            rollbackAvailable = false,
            parameters = listOf(
                JciParamDefinition("path", JciParamType.FILE_PATH, required = true)
            ),
            rateLimitMaxPerMinute = 5
        ))

        register(JciCapability(
            id = "DEVICE_WIPE",
            description = "Factory reset or device wipe",
            classification = JciToolClassification.DESTRUCTIVE,
            riskLevel = JciRiskLevel.CRITICAL,
            expectedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
            rollbackAvailable = false
        ))

        register(JciCapability(
            id = "ROOT_COMMAND",
            description = "Executes arbitrary root / su shell command",
            classification = JciToolClassification.DESTRUCTIVE,
            riskLevel = JciRiskLevel.CRITICAL,
            expectedEvidenceLevel = JciEvidenceLevel.USER_CONFIRMED,
            parameters = listOf(
                JciParamDefinition("cmd", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "JARVIS_DB",
            aliases = setOf("DB_STATUS", "DB_STATS", "DATABASE_STATUS", "VECTOR_DB", "VECTOR_DB_STATS", "STORAGE_DB", "JARVIS_STORAGE", "VECTOR_SEARCH", "SEARCH_VECTOR", "SEARCH_VECTORS", "INDEX_VECTOR", "STORE_VECTOR"),
            description = "Inspects internal database tables and vector metrics",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        // 9. Vision & Optical Perception
        register(JciCapability(
            id = "SCREEN_VISION",
            aliases = setOf("SCREEN_ANALYZE", "SCREEN_READ", "SCREEN_LOOK", "SCREEN_SUMMARIZE", "SEE_SCREEN", "LOOK_AT_SCREEN", "VISION"),
            description = "Captures screenshot and performs visual analysis",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("prompt", JciParamType.STRING, required = false)
            )
        ))

        register(JciCapability(
            id = "CAMERA_VISION",
            aliases = setOf("CAMERA_LOOK", "WHAT_AM_I_LOOKING_AT", "CAMERA_IDENTIFY", "CAMERA_SCAN", "IDENTIFY_OBJECT", "DESCRIBE_SCENE", "READ_OBJECT", "SEE_OBJECT", "OPTICAL_PERCEPTION"),
            description = "Captures camera frame and detects real-world objects",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        // 10. UI Automation & Accessibility
        register(JciCapability(
            id = "UI_CLICK",
            aliases = setOf("CLICK", "TAP"),
            description = "Simulates touch click on screen coordinate or node",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("x", JciParamType.INT, required = false),
                JciParamDefinition("y", JciParamType.INT, required = false)
            )
        ))

        register(JciCapability(
            id = "UI_SCROLL",
            aliases = setOf("SCROLL", "SWIPE", "SCROLL_DOWN", "SCROLL_UP"),
            description = "Simulates swipe or scroll gesture",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("direction", JciParamType.STRING, required = false, allowedValues = setOf("up", "down", "left", "right"))
            )
        ))

        register(JciCapability(
            id = "UI_TYPE",
            aliases = setOf("TYPE", "INPUT", "VOICE_TYPING"),
            description = "Types text into currently focused input field",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("text", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "UI_GESTURE",
            aliases = setOf("GESTURE", "LONG_PRESS"),
            description = "Performs custom gesture or long press",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK
        ))

        register(JciCapability(
            id = "UI_INSPECT",
            aliases = setOf("INSPECT", "INSPECT_SCREEN"),
            description = "Dumps active screen accessibility node hierarchy",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "UI_GLOBAL",
            aliases = setOf("GLOBAL_ACTION", "BACK", "HOME", "RECENTS", "SCREENSHOT", "NOTIFICATIONS_SHADE", "QUICK_SETTINGS"),
            description = "Triggers Android global accessibility action",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "CLIPBOARD_MANAGE",
            aliases = setOf("CLIPBOARD", "PASTE", "COPY", "OTP"),
            description = "Reads, writes, or retrieves OTP from clipboard",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true, allowedValues = setOf("copy", "paste", "read", "otp")),
                JciParamDefinition("text", JciParamType.STRING, required = false)
            )
        ))

        // 11. Autonomous Agent & Complex Workflows
        register(JciCapability(
            id = "APP_AUTOPILOT",
            aliases = setOf("UI_NAVIGATE", "AUTO_CLICK", "FILL_FORM", "YOUTUBE_SCROLL"),
            description = "Autonomous task autopilot navigating Android apps",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("goal", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "AUTONOMOUS_UI_AGENT",
            aliases = setOf("UI_AGENT", "AUTOPILOT", "UI_AUTOPILOT", "AUTO_UI"),
            description = "Vision-guided autonomous UI agent",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.HIGH,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("objective", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "WEB_AGENT",
            aliases = setOf("WEB_BROWSE", "BROWSE_WEB", "BROWSER"),
            description = "Automated web browsing agent extracting content",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("objective", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "ROUTINE_MANAGE",
            aliases = setOf("TRIGGER_ROUTINE", "RUN_ROUTINE", "LIST_ROUTINES", "SHOW_ROUTINES", "ENABLE_ROUTINE", "DISABLE_ROUTINE", "ROUTINES"),
            description = "Creates, manages, and executes automated routines",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "MACRO_WORKFLOW",
            aliases = setOf("RUN_MACRO", "EXECUTE_WORKFLOW", "LIST_MACROS", "SHOW_MACROS", "MACRO", "WORKFLOW"),
            description = "Executes recorded multi-step UI macros",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("macro_id", JciParamType.STRING, required = true)
            )
        ))

        // 12. System Telemetry, Notifications & Safety
        register(JciCapability(
            id = "BATTERY_CHECK",
            aliases = setOf("CHECK_BATTERY", "BATTERY_STATUS", "BATTERY_LEVEL", "BATTERY", "BATTERY_OPTIMIZER", "POWER_OPTIMIZER", "BATTERY_HEALTH", "THERMAL_STATUS"),
            description = "Queries battery level, charging state, and thermal status",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "NOTIFICATIONS_READ",
            aliases = setOf("READ_NOTIFICATIONS", "GET_NOTIFICATIONS", "CHECK_NOTIFICATIONS", "NOTIFICATIONS"),
            description = "Reads and summarizes incoming status bar notifications",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "DAILY_BRIEFING",
            aliases = setOf("MORNING_BRIEFING", "BRIEFING", "STATUS_REPORT"),
            description = "Aggregates morning status report (weather, battery, agenda, headlines)",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "JARVIS_PROTOCOL",
            aliases = setOf("DIAGNOSTICS", "SYSTEM_DIAGNOSTICS", "SYSTEM_STATUS", "DEVICE_TELEMETRY", "TELEMETRY", "JARVIS_STATUS", "PROTOCOL", "STEALTH_MODE", "WORK_MODE", "EMERGENCY_MODE"),
            description = "Queries system telemetry and triggers assistant operational protocols",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("protocol", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "NEWS_HEADLINES",
            aliases = setOf("NEWS", "GET_NEWS", "TOP_NEWS", "HEADLINES", "DAILY_NEWS", "TECH_NEWS", "WORLD_NEWS"),
            description = "Fetches top news headlines from RSS feeds",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "SOUND_DETECTOR",
            aliases = setOf("SOUND_MONITOR", "START_SOUND_DETECTION", "STOP_SOUND_DETECTION", "DETECT_SOUND", "LISTEN_SOUNDS"),
            description = "Monitors safety sounds (smoke alarm, crying, siren)",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true, allowedValues = setOf("start", "stop", "status"))
            )
        ))

        register(JciCapability(
            id = "VOICE_ACCESS",
            aliases = setOf("SHOW_NUMBERS", "HIDE_NUMBERS", "TAP_NUMBER", "VOICE_GRID"),
            description = "Displays numbered click grid over screen elements",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true, allowedValues = setOf("show", "hide", "tap"))
            )
        ))

        register(JciCapability(
            id = "SCREEN_READER",
            aliases = setOf("READ_SCREEN", "READ_PAGE"),
            description = "Extracts and reads aloud visible screen text",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "SPEAK",
            description = "Speaks text response aloud through TTS engine",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.SUBMISSION_ACK,
            parameters = listOf(
                JciParamDefinition("text", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "SYSTEM_FIX",
            aliases = setOf("CLEAN_MEMORY", "KILL_APPS", "OPTIMIZE_SYSTEM"),
            description = "Kills background memory hogs and frees RAM",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "AUDIO_PRESET",
            aliases = setOf("EQUALIZER", "EQ_PRESET", "BASS_BOOST", "AUDIO_EQ", "SOUND_PRESET"),
            description = "Configures equalizer audio preset",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "VOICE_MEMO",
            aliases = setOf("RECORD_MEMO", "RECORD_VOICE_MEMO", "LIST_MEMOS", "SEARCH_MEMOS", "SUMMARIZE_MEMO"),
            description = "Records, summarizes, and lists voice memos",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))

        register(JciCapability(
            id = "CALENDAR_MANAGE",
            aliases = setOf("CALENDAR", "SCHEDULE", "AGENDA", "MEETING", "ADD_EVENT", "CHECK_CONFLICT"),
            description = "Queries and creates calendar events",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.MEDIUM,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "AUTONOMOUS_CONTROL",
            aliases = setOf("AUTONOMOUS", "AUTO_PILOT", "DRIVING_MODE", "MEETING_MODE", "FOCUS_MODE", "SLEEP_MODE", "NIGHT_MODE", "WORKOUT_MODE"),
            description = "Enables or disables ambient autonomous background modes",
            classification = JciToolClassification.STATE_CHANGE,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.STATE_OBSERVED,
            parameters = listOf(
                JciParamDefinition("action", JciParamType.STRING, required = true)
            )
        ))

        register(JciCapability(
            id = "LOGS_READ",
            aliases = setOf("READ_LOGS", "SHOW_LOGS", "CHECK_LOGS", "SYSTEM_LOGS", "LOGCAT_READ", "DEVICE_LOGS", "GET_LOGS"),
            description = "Reads recent logcat system events and errors",
            classification = JciToolClassification.READ_ONLY,
            riskLevel = JciRiskLevel.LOW,
            expectedEvidenceLevel = JciEvidenceLevel.TOOL_RETURN
        ))
    }

    private fun registerAliases() {
        // Also import any additional aliases present in ToolRegistry
        for ((alias, canonical) in ToolRegistry.aliases) {
            val upperAlias = alias.uppercase()
            val upperTarget = canonical.uppercase()
            if (capabilities.containsKey(upperTarget)) {
                aliasMap[upperAlias] = upperTarget
            }
        }
    }
}
