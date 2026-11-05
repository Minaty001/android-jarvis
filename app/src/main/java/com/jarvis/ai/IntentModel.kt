package com.jarvis.ai

import com.jarvis.tools.AppRegistry
import java.util.Locale

enum class AssistantIntent {
    APP_OPEN,
    PLAY_MEDIA,
    STOP_MEDIA,
    SEARCH_WEB,
    REMEMBER_FACT,
    QUERY_MEMORY,
    SCREEN_VISION,
    EXPENSE_REPORT,
    BATTERY_CHECK,
    READ_NOTIFICATIONS,
    DAILY_BRIEFING,
    HABIT_SUGGEST,
    HABIT_LIST,
    ROUTINE_RUN,
    ROUTINE_LIST,
    MACRO_RUN,
    MACRO_LIST,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    MEDIA_RESUME,
    SPOTIFY_PLAY,
    TELEPHONY_CALL,
    TELEPHONY_SMS,
    CALENDAR_SCHEDULE,
    CALENDAR_AGENDA,
    SECURITY_AUDIT,
    FILE_SEARCH,
    AUTONOMOUS_CONTROL,
    RAG_QUERY,
    RAG_INDEX,
    CAMERA_VISION,
    WHATSAPP_READ,
    WHATSAPP_AUTO_REPLY,
    WHATSAPP_SEND,
    READ_LOGS,
    TELEPHONY_ANSWER,
    TELEPHONY_REJECT,
    TELEPHONY_CALLER_INFO,
    TELEPHONY_SMS_READ,
    SET_TIMER,
    SET_ALARM,
    REMIND_ME,
    WHERE_AM_I,
    READ_MESSAGES,
    CANCEL_TASK,
    COMPOUND_TASK,
    APPS_LIST,
    APPS_CLOSE_ALL,
    DEEP_RESEARCH,
    MEDIA_VOLUME,
    DEVICE_SETTINGS,
    SCREEN_LOCK,
    SCREEN_UNLOCK,
    SMART_HOME_CONTROL,
    UI_GLOBAL_ACTION,
    UI_SCROLL_ACTION,
    VOICE_ACCESS_OVERLAY,
    SCREEN_READER,
    AMBIENT_OPEN_MIC,
    DEFAULT_ASSISTANT_SETTINGS,
    HANDS_FREE_CAMERA,
    AIR_GESTURES,
    VOICE_TYPING,
    DRIVING_MODE,
    TELEPHONY_CONTROL,
    NOTIFICATION_INTERACTION,
    UI_GESTURE_ACTION,
    CLIPBOARD_CONTROL,
    EMERGENCY_SOS,
    SOUND_DETECTION
}

data class ResolvedIntent(
    val intent: AssistantIntent,
    val confidence: Float,
    val params: Map<String, String>,
    val directPlan: ExecutionPlan? = null,
    val reason: String? = null
)

class IntentModel(private val llmClient: LlmClient? = null) {
    private val reasoningCache = object : java.util.LinkedHashMap<String, ResolvedIntent>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolvedIntent>?): Boolean = size > 64
    }

    fun classify(utterance: String): ResolvedIntent? = IntentResolver.resolve(utterance)

    suspend fun classifyWithReasoning(
        utterance: String,
        llm: LlmClient? = llmClient
    ): ResolvedIntent? {
        val trimmed = utterance.trim()
        if (trimmed.isBlank()) return null
        val cacheKey = trimmed.lowercase(Locale.ROOT)
        synchronized(reasoningCache) {
            reasoningCache[cacheKey]?.let { return it }
        }

        // 1. Check deterministic fast-path
        val deterministic = classify(trimmed)
        if (deterministic != null) {
            val result = deterministic.copy(reason = deterministic.reason ?: "Deterministic rule match")
            synchronized(reasoningCache) {
                reasoningCache[cacheKey] = result
            }
            return result
        }

        // 2. Check privacy
        if (com.jarvis.foundation.SensitiveTerms.contains(trimmed)) {
            return null
        }

        // 3. Fallback to LLM reasoning if client available
        val client = llm ?: return null
        val llmResolved = classifyWithLlm(trimmed, client)
        if (llmResolved != null) {
            synchronized(reasoningCache) {
                reasoningCache[cacheKey] = llmResolved
            }
        }
        return llmResolved
    }

    private suspend fun classifyWithLlm(utterance: String, llm: LlmClient): ResolvedIntent? {
        val prompt = """
            You are the JARVIS Intent Classifier.
            Classify this user voice command into the most appropriate AssistantIntent.
            Available intents: ${AssistantIntent.values().joinToString(", ") { it.name }}

            Return strict JSON only:
            {
              "intent": "INTENT_NAME_OR_NULL",
              "confidence": 0.85,
              "params": {},
              "reason": "Brief explanation of reasoning"
            }
        """.trimIndent()

        val messages = listOf(
            Message("system", prompt),
            Message("user", utterance)
        )

        val raw = llm.chat(messages).getOrNull() ?: return null
        return parseLlmIntentResponse(raw)
    }

    fun parseLlmIntentResponse(rawResponse: String): ResolvedIntent? {
        val trimmed = rawResponse.trim()
        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null
        val jsonStr = trimmed.substring(jsonStart, jsonEnd + 1)

        return runCatching {
            val json = org.json.JSONObject(jsonStr)
            val intentStr = json.optString("intent", "").uppercase().trim()
            if (intentStr == "NULL" || intentStr.isBlank()) return null
            val intent = AssistantIntent.valueOf(intentStr)
            val confidence = json.optDouble("confidence", 0.8).toFloat()
            val reason = json.optString("reason", "LLM reasoning classification")
            val paramsMap = mutableMapOf<String, String>()
            val paramsObj = json.optJSONObject("params")
            if (paramsObj != null) {
                for (key in paramsObj.keys()) {
                    paramsMap[key] = paramsObj.optString(key, "")
                }
            }
            ResolvedIntent(
                intent = intent,
                confidence = confidence,
                params = paramsMap,
                directPlan = null,
                reason = reason
            )
        }.getOrNull()
    }
}

private val COMPOUND_SPLIT_REGEX = Regex("\\s+(?:and|then|aur|fir)\\s+")
private val NOTIF_READ_ITEM_REGEX = Regex("^(?:read|check|open)\\s+notification\\s+(\\d+)")
private val NOTIF_REPLY_REGEX = Regex("^(?:reply|respond)(?:\\s+to)?\\s+notification\\s+(\\d+)(?:\\s*(?:with|saying|:)?\\s*(.+))?$", RegexOption.IGNORE_CASE)
private val NOTIF_REPLY_HINGLISH_REGEX = Regex("^notification\\s+(\\d+)\\s+(?:ko\\s+)?(?:reply\\s+(?:karo|do)|jawab\\s+do)\\s*(?:ki|with)?\\s*(.+)?$", RegexOption.IGNORE_CASE)
private val NOTIF_ACTION_REGEX = Regex("^(?:trigger|press|click)\\s+notification\\s+(\\d+)(?:\\s+action\\s+(.+))?")
private val NOTIF_DISMISS_ITEM_REGEX = Regex("^(?:clear|dismiss|remove)\\s+notification\\s+(\\d+)")
private val NOTIF_DISMISS_ALL_REGEX = Regex("^(?:clear|dismiss|remove)\\s+(?:all\\s+notifications|notifications)")
private val GESTURE_LONG_PRESS_REGEX = Regex("^(?:long\\s+press|long\\s+click|hold)\\s+(.+)$")
private val GESTURE_SWIPE_REGEX = Regex("^(?:swipe|slide|scroll\\s+sideways)\\s+(left|right)$")
private val CLIPBOARD_COPY_REGEX = Regex("^(?:copy|copy to clipboard)\\s+(.+)$")
private val DEEP_APP_SEARCH_REGEX = Regex("^search\\s+(.+?)\\s+on\\s+(youtube|chrome|spotify)$")
private val SCREEN_TRANSLATE_REGEX = Regex("^(?:translate\\s+(?:this\\s+)?(?:screen|page)|screen\\s+translate)\\s*(?:to|in|into)?\\s*([a-zA-Z]+)?$", RegexOption.IGNORE_CASE)
private val SCREEN_TRANSLATE_HINGLISH_REGEX = Regex("^(?:(?:is|ye)\\s+)?screen\\s+(?:ko\\s+)?(?:([a-zA-Z]+)\\s+me\\s+)?(?:translate|anuvad)\\s*(?:karo|kar\\s+do)?$", RegexOption.IGNORE_CASE)
private val SCREEN_WHAT_SAYS_REGEX = Regex("^what\\s+does\\s+(?:this\\s+)?(?:screen|page)\\s+say\\s+(?:in|into)?\\s*([a-zA-Z]+)?$", RegexOption.IGNORE_CASE)
private val LENS_PRESCRIPTION_REGEX = Regex("^(?:read|check|scan)\\s+(?:the\\s+)?(?:prescription|medicine\\s+label|medicine|pill\\s+bottle)|^(?:dawa|medicine)\\s+(?:padho|check\\s+karo)$", RegexOption.IGNORE_CASE)
private val LENS_DOCUMENT_REGEX = Regex("^(?:read|scan)\\s+(?:this\\s+)?(?:document|page|letter|paper|book)$", RegexOption.IGNORE_CASE)
private val LENS_NUTRITION_REGEX = Regex("^(?:read|check|scan)\\s+(?:the\\s+)?(?:nutrition\\s+label|nutritional\\s+facts|calories|nutrition)$", RegexOption.IGNORE_CASE)
private val LENS_BARCODE_REGEX = Regex("^(?:scan|read)\\s+(?:the\\s+)?(?:barcode|qr\\s+code|product\\s+label|barcode\\s+label)$", RegexOption.IGNORE_CASE)
private val PAYMENT_CHECK_REGEX = Regex("^(?:did\\s+(?:the\\s+)?payment\\s+go\\s+through|is\\s+(?:the\\s+)?payment\\s+successful|check\\s+payment\\s+status|(?:kya\\s+)?payment\\s+(?:ho\\s+gaya|hua|ho\\s+chuka)(?:\\s+kya)?|payment\\s+check)$", RegexOption.IGNORE_CASE)
private val LINK_SAFETY_REGEX = Regex("^(?:is\\s+this\\s+link\\s+safe|is\\s+this\\s+page\\s+secure|check\\s+(?:link\\s+safety|if\\s+link\\s+is\\s+safe))$", RegexOption.IGNORE_CASE)
private val FIND_BUTTON_REGEX = Regex("^(?:find|where\\s+is)\\s+(?:the\\s+)?(.+?)\\s+button$", RegexOption.IGNORE_CASE)

val SOS_TRIGGER_REGEX = Regex("^(?:send\\s+sos|emergency\\s+sos|sos|help\\s+me|bachao(?:\\s+mujhe)?|madad\\s+karo|emergency|call\\s+112|police\\s+ko\\s+call\\s+karo|send\\s+emergency\\s+alert)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOS_CANCEL_REGEX = Regex("^(?:stop\\s+sos|cancel\\s+sos|sos\\s+band\\s+karo|stop\\s+emergency|cancel\\s+emergency|emergency\\s+band\\s+karo)[.!?,]*$", RegexOption.IGNORE_CASE)
val PIN_UNLOCK_REGEX = Regex("^(?:unlock\\s+(?:phone|screen|device)\\s+with\\s+pin\\s+(\\S+)|enter\\s+pin\\s+(\\S+)|pin\\s+dalo\\s+(\\S+)|my\\s+pin\\s+is\\s+(\\S+)|unlock\\s+with\\s+pin\\s+(\\S+))[.!?,]*$", RegexOption.IGNORE_CASE)
val WAKE_DISPLAY_REGEX = Regex("^(?:wake\\s+(?:up\\s+)?(?:screen|display|phone)|turn\\s+on\\s+(?:screen|display)|display\\s+on|screen\\s+on(?:\\s+karo)?|screen\\s+chalu\\s+karo)[.!?,]*$", RegexOption.IGNORE_CASE)
val LOCK_DISPLAY_REGEX = Regex("^(?:lock\\s+(?:screen|phone|device)|phone\\s+lock(?:\\s+karo)?|screen\\s+band\\s+karo|sleep\\s+(?:display|phone|screen)|turn\\s+off\\s+(?:screen|display)|sleep\\s+phone|phone\\s+band\\s+karo)[.!?,]*$", RegexOption.IGNORE_CASE)

val SOUND_STOP_REGEX = Regex("^(?:stop|turn off|disable|exit|close)\\s+(?:sound|ambient sound|audio)?\\s*(?:monitoring|detection|detector)[.!?,]*$|^(?:sound\\s+detector|sound\\s+detection)\\s+(?:band\\s+karo|off\\s+karo)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_STATUS_REGEX = Regex("^(?:sound\\s+detector|sound\\s+detection)\\s+(?:status|status\\s+batao)[.!?,]*$|^(?:is\\s+sound\\s+detector\\s+running|sound\\s+monitoring\\s+status)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_BABY_REGEX = Regex("^(?:listen\\s+for\\s+baby(?:\\s+crying)?|detect\\s+baby\\s+crying|monitor\\s+baby|baby\\s+crying(?:\\s+detect\\s+karo)?|bacha\\s+ro\\s+raha\\s+hai\\s+suno|bacha\\s+roye\\s+toh\\s+batana)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_SMOKE_REGEX = Regex("^(?:detect\\s+smoke\\s+alarm|listen\\s+for\\s+smoke\\s+alarm|smoke\\s+detector\\s+on|fire\\s+alarm\\s+alert|smoke\\s+alarm\\s+suno|alarm\\s+detection\\s+on\\s+karo|aag\\s+ka\\s+alarm\\s+suno|detect\\s+fire\\s+alarm)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_DOORBELL_REGEX = Regex("^(?:listen\\s+for\\s+doorbell|detect\\s+doorbell|doorbell\\s+suno|ghanti\\s+suno|detect\\s+chime|listen\\s+for\\s+chime)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_GLASS_REGEX = Regex("^(?:detect\\s+glass\\s+break(?:ing)?|listen\\s+for\\s+glass\\s+break(?:ing)?|sheesha\\s+tootna\\s+suno|glass\\s+break\\s+detection)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_HONK_REGEX = Regex("^(?:detect\\s+car\\s+honk|listen\\s+for\\s+car\\s+honk|car\\s+honk\\s+detection|detect\\s+(?:car\\s+)?horn|listen\\s+for\\s+(?:car\\s+)?horn)[.!?,]*$", RegexOption.IGNORE_CASE)
val SOUND_START_ALL_REGEX = Regex("^(?:start\\s+sound\\s+monitoring|enable\\s+sound\\s+detection|detect\\s+ambient\\s+sounds|listen\\s+for\\s+(?:ambient\\s+)?sounds|monitor\\s+sounds|sound\\s+detector\\s+chalu\\s+karo|ambient\\s+sounds\\s+suno|sound\\s+detection\\s+on\\s+karo|sound\\s+detector\\s+on)[.!?,]*$", RegexOption.IGNORE_CASE)

object IntentResolver {
    fun resolve(utterance: String): ResolvedIntent? {
        val text = utterance.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return null
        val parts = text.split(COMPOUND_SPLIT_REGEX)
        if (parts.size > 1) {
            // Keep explicit screen follow-ups together; leave other compound screen requests to the agent.
            val screenPart = parts.indexOfFirst { it.contains("screen") }
            if (screenPart >= 0 && parts.drop(screenPart + 1).none {
                    listOf("explain ", "summarize ", "describe ", "read ", "extract ").any(it::startsWith)
                }) return null
            val intents = parts.map { resolveSingle(it) }
            if (intents.all { it?.intent == AssistantIntent.SCREEN_VISION }) return resolveSingle(utterance)
            val actions = intents.flatMap { it?.directPlan?.actions.orEmpty() }
            if (actions.size >= 2 && intents.all { it?.directPlan != null }) {
                return ResolvedIntent(
                    intent = AssistantIntent.COMPOUND_TASK,
                    confidence = intents.minOf { it!!.confidence },
                    params = emptyMap(),
                    directPlan = ExecutionPlan("Completing both requested actions.", actions)
                )
            }
        }
        return resolveSingle(utterance)
    }

    fun resolveSingle(utterance: String): ResolvedIntent? {
        val text = utterance.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return null

        // 0.00 Conversational Greetings & Pleasantries Fast-Path (<5ms on-device response)
        if (Regex("^(?:hello|hi|hey|namaste|halo|good morning|good evening|good afternoon)(?:\\s+jarvis)?[.!?,]*$", RegexOption.IGNORE_CASE).matches(text)) {
            val response = "Hello sir! How can I assist you today?"
            val params = mapOf("text" to response)
            return ResolvedIntent(
                intent = AssistantIntent.DAILY_BRIEFING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan(response, listOf(ActionPlan("SPEAK", params)))
            )
        }

        if (Regex("^(?:how are you|kaise ho|kya haal hai|sab theek)(?:\\s+jarvis)?[.?]*$", RegexOption.IGNORE_CASE).matches(text)) {
            val response = "I am fully operational and ready to assist you, sir."
            val params = mapOf("text" to response)
            return ResolvedIntent(
                intent = AssistantIntent.DAILY_BRIEFING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan(response, listOf(ActionPlan("SPEAK", params)))
            )
        }

        if (Regex("^(?:who are you|what is your name|naam kya hai|kaun ho tum)(?:\\s+jarvis)?[.?]*$", RegexOption.IGNORE_CASE).matches(text)) {
            val response = "I am J.A.R.V.I.S., your personal AI assistant."
            val params = mapOf("text" to response)
            return ResolvedIntent(
                intent = AssistantIntent.DAILY_BRIEFING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan(response, listOf(ActionPlan("SPEAK", params)))
            )
        }

        if (Regex("^(?:thank you|thanks|dhanyawad|shukriya)(?:\\s+jarvis)?[.!]*$", RegexOption.IGNORE_CASE).matches(text)) {
            val response = "You're welcome, sir. Always at your service."
            val params = mapOf("text" to response)
            return ResolvedIntent(
                intent = AssistantIntent.DAILY_BRIEFING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan(response, listOf(ActionPlan("SPEAK", params)))
            )
        }

        // 0.0 Emergency SOS Triggers & Cancellations (Life-Critical Fast-Path)
        if (SOS_CANCEL_REGEX.matches(text)) {
            val params = mapOf("action" to "stop")
            return ResolvedIntent(
                intent = AssistantIntent.EMERGENCY_SOS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Stopping Emergency SOS beacon.", listOf(ActionPlan("EMERGENCY_SOS", params)))
            )
        }
        if (SOS_TRIGGER_REGEX.matches(text)) {
            val params = mapOf("action" to "start")
            return ResolvedIntent(
                intent = AssistantIntent.EMERGENCY_SOS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Activating Emergency SOS beacon.", listOf(ActionPlan("EMERGENCY_SOS", params)))
            )
        }

        // 0.01 Voice PIN Unlock
        val pinMatch = PIN_UNLOCK_REGEX.find(text)
        if (pinMatch != null) {
            val pin = pinMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: ""
            val params = mapOf("action" to "unlock", "pin" to pin)
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_UNLOCK,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Unlocking screen with PIN.", listOf(ActionPlan("KEYGUARD_UNLOCK", params)))
            )
        }

        // 0.02 Wake Display
        if (WAKE_DISPLAY_REGEX.matches(text)) {
            val params = mapOf("action" to "wake")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_UNLOCK,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Waking display screen.", listOf(ActionPlan("SCREEN_UNLOCK", params)))
            )
        }

        // 0.03 Lock Display
        if (LOCK_DISPLAY_REGEX.matches(text)) {
            val params = mapOf("action" to "lock")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_LOCK,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Locking device screen.", listOf(ActionPlan("SCREEN_LOCK", params)))
            )
        }

        // 0.04 Ambient Sound Detection & Safety Alerts Fast-Paths
        if (SOUND_STOP_REGEX.matches(text)) {
            val params = mapOf("action" to "stop")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Stopping ambient sound detection.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_STATUS_REGEX.matches(text)) {
            val params = mapOf("action" to "status")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Checking ambient sound detection status.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_BABY_REGEX.matches(text)) {
            val params = mapOf("action" to "start", "type" to "baby_crying")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting ambient sound monitoring for baby crying.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_SMOKE_REGEX.matches(text)) {
            val params = mapOf("action" to "start", "type" to "smoke_alarm")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting ambient sound monitoring for smoke alarm.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_DOORBELL_REGEX.matches(text)) {
            val params = mapOf("action" to "start", "type" to "doorbell")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting ambient sound monitoring for doorbell chime.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_GLASS_REGEX.matches(text)) {
            val params = mapOf("action" to "start", "type" to "glass_break")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting ambient sound monitoring for glass breaking.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_HONK_REGEX.matches(text)) {
            val params = mapOf("action" to "start", "type" to "car_honk")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting ambient sound monitoring for car honk.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        if (SOUND_START_ALL_REGEX.matches(text)) {
            val params = mapOf("action" to "start", "type" to "all")
            return ResolvedIntent(
                intent = AssistantIntent.SOUND_DETECTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting ambient sound monitoring for all safety categories.", listOf(ActionPlan("SOUND_DETECTOR", params)))
            )
        }
        
        // Smart Home: Lock/Unlock Door
        if (text.contains("door") && (text.contains("unlock") || text.contains("kholo") || text.contains("khol do"))) {
            val params = mapOf("domain" to "smart_home", "device" to "lock", "target" to "door", "action" to "unlock", "requiresConfirmation" to "true")
            return ResolvedIntent(intent = AssistantIntent.SMART_HOME_CONTROL, confidence = 0.95f, params = params, directPlan = ExecutionPlan("Unlocking door...", listOf(ActionPlan("SMART_HOME", params))))
        }
        if (text.contains("door") && (text.contains("lock") || text.contains("band karo") || text.contains("band kar do"))) {
            val params = mapOf("domain" to "smart_home", "device" to "lock", "target" to "door", "action" to "lock")
            return ResolvedIntent(intent = AssistantIntent.SMART_HOME_CONTROL, confidence = 0.95f, params = params, directPlan = ExecutionPlan("Locking door.", listOf(ActionPlan("SMART_HOME", params))))
        }
        
        // Smart Home: Lights (Specific Rooms)
        val roomMatch = Regex("(bedroom|living room|kitchen|hall|bathroom|main)\\s+light", RegexOption.IGNORE_CASE).find(text)
        if (roomMatch != null || text.contains("lights off") || text.contains("movie mode") || text.contains("study light")) {
            val off = text.contains("off") || text.contains("band")
            val brightnessMatch = Regex("(\\d{1,3})\\s*percent").find(text)
            val action = if (brightnessMatch != null) "set_brightness" else if (off) "turn_off" else "turn_on"
            val target = roomMatch?.groupValues?.get(1) ?: "all"
            val params = mutableMapOf("domain" to "smart_home", "device" to "light", "target" to target, "action" to action)
            if (brightnessMatch != null) params["value"] = brightnessMatch.groupValues[1]
            return ResolvedIntent(intent = AssistantIntent.SMART_HOME_CONTROL, confidence = 0.95f, params = params, directPlan = ExecutionPlan("Adjusting $target lights.", listOf(ActionPlan("SMART_HOME", params))))
        }

        // 0.1 Flashlight / Torch
        if (text in listOf("turn on flashlight", "torch on karo", "flashlight on", "torch on", "torch chalu karo", "light on karo", "turn on torch", "torch start karo", "light jalao", "light on") ||
            text in listOf("turn off flashlight", "torch band karo", "flashlight off", "torch off", "light band karo", "turn off torch", "torch stop karo", "light bujhao", "light off") ||
            (text.contains("torch") || text.contains("flashlight")) && (text.contains("on") || text.contains("off") || text.contains("chalu") || text.contains("band"))) {
            val off = text.contains("off") || text.contains("band") || text.contains("stop") || text.contains("bujhao")
            val mode = if (off) "off" else "on"
            val params = mapOf("mode" to mode)
            val desc = if (off) "Turning off flashlight." else "Turning on flashlight."
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan(desc, listOf(ActionPlan("FLASHLIGHT", params)))
            )
        }

        // 0.18 Display Brightness Scrubbing
        if (text in listOf("full brightness", "max brightness", "100% brightness", "full screen brightness", "brightness full")) {
            val params = mapOf("action" to "set_brightness", "brightness_percent" to "100")
            return ResolvedIntent(
                intent = AssistantIntent.DEVICE_SETTINGS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Setting screen brightness to 100%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        if (text in listOf("dim screen", "screen dim karo", "brightness low", "dim the screen", "low brightness")) {
            val params = mapOf("action" to "set_brightness", "brightness_percent" to "20")
            return ResolvedIntent(
                intent = AssistantIntent.DEVICE_SETTINGS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Dimming screen brightness to 20%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        val brightnessMatch = Regex("(?:set\\s+)?(?:screen\\s+)?brightness\\s+(?:to\\s+)?(\\d{1,3})%?|(?:set\\s+)?(\\d{1,3})%?\\s*brightness", RegexOption.IGNORE_CASE).find(text)
        if (brightnessMatch != null && !text.contains("smart home") && !text.contains("light")) {
            val pctStr = brightnessMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "50"
            val pct = pctStr.toIntOrNull()?.coerceIn(0, 100) ?: 50
            val params = mapOf("action" to "set_brightness", "brightness_percent" to pct.toString(), "percent" to pct.toString())
            return ResolvedIntent(
                intent = AssistantIntent.DEVICE_SETTINGS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Setting screen brightness to $pct%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        // 0.19 Stream-Specific Audio Volume Scrubbing
        val streamVolumeMatch = Regex("(?:set\\s+)?(alarm|ringtone|ring|notification|notif|media|music)\\s+volume\\s+(?:to\\s+)?(\\d{1,3})%?|(?:set\\s+)?(\\d{1,3})%?\\s+(alarm|ringtone|ring|notification|notif|media|music)\\s+volume", RegexOption.IGNORE_CASE).find(text)
        if (streamVolumeMatch != null) {
            val rawStream = if (streamVolumeMatch.groupValues[1].isNotBlank()) streamVolumeMatch.groupValues[1] else streamVolumeMatch.groupValues[4]
            val pctStr = if (streamVolumeMatch.groupValues[2].isNotBlank()) streamVolumeMatch.groupValues[2] else streamVolumeMatch.groupValues[3]
            val pct = pctStr.toIntOrNull()?.coerceIn(0, 100) ?: 50
            val streamType = when (rawStream.lowercase()) {
                "ring", "ringtone" -> "ring"
                "alarm" -> "alarm"
                "notification", "notif" -> "notification"
                else -> "music"
            }
            val params = mapOf("action" to "set_volume", "stream" to streamType, "volume_percent" to pct.toString(), "percent" to pct.toString())
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_VOLUME,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Setting $rawStream volume to $pct%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        // 0.2 Volume Up / Down / Set / Mute / Unmute
        val volumePercentMatch = Regex("(?:set\\s+)?volume\\s+(?:to\\s+)?(\\d{1,3})%?|(?:set\\s+)?(\\d{1,3})%?\\s*volume|(?:volume\\s+)?(\\d{1,3})\\s*(?:percent|%|karo)").find(text)
        if (volumePercentMatch != null && !text.contains("up") && !text.contains("down") && !text.contains("badhao") && !text.contains("kam")) {
            val pctStr = volumePercentMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "50"
            val pct = pctStr.toIntOrNull()?.coerceIn(0, 100) ?: 50
            val params = mapOf("action" to "set_volume", "volume_percent" to pct.toString(), "percent" to pct.toString())
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_VOLUME,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Setting volume to $pct%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        if (text in listOf("full volume", "max volume", "volume full", "volume 100", "volume 100%", "full volume karo", "volume full karo")) {
            val params = mapOf("action" to "set_volume", "volume_percent" to "100", "percent" to "100")
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_VOLUME,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Setting volume to 100%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        if (text in listOf("half volume", "volume half", "volume 50", "volume 50%", "half volume karo", "volume half karo")) {
            val params = mapOf("action" to "set_volume", "volume_percent" to "50", "percent" to "50")
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_VOLUME,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Setting volume to 50%.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        if (text in listOf("volume up", "increase volume", "raise volume", "volume badhao", "awaaz badhao", "awaz badhao", "volume tez karo", "awaaz tez karo", "louder", "sound up") ||
            text.startsWith("volume up") || text.startsWith("volume badhao") || text.startsWith("awaaz badhao")) {
            val params = mapOf("action" to "volume_up")
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_VOLUME,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Increasing volume.", listOf(ActionPlan("MEDIA_CONTROL", params)))
            )
        }

        if (text in listOf("volume down", "decrease volume", "lower volume", "volume kam karo", "awaaz kam karo", "awaz kam karo", "volume dheeme karo", "quieter", "sound down") ||
            text.startsWith("volume down") || text.startsWith("volume kam karo") || text.startsWith("awaaz kam karo")) {
            val params = mapOf("action" to "volume_down")
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_VOLUME,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Decreasing volume.", listOf(ActionPlan("MEDIA_CONTROL", params)))
            )
        }

        if (text in listOf("mute", "mute karo", "awaaz band karo", "awaz band karo", "silent karo", "silence") || text == "mute") {
            val params = mapOf("action" to "mute")
            return ResolvedIntent(
                intent = AssistantIntent.DEVICE_SETTINGS,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Muting audio.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        if (text in listOf("unmute", "unmute karo", "awaaz chalu karo", "awaz chalu karo") || text == "unmute") {
            val params = mapOf("action" to "unmute")
            return ResolvedIntent(
                intent = AssistantIntent.DEVICE_SETTINGS,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Unmuting audio.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        // 0.3 Screen Lock & Unlock
        if (text in listOf("lock screen", "lock phone", "phone lock karo", "screen lock karo", "screen band karo", "phone band karo", "lock device", "lock") ||
            text.startsWith("lock phone") || text.startsWith("phone lock")) {
            val params = mapOf("action" to "lock")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_LOCK,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Locking device screen.", listOf(ActionPlan("SCREEN_LOCK", params)))
            )
        }

        if (text in listOf("unlock screen", "unlock phone", "phone unlock karo", "screen unlock karo", "screen on karo", "screen chalu karo", "unlock device", "unlock") ||
            text.startsWith("unlock phone") || text.startsWith("phone unlock")) {
            val params = mapOf("action" to "unlock")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_UNLOCK,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Unlocking device screen.", listOf(ActionPlan("SCREEN_UNLOCK", params)))
            )
        }

        // 0.4 Close All Apps / Clear Recents
        if (text in listOf("close all apps", "close all", "clear recents", "clear all apps", "sab apps band karo", "apps close karo", "close apps", "band karo sab apps")) {
            val params = mapOf("action" to "close_all")
            return ResolvedIntent(
                intent = AssistantIntent.APPS_CLOSE_ALL,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Closing all open applications.", listOf(ActionPlan("APPS_CLOSE_ALL", params)))
            )
        }

        // 0.45 Hands-Free Global Navigation & System Gestures
        if (text in listOf("go back", "back", "piche", "piche jao", "back jao") || text == "back") {
            val params = mapOf("action" to "back")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Going back.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("go home", "home", "home screen", "ghar", "ghar jao") || text == "home") {
            val params = mapOf("action" to "home")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Going to home screen.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("recent apps", "recent app", "recents", "overview", "switch apps", "open recents")) {
            val params = mapOf("action" to "recents")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Opening recent apps.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("notifications", "open notifications", "pull down notifications", "notification shade", "show notifications")) {
            val params = mapOf("action" to "notifications")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Opening notifications shade.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("quick settings", "open quick settings", "settings shade", "show quick settings")) {
            val params = mapOf("action" to "quick_settings")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Opening quick settings.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("take screenshot", "take a screenshot", "screenshot", "capture screen", "screenshot lo", "screen shot")) {
            val params = mapOf("action" to "screenshot")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Capturing screenshot.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("split screen", "toggle split screen", "open split screen", "start split screen", "side by side", "side by side mode", "close split screen", "exit split screen")) {
            val params = mapOf("action" to "split_screen")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Toggling split screen mode.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        if (text in listOf("open app drawer", "show app drawer", "app drawer", "all apps", "show all apps", "open apps")) {
            val params = mapOf("action" to "app_drawer")
            return ResolvedIntent(
                intent = AssistantIntent.UI_GLOBAL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Opening app drawer.", listOf(ActionPlan("UI_GLOBAL", params)))
            )
        }

        // 0.46 Hands-Free In-App Scrolling
        if (text in listOf("scroll down", "niche scroll karo", "scroll down karo", "niche scroll", "page down")) {
            val params = mapOf("direction" to "down")
            return ResolvedIntent(
                intent = AssistantIntent.UI_SCROLL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Scrolling down.", listOf(ActionPlan("UI_SCROLL", params)))
            )
        }

        if (text in listOf("scroll up", "uper scroll karo", "scroll up karo", "upar scroll", "page up")) {
            val params = mapOf("direction" to "up")
            return ResolvedIntent(
                intent = AssistantIntent.UI_SCROLL_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Scrolling up.", listOf(ActionPlan("UI_SCROLL", params)))
            )
        }

        // Phase 4: Touchless Long-Press & Directional Swipes
        val swipeDir = when {
            text == "swipe left" || text == "next tab" || text == "slide left" -> "left"
            text == "swipe right" || text == "previous tab" || text == "slide right" -> "right"
            else -> GESTURE_SWIPE_REGEX.find(text)?.groupValues?.get(1)
        }
        if (swipeDir != null) {
            val params = mapOf("action" to "swipe", "direction" to swipeDir)
            return ResolvedIntent(
                intent = AssistantIntent.UI_GESTURE_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Swiping $swipeDir.", listOf(ActionPlan("UI_GESTURE", params)))
            )
        }

        val longPressMatch = GESTURE_LONG_PRESS_REGEX.find(text)
        if (longPressMatch != null) {
            val target = longPressMatch.groupValues[1].trim()
            val params = mapOf("action" to "long_click", "target" to target)
            return ResolvedIntent(
                intent = AssistantIntent.UI_GESTURE_ACTION,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Long pressing '$target'.", listOf(ActionPlan("UI_GESTURE", params)))
            )
        }

        // Phase 4: Hands-Free Voice Clipboard & OTP Injection
        if (text in listOf("read clipboard", "check clipboard", "what is in clipboard", "clipboard content", "clipboard check", "clipboard padho")) {
            val params = mapOf("action" to "read")
            return ResolvedIntent(
                intent = AssistantIntent.CLIPBOARD_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Reading clipboard contents.", listOf(ActionPlan("CLIPBOARD_MANAGE", params)))
            )
        }

        if (text in listOf("paste clipboard", "paste text", "paste here", "paste karo", "paste")) {
            val params = mapOf("action" to "paste")
            return ResolvedIntent(
                intent = AssistantIntent.CLIPBOARD_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Pasting clipboard text into active field.", listOf(ActionPlan("CLIPBOARD_MANAGE", params)))
            )
        }

        if (text in listOf("paste otp", "enter otp", "inject otp", "type otp", "fill otp", "otp dalo", "otp paste karo")) {
            val params = mapOf("action" to "paste_otp")
            return ResolvedIntent(
                intent = AssistantIntent.CLIPBOARD_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Injecting SMS OTP code.", listOf(ActionPlan("CLIPBOARD_MANAGE", params)))
            )
        }

        val copyMatch = CLIPBOARD_COPY_REGEX.find(text)
        if (copyMatch != null) {
            val textToCopy = copyMatch.groupValues[1].trim()
            val params = mapOf("action" to "copy", "text" to textToCopy)
            return ResolvedIntent(
                intent = AssistantIntent.CLIPBOARD_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Copying text to clipboard.", listOf(ActionPlan("CLIPBOARD_MANAGE", params)))
            )
        }

        // 0.47 Voice Access Numbered Overlay
        if (text in listOf("show numbers", "show grid", "numbers dikhao", "numbers on", "show badges", "display numbers")) {
            val params = mapOf("action" to "show")
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_ACCESS_OVERLAY,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Displaying numbered badges.", listOf(ActionPlan("VOICE_ACCESS", params)))
            )
        }

        if (text in listOf("hide numbers", "hide grid", "numbers band karo", "numbers off", "hide badges")) {
            val params = mapOf("action" to "hide")
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_ACCESS_OVERLAY,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Hiding numbered badges.", listOf(ActionPlan("VOICE_ACCESS", params)))
            )
        }

        val tapNumberMatch = Regex("^(?:tap|click|select|choose|number|no|dabao)\\s+(\\d{1,3})$").find(text)
        if (tapNumberMatch != null) {
            val num = tapNumberMatch.groupValues[1]
            val params = mapOf("action" to "click", "number" to num)
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_ACCESS_OVERLAY,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Tapping item $num.", listOf(ActionPlan("VOICE_ACCESS", params)))
            )
        }

        // 0.48 Screen Reader & Narration
        if (text in listOf("read screen", "read this screen", "read page", "read this page", "read article", "screen padho", "read content")) {
            val params = mapOf("action" to "read")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_READER,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Reading on-screen content aloud.", listOf(ActionPlan("SCREEN_READER", params)))
            )
        }

        // 0.49 Continuous Ambient Open-Mic Mode
        if (text in listOf("open mic", "turn on open mic", "start open mic", "enable open mic", "walkie talkie mode", "continuous listening", "continuous mode", "open mic on")) {
            val params = mapOf("action" to "start")
            return ResolvedIntent(
                intent = AssistantIntent.AMBIENT_OPEN_MIC,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Enabling continuous open-mic mode.", listOf(ActionPlan("AMBIENT_OPEN_MIC", params)))
            )
        }

        if (text in listOf("stop open mic", "turn off open mic", "disable open mic", "exit open mic", "close open mic", "open mic off", "jarvis sleep", "go to sleep", "sleep")) {
            val params = mapOf("action" to "stop")
            return ResolvedIntent(
                intent = AssistantIntent.AMBIENT_OPEN_MIC,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Disabling open-mic mode and returning to standby.", listOf(ActionPlan("AMBIENT_OPEN_MIC", params)))
            )
        }

        // 0.494 Dedicated In-Car Driving Mode & Ambient HUD
        if (text in listOf("start driving mode", "enable driving mode", "driving mode on", "i am driving", "car mode", "turn on driving mode", "drive mode on")) {
            val params = mapOf("action" to "start")
            return ResolvedIntent(
                intent = AssistantIntent.DRIVING_MODE,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Enabling in-car driving mode.", listOf(ActionPlan("DRIVING_MODE", params)))
            )
        }

        if (text in listOf("stop driving mode", "disable driving mode", "driving mode off", "i have arrived", "exit car mode", "turn off driving mode", "drive mode off", "exit driving mode")) {
            val params = mapOf("action" to "stop")
            return ResolvedIntent(
                intent = AssistantIntent.DRIVING_MODE,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Disabling driving mode.", listOf(ActionPlan("DRIVING_MODE", params)))
            )
        }

        if (text in listOf("driving mode status", "is driving mode on", "check driving mode", "car mode status")) {
            val params = mapOf("action" to "status")
            return ResolvedIntent(
                intent = AssistantIntent.DRIVING_MODE,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Checking driving mode status.", listOf(ActionPlan("DRIVING_MODE", params)))
            )
        }

        // 0.495 Default Voice Assistant Settings & Status
        if (text in listOf("set jarvis as default assistant", "make jarvis default assistant", "open assistant settings", "default assistant settings", "change default assistant", "assistant settings")) {
            val params = mapOf("action" to "open")
            return ResolvedIntent(
                intent = AssistantIntent.DEFAULT_ASSISTANT_SETTINGS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Opening Default Assistant settings.", listOf(ActionPlan("DEFAULT_ASSISTANT", params)))
            )
        }

        if (text in listOf("default assistant status", "is jarvis default assistant", "check default assistant", "assistant status")) {
            val params = mapOf("action" to "check")
            return ResolvedIntent(
                intent = AssistantIntent.DEFAULT_ASSISTANT_SETTINGS,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Checking Default Assistant status.", listOf(ActionPlan("DEFAULT_ASSISTANT", params)))
            )
        }

        // 0.496 Hands-Free Camera Voice Triggers & Photo Capture
        if (text in listOf("take a picture", "take a photo", "take photo", "capture photo", "click a picture", "click photo", "photo khicho", "picture khicho")) {
            val params = mapOf("action" to "take_photo", "facing" to "back")
            return ResolvedIntent(
                intent = AssistantIntent.HANDS_FREE_CAMERA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Taking a picture.", listOf(ActionPlan("HANDS_FREE_CAMERA", params)))
            )
        }

        if (text in listOf("take a selfie", "take selfie", "click selfie", "click a selfie", "selfie lo", "selfie khicho", "selfie")) {
            val params = mapOf("action" to "take_photo", "facing" to "front")
            return ResolvedIntent(
                intent = AssistantIntent.HANDS_FREE_CAMERA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Taking a selfie.", listOf(ActionPlan("HANDS_FREE_CAMERA", params)))
            )
        }

        if (text in listOf("open camera", "launch camera", "start camera", "camera kholo", "open the camera")) {
            val params = mapOf("action" to "launch_camera", "mode" to "still")
            return ResolvedIntent(
                intent = AssistantIntent.HANDS_FREE_CAMERA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Opening camera.", listOf(ActionPlan("HANDS_FREE_CAMERA", params)))
            )
        }

        if (text in listOf("record video", "start recording video", "record a video", "take a video", "start recording", "video record karo", "video shoot karo")) {
            val params = mapOf("action" to "launch_camera", "mode" to "video")
            return ResolvedIntent(
                intent = AssistantIntent.HANDS_FREE_CAMERA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Starting video recording.", listOf(ActionPlan("HANDS_FREE_CAMERA", params)))
            )
        }

        if (text in listOf("press shutter", "click shutter", "capture shutter", "camera shutter", "shutter click karo")) {
            val params = mapOf("action" to "shutter")
            return ResolvedIntent(
                intent = AssistantIntent.HANDS_FREE_CAMERA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Clicking camera shutter.", listOf(ActionPlan("HANDS_FREE_CAMERA", params)))
            )
        }

        // 0.497 Touchless Air Gestures
        if (text in listOf("turn on air gestures", "enable air gestures", "touchless control on", "air gestures on", "touchless mode on", "air gesture on")) {
            val params = mapOf("action" to "enable")
            return ResolvedIntent(
                intent = AssistantIntent.AIR_GESTURES,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Enabling air gestures.", listOf(ActionPlan("AIR_GESTURES", params)))
            )
        }

        if (text in listOf("turn off air gestures", "disable air gestures", "touchless control off", "air gestures off", "touchless mode off", "air gesture off")) {
            val params = mapOf("action" to "disable")
            return ResolvedIntent(
                intent = AssistantIntent.AIR_GESTURES,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Disabling air gestures.", listOf(ActionPlan("AIR_GESTURES", params)))
            )
        }

        if (text in listOf("air gestures status", "touchless status", "air gesture status", "check air gestures")) {
            val params = mapOf("action" to "status")
            return ResolvedIntent(
                intent = AssistantIntent.AIR_GESTURES,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Checking air gestures status.", listOf(ActionPlan("AIR_GESTURES", params)))
            )
        }

        // 0.498 Universal Voice Typing & Field Dictation
        val typeMatch = Regex("^(?:type|write|dictate|likho)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)
        if (typeMatch != null) {
            val content = typeMatch.groupValues[1].trim()
            val params = mapOf("action" to "type", "text" to content)
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_TYPING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Typing: \"$content\"", listOf(ActionPlan("VOICE_TYPING", params)))
            )
        }

        if (text in listOf("backspace", "press backspace", "delete word", "delete letter", "one backspace", "piche karo")) {
            val params = mapOf("action" to "backspace")
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_TYPING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Backspacing in input field.", listOf(ActionPlan("VOICE_TYPING", params)))
            )
        }

        if (text in listOf("clear text", "clear input", "clear field", "delete text", "clear all text", "sab saaf karo")) {
            val params = mapOf("action" to "clear")
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_TYPING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Clearing input field.", listOf(ActionPlan("VOICE_TYPING", params)))
            )
        }

        if (text in listOf("press send", "press enter", "hit send", "hit enter", "send input", "submit field", "bhejo")) {
            val params = mapOf("action" to "send")
            return ResolvedIntent(
                intent = AssistantIntent.VOICE_TYPING,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Submitting input.", listOf(ActionPlan("VOICE_TYPING", params)))
            )
        }

        // 0.499 Hands-Free Speakerphone & In-Call Voice Management
        val callOnSpeakerMatch = Regex("^(?:call|dial)\\s+(.+?)\\s+on\\s+speaker$", RegexOption.IGNORE_CASE).find(text)
        if (callOnSpeakerMatch != null) {
            val target = callOnSpeakerMatch.groupValues[1].trim()
            val params = mapOf("action" to "call", "recipient" to target, "speaker" to "true")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Calling $target on speaker.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        if (text in listOf("put on speaker", "turn on speaker", "speaker on", "enable speaker", "turn on speakerphone", "speakerphone on", "loudspeaker", "speaker par dalo")) {
            val params = mapOf("action" to "speaker_on")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Enabling speakerphone.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        if (text in listOf("turn off speaker", "speaker off", "disable speaker", "turn off speakerphone", "speakerphone off")) {
            val params = mapOf("action" to "speaker_off")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Disabling speakerphone.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        if (text in listOf("mute mic", "mute microphone", "mute my mic", "mic mute", "mic mute karo")) {
            val params = mapOf("action" to "mute_mic")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Muting microphone.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        if (text in listOf("unmute mic", "unmute microphone", "unmute my mic", "mic unmute", "mic unmute karo", "turn on mic")) {
            val params = mapOf("action" to "unmute_mic")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Unmuting microphone.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        if (text in listOf("hang up", "end call", "cut call", "disconnect call", "hang up the call", "phone kato", "call kato")) {
            val params = mapOf("action" to "end_call")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CONTROL,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Ending active call.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        // 0.5 WiFi & Bluetooth Toggles
        if (text in listOf("turn on wifi", "wifi on karo", "wifi chalu karo", "turn off wifi", "wifi off karo", "wifi band karo")) {
            val off = text.contains("off") || text.contains("band")
            val params = mapOf("action" to "set_wifi", "state" to if (off) "off" else "on")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan(if (off) "Turning off Wi-Fi." else "Turning on Wi-Fi.", listOf(ActionPlan("SYSTEM_SWITCHBOARD", params)))
            )
        }

        if (text in listOf("turn on bluetooth", "bluetooth on karo", "bluetooth chalu karo", "turn off bluetooth", "bluetooth off karo", "bluetooth band karo")) {
            val off = text.contains("off") || text.contains("band")
            val params = mapOf("action" to "set_bluetooth", "state" to if (off) "off" else "on")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan(if (off) "Turning off Bluetooth." else "Turning on Bluetooth.", listOf(ActionPlan("SYSTEM_SWITCHBOARD", params)))
            )
        }

        if (text in listOf("turn on do not disturb", "turn off do not disturb", "dnd on", "dnd off", "do not disturb on", "do not disturb off", "enable dnd", "disable dnd")) {
            val off = text.contains("off") || text.contains("disable")
            val params = mapOf("action" to "dnd", "target" to if (off) "off" else "on")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan(if (off) "Turning off Do Not Disturb." else "Turning on Do Not Disturb.", listOf(ActionPlan("SYSTEM_SWITCHBOARD", params)))
            )
        }

        if (text in listOf("turn on auto rotate", "turn off auto rotate", "auto rotate on", "auto rotate off", "enable auto rotate", "disable auto rotate", "lock screen rotation", "unlock screen rotation", "auto rotate")) {
            val off = text.contains("off") || text.contains("disable") || text.contains("lock screen rotation")
            val params = mapOf("action" to "auto_rotate", "target" to if (off) "off" else "on")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan(if (off) "Disabling screen auto-rotation." else "Enabling screen auto-rotation.", listOf(ActionPlan("SYSTEM_SWITCHBOARD", params)))
            )
        }

        // 1. Media stop / pause
        if (text in listOf("stop", "pause", "band karo", "gaana band karo", "stop music", "pause music", "stop video")) {
            return ResolvedIntent(
                intent = AssistantIntent.STOP_MEDIA,
                confidence = 0.98f,
                params = emptyMap(),
                directPlan = ExecutionPlan("Stopping playback.", listOf(ActionPlan("MEDIA_STOP", emptyMap())))
            )
        }

        // 1.1 Cancel current task ("cancel", "cancel task", "task cancel karo", "ruk jao")
        if (text in listOf("cancel", "cancel task", "cancel that", "task cancel karo", "cancel karo", "ruk jao", "stop task", "task band karo", "cancel everything")) {
            return ResolvedIntent(
                intent = AssistantIntent.CANCEL_TASK,
                confidence = 0.98f,
                params = emptyMap()
            )
        }

        // 1.2 Scan / List Installed Apps ("scan all apps", "list installed apps", "show apps")
        if (text in listOf(
            "scan apps", "scan all apps", "scan installed apps", "scan apps on device",
            "list apps", "list all apps", "list installed apps", "installed apps", "installed applications",
            "show all apps", "show apps", "koun koun se apps hai", "kon se app installed hai",
            "apps list", "all apps", "show installed apps"
        ) || text.startsWith("scan apps") || text.startsWith("scan all apps") || text.startsWith("list installed apps") || text.startsWith("scan installed apps")) {
            val action = if (text.contains("scan")) "scan" else "list"
            return ResolvedIntent(
                intent = AssistantIntent.APPS_LIST,
                confidence = 0.98f,
                params = mapOf("action" to action),
                directPlan = ExecutionPlan("Scanning installed applications.", listOf(ActionPlan("APPS_LIST", mapOf("action" to action))))
            )
        }

        // 1.3 Deep App Macro Shortcuts ("open storage care", "device storage", "free up space", "open whatsapp status", "whatsapp status")
        if (text in listOf("free up space", "open storage manager", "open storage care", "device storage", "storage saaf karo", "device care", "storage care") ||
            text.contains("storage care") || text.contains("device storage")) {
            val params = mapOf("action" to "run", "macro_id" to "macro_device_storage")
            return ResolvedIntent(
                intent = AssistantIntent.MACRO_RUN,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Opening Device Storage Care.", listOf(ActionPlan("MACRO_WORKFLOW", params)))
            )
        }

        if (text in listOf("open whatsapp status", "whatsapp status", "whatsapp updates", "show whatsapp status", "status dikhao") ||
            (text.contains("whatsapp") && (text.contains("status") || text.contains("updates")))) {
            val params = mapOf("action" to "run", "macro_id" to "macro_whatsapp_status")
            return ResolvedIntent(
                intent = AssistantIntent.MACRO_RUN,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Opening WhatsApp Status / Updates.", listOf(ActionPlan("MACRO_WORKFLOW", params)))
            )
        }

        // WebApp / Website creation check ("mere liye website banao", "make a website for me where...", "create webapp")
        val isCreationQuery = text.contains("banao") || text.contains("bana") || text.contains("website") ||
                text.contains("webapp") || text.contains("web app") || text.contains("create") ||
                text.contains("build") || text.contains("make") || text.contains("code") || text.contains("develop") || text.contains("design")

        // Phase 4: Deep App Search & Intent Shortcuts
        val deepSearchMatch = DEEP_APP_SEARCH_REGEX.find(text)
        if (deepSearchMatch != null) {
            val query = deepSearchMatch.groupValues[1].trim()
            val app = deepSearchMatch.groupValues[2].trim()
            val params = mapOf("app" to app, "query" to query, "action" to "search_in_app")
            return ResolvedIntent(
                intent = AssistantIntent.APP_OPEN,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Searching '$query' on $app.", listOf(ActionPlan("OPEN_APP", params)))
            )
        }

        // 2. Open App ("open youtube", "youtube kholo", "launch whatsapp", "open settings")
        val openAppPatterns = listOf(
            Regex("^(?:open|launch|kholo|chalu karo|start)\\s+([a-z0-9_\\s]+)$"),
            Regex("^([a-z0-9_\\s]+)\\s+(?:kholo|launch karo|chalu karo|open karo)$")
        )
        val actionKeywords = setOf(
            "check", "search", "play", "bajao", "chalao", "send", "bhejo", "call",
            "message", "messages", "massages", "unread", "read", "post", "scroll",
            "mode", "routine", "automation", "autopilot", "timer", "alarm"
        )
        if (!isCreationQuery) {
            for (pattern in openAppPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    val appRaw = match.groupValues[1].trim()
                    val words = appRaw.split(Regex("\\s+"))
                    val registeredApp = AppRegistry.ALLOWED_PACKAGES.containsKey(appRaw)
                    if (words.size > 3 || (words.any { it in actionKeywords } && !registeredApp)) {
                        continue
                    }
                    val targetApp = normalizeAppName(appRaw)
                    if (targetApp.isNotBlank()) {
                        return ResolvedIntent(
                            intent = AssistantIntent.APP_OPEN,
                            confidence = 0.95f,
                            params = mapOf("app" to targetApp),
                            directPlan = ExecutionPlan("Opening $targetApp.", listOf(ActionPlan("OPEN_APP", mapOf("app" to targetApp))))
                        )
                    }
                }
            }
        }

        // 2.05 Media Scrubbing (Seek Forward / Rewind)
        val forwardSecsMatch = Regex("^(?:skip|forward|fast forward)\\s+(\\d+)\\s*(?:seconds?|secs?|s)$").find(text)
        val forwardMinsMatch = Regex("^(?:skip|forward|fast forward)\\s+(\\d+)\\s*(?:minutes?|mins?|m)$").find(text)
        if (forwardSecsMatch != null || forwardMinsMatch != null) {
            val secs = if (forwardMinsMatch != null) {
                ((forwardMinsMatch.groupValues[1].toIntOrNull() ?: 1) * 60).toString()
            } else {
                forwardSecsMatch!!.groupValues[1]
            }
            val params = mapOf("action" to "seek_forward", "seconds" to secs)
            return ResolvedIntent(
                intent = AssistantIntent.STOP_MEDIA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Skipping forward by $secs seconds.", listOf(ActionPlan("MEDIA_CONTROL", params)))
            )
        }

        val rewindSecsMatch = Regex("^(?:rewind|go back|replay)\\s+(\\d+)\\s*(?:seconds?|secs?|s)$").find(text)
        val rewindMinsMatch = Regex("^(?:rewind|go back|replay)\\s+(\\d+)\\s*(?:minutes?|mins?|m)$").find(text)
        if (rewindSecsMatch != null || rewindMinsMatch != null) {
            val secs = if (rewindMinsMatch != null) {
                ((rewindMinsMatch.groupValues[1].toIntOrNull() ?: 1) * 60).toString()
            } else {
                rewindSecsMatch!!.groupValues[1]
            }
            val params = mapOf("action" to "seek_backward", "seconds" to secs)
            return ResolvedIntent(
                intent = AssistantIntent.STOP_MEDIA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Rewinding by $secs seconds.", listOf(ActionPlan("MEDIA_CONTROL", params)))
            )
        }

        // 2.06 Media Playback Speed
        val speedMatch = Regex("^(?:set\\s+)?(?:playback\\s+)?speed\\s+(?:to\\s+)?([0-9.]+)(?:x)?$").find(text)
            ?: Regex("^play\\s+at\\s+([0-9.]+)(?:x)?$").find(text)
        if (speedMatch != null || text == "normal speed") {
            val rawSpeed = speedMatch?.groupValues?.get(1) ?: "1.0"
            val formattedSpeed = if (rawSpeed == "2") "2.0" else rawSpeed
            val params = mapOf("action" to "set_speed", "speed" to formattedSpeed)
            return ResolvedIntent(
                intent = AssistantIntent.STOP_MEDIA,
                confidence = 0.99f,
                params = params,
                directPlan = ExecutionPlan("Setting playback speed to ${formattedSpeed}x.", listOf(ActionPlan("MEDIA_CONTROL", params)))
            )
        }

        // 2.1 Media Next Track ("next song", "skip song", "agla gaana", "next track", "skip")
        if (text in listOf("next", "next song", "next track", "skip", "skip song", "agla gaana", "agla song", "change song")) {
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_NEXT,
                confidence = 0.98f,
                params = mapOf("action" to "next"),
                directPlan = ExecutionPlan("Skipping to next track.", listOf(ActionPlan("MEDIA_CONTROL", mapOf("action" to "next"))))
            )
        }

        // 2.2 Media Previous Track ("previous song", "prev song", "pichla gaana", "previous track")
        if (text in listOf("previous", "previous song", "previous track", "prev track", "prev song", "pichla gaana", "pichhla gaana", "pichla song", "last song")) {
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_PREVIOUS,
                confidence = 0.98f,
                params = mapOf("action" to "previous"),
                directPlan = ExecutionPlan("Returning to previous track.", listOf(ActionPlan("MEDIA_CONTROL", mapOf("action" to "previous"))))
            )
        }

        // 2.3 Media Resume / Play ("resume", "resume music", "continue music", "gaana chalao", "wapas chalao")
        if (text in listOf("resume", "resume music", "continue music", "continue", "play music", "gaana wapas chalao", "wapas bajao")) {
            return ResolvedIntent(
                intent = AssistantIntent.MEDIA_RESUME,
                confidence = 0.98f,
                params = mapOf("action" to "play"),
                directPlan = ExecutionPlan("Resuming media playback.", listOf(ActionPlan("MEDIA_CONTROL", mapOf("action" to "play"))))
            )
        }

        // 2.4 Spotify Play ("play ... on spotify", "spotify pe ... chalao")
        if (text.contains("spotify")) {
            val query = text.replace(Regex("\\b(play|spotify|on|pe|chalao|bajao)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            val params = mapOf("query" to query)
            return ResolvedIntent(
                intent = AssistantIntent.SPOTIFY_PLAY,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Playing on Spotify: $query", listOf(ActionPlan("SPOTIFY_PLAY", params)))
            )
        }

        // 2.5 Lyrics ("lyrics of ...", "show lyrics for ...", "is gaane ke lyrics")
        if (text.contains("lyrics") || text.contains("bol")) {
            val query = text.replace(Regex("\\b(show|lyrics|for|of|gaane ke|bol|batao|dikhao|what are the)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            val params = mapOf("query" to "$query lyrics")
            return ResolvedIntent(
                intent = AssistantIntent.SEARCH_WEB,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Searching web for lyrics: $query", listOf(ActionPlan("SEARCH_WEB", params)))
            )
        }

        // 3. Play music / YouTube ("play believer", "youtube pe chalao kesariya", "open youtube and directly play song", "kesariya gaana bajao")
        if (!isCreationQuery) {
            val playPatterns = listOf(
                Regex("^(?:open\\s+youtube\\s+(?:and|aur)?\\s*(?:directly)?\\s*(?:play|chalao|bajao)|launch\\s+youtube\\s+and\\s+play)\\s*(.*)$"),
                Regex("^(?:directly\\s+)?(?:play|chalao|bajao)\\s+(.+?)(?:\\s+(?:on|in|pe|par)\\s+youtube)?$"),
                Regex("^youtube\\s+(?:pe|par)?\\s*(?:play|chalao|bajao)\\s*(.+)$"),
                Regex("^youtube\\s+(?:pe|par)\\s+(.+?)\\s+(?:play|chalao|bajao|play karo|chala do)$"),
                Regex("^(.+)\\s+(?:song|gaana|music)\\s+(?:play|chalao|bajao|chala do)$"),
                Regex("^(?:directly\\s+)?play\\s*(?:a\\s+)?song$")
            )
            for (pattern in playPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    var query = if (match.groupValues.size > 1) match.groupValues[1].trim() else ""
                    query = query.replace(Regex("\\b(directly|on youtube|in youtube|pe|par|chalao|bajao|play)\\b", RegexOption.IGNORE_CASE), "").trim()
                    if (query.isBlank() || query.equals("song", ignoreCase = true) || query.equals("gaana", ignoreCase = true) || query.equals("music", ignoreCase = true)) {
                        query = "trending songs"
                    }
                    return ResolvedIntent(
                        intent = AssistantIntent.PLAY_MEDIA,
                        confidence = 0.94f,
                        params = mapOf("query" to query),
                        directPlan = ExecutionPlan("Playing $query.", listOf(ActionPlan("YOUTUBE_PLAY", mapOf("query" to query))))
                    )
                }
            }
        }

        // 3.9 Calculator & Math ("calculate 45 times 8 plus 120", "what is 25 * 4", "compute 100 / 5")
        if (text.startsWith("calculate ") || text.startsWith("compute ") || text.startsWith("solve ") ||
            (text.startsWith("what is ") && text.any { it.isDigit() } &&
                (text.contains("+") || text.contains("-") || text.contains("*") || text.contains("/") ||
                 text.contains("times") || text.contains("plus") || text.contains("minus") || text.contains("divided")))) {
            val rawExpr = if (text.startsWith("what is ", ignoreCase = true)) {
                text.substringAfter("what is ").trim()
            } else {
                text.substringAfter(" ").trim()
            }
            val expr = rawExpr
                .replace(Regex("(?i)\\bmultiplied by\\b"), "*")
                .replace(Regex("(?i)\\bdivided by\\b"), "/")
                .replace(Regex("(?i)\\btimes\\b"), "*")
                .replace(Regex("(?i)\\binto\\b"), "*")
                .replace(Regex("(?i)\\bplus\\b"), "+")
                .replace(Regex("(?i)\\bminus\\b"), "-")
            return ResolvedIntent(
                intent = AssistantIntent.SEARCH_WEB,
                confidence = 0.95f,
                params = mapOf("expression" to expr),
                directPlan = ExecutionPlan("Calculating math expression.", listOf(ActionPlan("CALCULATE", mapOf("expression" to expr))))
            )
        }

        // 4. Memory store / Note ("remember that my key is X", "yaad rakhna mera favourite X hai")
        val rememberPatterns = listOf(
            Regex("^(?:remember|note down|yaad rakhna|note karo)\\s+(?:that\\s+)?(.+)$")
        )
        for (pattern in rememberPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val fact = match.groupValues[1].trim()
                if (fact.isNotBlank()) {
                    // Derive a stable key from the fact content (first few words) so each
                    // "remember" statement gets its own slot instead of every entry
                    // overwriting a single hardcoded "fact" key (which silently destroyed
                    // all previously remembered facts).
                    val key = deriveNoteKey(fact)
                    return ResolvedIntent(
                        intent = AssistantIntent.REMEMBER_FACT,
                        confidence = 0.90f,
                        params = mapOf("key" to key, "value" to fact),
                        directPlan = ExecutionPlan("I have recorded that.", listOf(ActionPlan("NOTE", mapOf("key" to key, "value" to fact))))
                    )
                }
            }
        }

        // 4.1 Voice Quick Note & Knowledge Vault ("take a note", "create a note", "save note X", "quick note")
        val quickNoteMatch = Regex("^(?:take a note|create a note|quick note|save note|write note|note this down)\\s*(?:that|about|:|to)?\\s*(.+)?$", RegexOption.IGNORE_CASE).find(text)
        if (quickNoteMatch != null || text == "take a note" || text == "quick note" || text == "create a note") {
            val noteContent = quickNoteMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val params = if (noteContent.isNotBlank()) mapOf("action" to "add", "content" to noteContent) else mapOf("action" to "list")
            val desc = if (noteContent.isNotBlank()) "Saving note and indexing into Knowledge Vault." else "Listing saved notes."
            return ResolvedIntent(
                intent = AssistantIntent.REMEMBER_FACT,
                confidence = 0.94f,
                params = params,
                directPlan = ExecutionPlan(desc, listOf(ActionPlan("QUICK_NOTES", params)))
            )
        }

        // 5. Memory query ("what did I tell you about X", "meri memory me kya hai")
        if (text.startsWith("what did i tell you") || text.startsWith("search memory") || text.contains("yaad hai")) {
            val query = text.replace(Regex("^(what did i tell you about|search memory for|kya tumhe yaad hai)\\s*"), "").trim()
            return ResolvedIntent(
                intent = AssistantIntent.QUERY_MEMORY,
                confidence = 0.88f,
                params = mapOf("query" to query),
                directPlan = ExecutionPlan("Searching local memory.", listOf(ActionPlan("SEARCH_MEMORY", mapOf("query" to query))))
            )
        }

        // 5.9 Background Deep Research & Reports ("research about tony stark and give me report", "make a report on X", "deep research on Y")
        val researchPatterns = listOf(
            Regex("^(?:research about|research on|deep research on|background research on|research)\\s+(.+?)(?:\\s+(?:and give me report|and make report|and prepare report|and generate report|report))?$"),
            Regex("^(?:make a report on|make report on|prepare a report on|prepare report on|report on)\\s+(.+?)$"),
            Regex("^(.+?)\\s+(?:par research karo|ki report banao|report banao)$")
        )
        for (pattern in researchPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val topic = match.groupValues[1]
                    .replace("and give me report", "")
                    .replace("and make report", "")
                    .replace("and prepare report", "")
                    .replace("and generate report", "")
                    .trim()
                if (topic.isNotBlank() && !topic.startsWith("document") && !topic.startsWith("file")) {
                    return ResolvedIntent(
                        intent = AssistantIntent.DEEP_RESEARCH,
                        confidence = 0.95f,
                        params = mapOf("topic" to topic),
                        directPlan = ExecutionPlan("Conducting background research on $topic and generating report in JARVIS Reports folder.", listOf(ActionPlan("RESEARCH_DEEP", mapOf("topic" to topic))))
                    )
                }
            }
        }

        // Quick notes search / list ("search notes for X", "show my notes", "list notes")
        if (text.startsWith("search notes") || text.startsWith("search note")) {
            val q = text.replace(Regex("^(search notes for|search notes|search note for|search note)\\s*"), "").trim()
            return ResolvedIntent(
                intent = AssistantIntent.REMEMBER_FACT,
                confidence = 0.94f,
                params = mapOf("action" to "search", "query" to q),
                directPlan = ExecutionPlan("Searching saved notes.", listOf(ActionPlan("QUICK_NOTES", mapOf("action" to "search", "query" to q))))
            )
        }
        if (text == "show notes" || text == "show my notes" || text == "list notes" || text == "my notes") {
            return ResolvedIntent(
                intent = AssistantIntent.REMEMBER_FACT,
                confidence = 0.94f,
                params = mapOf("action" to "list"),
                directPlan = ExecutionPlan("Listing saved notes.", listOf(ActionPlan("QUICK_NOTES", mapOf("action" to "list"))))
            )
        }

        // Weather query ("weather in tokyo", "what is the weather in delhi", "delhi weather")
        val weatherMatch = Regex("^(?:what is the weather in|weather in|weather of|check weather for|weather)\\s+([a-zA-Z\\s]+)$", RegexOption.IGNORE_CASE).find(text)
        if (weatherMatch != null) {
            val city = weatherMatch.groupValues[1].trim()
            if (city.isNotBlank() && !city.equals("report", ignoreCase = true)) {
                return ResolvedIntent(
                    intent = AssistantIntent.SEARCH_WEB,
                    confidence = 0.94f,
                    params = mapOf("city" to city),
                    directPlan = ExecutionPlan("Checking weather for $city.", listOf(ActionPlan("WEATHER", mapOf("city" to city))))
                )
            }
        }

        // Voice Translation ("translate hello to spanish", "translate how are you in hindi")
        val translateVoiceMatch = Regex("^(?:translate)\\s+(.+?)\\s+(?:to|in|into)\\s+([a-zA-Z]+)$", RegexOption.IGNORE_CASE).find(text)
        if (translateVoiceMatch != null) {
            val textToTranslate = translateVoiceMatch.groupValues[1].trim()
            val targetLang = translateVoiceMatch.groupValues[2].trim()
            if (!textToTranslate.equals("screen", ignoreCase = true) && !textToTranslate.equals("this page", ignoreCase = true)) {
                return ResolvedIntent(
                    intent = AssistantIntent.SEARCH_WEB,
                    confidence = 0.95f,
                    params = mapOf("text" to textToTranslate, "target_lang" to targetLang),
                    directPlan = ExecutionPlan("Translating text.", listOf(ActionPlan("TRANSLATOR", mapOf("text" to textToTranslate, "target_lang" to targetLang))))
                )
            }
        }

        // 6. Web search ("search weather in delhi", "google search openai")
        if ((text.startsWith("search ") || text.startsWith("google ")) &&
            !text.startsWith("search document") &&
            !text.startsWith("search notes") &&
            !text.startsWith("search file") &&
            !text.startsWith("search memory") &&
            !text.startsWith("search knowledge")
        ) {
            val query = text.substringAfter(" ").trim()
            if (query.isNotBlank()) {
                return ResolvedIntent(
                    intent = AssistantIntent.SEARCH_WEB,
                    confidence = 0.90f,
                    params = mapOf("query" to query),
                    directPlan = ExecutionPlan("Searching for $query.", listOf(ActionPlan("SEARCH_WEB", mapOf("query" to query))))
                )
            }
        }

        // Storage Hub & Local Database Inspection ("storage stats", "database stats", "jarvis storage stats")
        if (text.contains("storage stats") || text.contains("storage status") || text.contains("database stats") || text.contains("jarvis storage") || text == "storage") {
            val params = mapOf("action" to "stats")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan("Inspecting central JARVIS storage hub.", listOf(ActionPlan("JARVIS_DB", params)))
            )
        }

        // 6c. Screen Translation Fast Path ("translate screen to hindi", "translate this page to english", etc.)
        val screenTranslateMatch = SCREEN_TRANSLATE_REGEX.find(text)
            ?: SCREEN_TRANSLATE_HINGLISH_REGEX.find(text)
            ?: SCREEN_WHAT_SAYS_REGEX.find(text)
        if (screenTranslateMatch != null) {
            val rawLang = screenTranslateMatch.groupValues.getOrNull(1)?.ifBlank { null } ?: "en"
            val targetLang = when {
                rawLang.startsWith("hi") || rawLang.contains("hindi") -> "hi"
                rawLang.startsWith("en") || rawLang.contains("english") -> "en"
                rawLang.startsWith("es") || rawLang.contains("spanish") -> "es"
                rawLang.startsWith("fr") || rawLang.contains("french") -> "fr"
                rawLang.startsWith("de") || rawLang.contains("german") -> "de"
                rawLang.startsWith("ja") || rawLang.contains("japanese") -> "ja"
                else -> rawLang
            }
            val params = mapOf("query" to utterance, "mode" to "translate", "target_lang" to targetLang)
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_VISION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Translating active screen content to $targetLang.", listOf(ActionPlan("SCREEN_VISION", params)))
            )
        }

        // 6d. Camera Lens Reader Fast Paths (Prescription, Document, Nutrition, Barcode)
        val lensPrescriptionMatch = LENS_PRESCRIPTION_REGEX.find(text)
        val lensDocMatch = LENS_DOCUMENT_REGEX.find(text)
        val lensNutritionMatch = LENS_NUTRITION_REGEX.find(text)
        val lensBarcodeMatch = LENS_BARCODE_REGEX.find(text)

        if (lensPrescriptionMatch != null || lensDocMatch != null || lensNutritionMatch != null || lensBarcodeMatch != null) {
            val mode = when {
                lensPrescriptionMatch != null -> "prescription"
                lensDocMatch != null -> "document"
                lensNutritionMatch != null -> "nutrition"
                else -> "barcode"
            }
            val facing = if (text.contains("selfie") || text.contains("front")) "front" else "back"
            val params = mapOf("query" to utterance, "mode" to mode, "facing" to facing)
            return ResolvedIntent(
                intent = AssistantIntent.CAMERA_VISION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Reading $mode through camera lens.", listOf(ActionPlan("CAMERA_VISION", params)))
            )
        }

        // 6e. Conversational Visual Q&A Fast Paths (Payment Status, Link Safety, Button Finder)
        if (PAYMENT_CHECK_REGEX.containsMatchIn(text)) {
            val params = mapOf("query" to utterance, "mode" to "payment_check")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_VISION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Checking payment status on active screen.", listOf(ActionPlan("SCREEN_VISION", params)))
            )
        }

        if (LINK_SAFETY_REGEX.containsMatchIn(text)) {
            val params = mapOf("query" to utterance, "mode" to "link_check")
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_VISION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Checking website and link safety on active screen.", listOf(ActionPlan("SCREEN_VISION", params)))
            )
        }

        val buttonMatch = FIND_BUTTON_REGEX.find(text)
        if (buttonMatch != null) {
            val targetBtn = buttonMatch.groupValues.getOrNull(1)?.trim() ?: "button"
            val params = mapOf("query" to utterance, "mode" to "find_button", "target" to targetBtn)
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_VISION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Finding $targetBtn button on screen.", listOf(ActionPlan("SCREEN_VISION", params)))
            )
        }

        // 7. Screen Vision intent ("what is on my screen", "is screen pe kya hai", "read this", etc.)
        val visionTriggers = listOf(
            "what is on my screen", "what's on my screen", "look at my screen",
            "look at this", "look at the screen", "read my screen", "read the screen",
            "read this", "read this message", "read this text", "summarize screen",
            "summarize this", "summarize page", "summarize article", "what does this say",
            "check screen", "analyze screen", "explain this", "explain screen", "explain what you see",
            "read error on screen", "what is this error", "screen error", "check screen error", "crash error",
            "extract code", "extract otp", "extract text", "read the text",
            "is screen pe kya hai", "screen pe kya hai", "ye screen dekho",
            "screen dekho", "ye dekh ke batao", "ye padh ke batao", "padh ke batao",
            "kya likha hai", "screen samjhao", "ye kya hai", "error samjhao", "page samjhao"
        )
        val isScreenQuery = !text.contains("log") && (
            visionTriggers.any { text.contains(it) } ||
            (text.contains("screen") && (text.contains("what") || text.contains("describe") || text.contains("see") || text.contains("look") || text.contains("error") || text.contains("crash") || text.contains("bug") || text.contains("issue") || text.contains("problem") || text.contains("summar") || text.contains("page") || text.contains("explain")))
        )
        if (isScreenQuery) {
            val mode = when {
                text.contains("error") || text.contains("crash") || text.contains("bug") || text.contains("issue") -> "error_check"
                text.contains("extract") -> "extract"
                text.contains("summar") -> "summarize"
                text.contains("explain") -> "explain"
                text.contains("describe") -> "describe"
                text.contains("read") -> "read"
                text.contains("off") || text.contains("stop") || text.contains("band") -> "off"
                text.contains("driving") || text.contains("drive") -> "driving"
                text.contains("meeting") || text.contains("meet") -> "meeting"
                Regex("\\bai\\b").containsMatchIn(text) || text.contains("smart") -> "ai"
                text.contains("busy") -> "busy"
                else -> "driving"
            }
            return ResolvedIntent(
                intent = AssistantIntent.SCREEN_VISION,
                confidence = 0.95f,
                params = mapOf("query" to utterance, "mode" to mode),
                directPlan = ExecutionPlan(
                    "Analyzing screen visual context.",
                    listOf(ActionPlan("SCREEN_VISION", mapOf("query" to utterance, "mode" to mode)))
                )
            )
        }

        // 7b. Financial Tracking / Expense Report
        if (text.contains("spend") || text.contains("spent") || text.contains("expense") || text.contains("budget") || text.contains("how much money")) {
            return ResolvedIntent(
                intent = AssistantIntent.EXPENSE_REPORT,
                confidence = 0.95f,
                params = emptyMap(),
                directPlan = ExecutionPlan(
                    "Checking your financial expenses.",
                    listOf(ActionPlan("EXPENSE_REPORT", emptyMap()))
                )
            )
        }

        // 8. Battery Check ("battery check", "battery kitni hai", "how much battery", etc.)
        if (text in listOf("battery health", "battery report", "check battery health", "battery diagnostics", "phone temperature")) {
            val params = mapOf("action" to "health")
            return ResolvedIntent(
                intent = AssistantIntent.BATTERY_CHECK,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Checking battery health diagnostics.", listOf(ActionPlan("BATTERY_CHECK", params)))
            )
        }

        val batteryTriggers = listOf(
            "battery", "battery check", "check battery", "battery level",
            "battery percentage", "how much battery", "battery kitni hai",
            "battery kitna hai", "battery bachi hai", "charging status"
        )
        if (batteryTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return ResolvedIntent(
                intent = AssistantIntent.BATTERY_CHECK,
                confidence = 0.96f,
                params = emptyMap(),
                directPlan = ExecutionPlan("Checking battery status.", listOf(ActionPlan("BATTERY_CHECK", emptyMap())))
            )
        }

        // 9. Daily Briefing ("briefing", "daily briefing", "morning briefing", "status report", "good morning")
        val briefingTriggers = listOf(
            "briefing", "daily briefing", "morning briefing", "status report",
            "good morning", "subah ki report", "kya chal raha hai", "system status"
        )
        if (briefingTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return ResolvedIntent(
                intent = AssistantIntent.DAILY_BRIEFING,
                confidence = 0.95f,
                params = emptyMap(),
                directPlan = ExecutionPlan("Preparing daily briefing.", listOf(ActionPlan("DAILY_BRIEFING", emptyMap())))
            )
        }

        // Phase 4: Interactive Notification Actions & Dismissal
        if (text.startsWith("clear all notifications") || text.startsWith("dismiss all notifications") ||
            text.startsWith("clear notifications") || text.startsWith("dismiss notifications") ||
            text == "clear notification" || text == "dismiss notification" ||
            NOTIF_DISMISS_ALL_REGEX.matches(text)) {
            val params = mapOf("action" to "dismiss_all")
            return ResolvedIntent(
                intent = AssistantIntent.NOTIFICATION_INTERACTION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Dismissing all notifications.", listOf(ActionPlan("NOTIFICATIONS_READ", params)))
            )
        }

        val readItemMatch = NOTIF_READ_ITEM_REGEX.find(text)
        if (readItemMatch != null) {
            val idx = readItemMatch.groupValues[1]
            val params = mapOf("action" to "read_item", "index" to idx)
            return ResolvedIntent(
                intent = AssistantIntent.NOTIFICATION_INTERACTION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Reading notification $idx.", listOf(ActionPlan("NOTIFICATIONS_READ", params)))
            )
        }

        val replyMatch = NOTIF_REPLY_REGEX.find(utterance.trim()) ?: NOTIF_REPLY_HINGLISH_REGEX.find(utterance.trim())
        if (replyMatch != null) {
            val idx = replyMatch.groupValues[1]
            val replyText = replyMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            val params = mutableMapOf("action" to "trigger_action", "index" to idx)
            if (replyText.isNotBlank()) params["text"] = replyText
            return ResolvedIntent(
                intent = AssistantIntent.NOTIFICATION_INTERACTION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Replying to notification $idx.", listOf(ActionPlan("NOTIFICATIONS_READ", params)))
            )
        }

        val actionMatch = NOTIF_ACTION_REGEX.find(text)
        if (actionMatch != null) {
            val idx = actionMatch.groupValues[1]
            val actQuery = actionMatch.groupValues.getOrNull(2).orEmpty()
            val params = mutableMapOf("action" to "trigger_action", "index" to idx)
            if (actQuery.isNotBlank()) params["query"] = actQuery
            return ResolvedIntent(
                intent = AssistantIntent.NOTIFICATION_INTERACTION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Triggering action on notification $idx.", listOf(ActionPlan("NOTIFICATIONS_READ", params)))
            )
        }

        val dismissItemMatch = NOTIF_DISMISS_ITEM_REGEX.find(text)
        if (dismissItemMatch != null) {
            val idx = dismissItemMatch.groupValues[1]
            val params = mapOf("action" to "dismiss_item", "index" to idx)
            return ResolvedIntent(
                intent = AssistantIntent.NOTIFICATION_INTERACTION,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Dismissing notification $idx.", listOf(ActionPlan("NOTIFICATIONS_READ", params)))
            )
        }

        // 10. Read Notifications / Digest / Clear Spam
        val isDigest = text.contains("digest") || (text.contains("summarize") && text.contains("notification")) ||
            text.contains("notifications summary") || text.contains("what did i miss") || text.contains("triage")
        val isClearSpam = (text.contains("clear") || text.contains("delete") || text.contains("remove") || text.contains("hatao")) &&
            (text.contains("spam") || text.contains("promo") || text.contains("junk"))
        val notifTriggers = listOf(
            "read notifications", "check notifications", "what notifications do i have",
            "read my notifications", "any new notifications", "any new messages",
            "kiska notification aaya", "kiska message aaya", "notifications padh",
            "notifications padh ke batao", "whatsapp notification", "whatsapp message check",
            "summarize notifications", "notification digest", "priority notifications",
            "what did i miss", "clear spam notifications", "clear spam", "spam notifications"
        )
        if (isDigest || isClearSpam || notifTriggers.any { text.contains(it) }) {
            val action = when {
                isClearSpam -> "clear_spam"
                isDigest -> "digest"
                text.contains("priority") -> "priority"
                else -> "read"
            }
            val app = if (text.contains("whatsapp")) "whatsapp" else ""
            val params = mutableMapOf("action" to action)
            if (app.isNotBlank()) params["app"] = app
            val desc = when (action) {
                "digest" -> "Generating priority notification digest."
                "clear_spam" -> "Clearing promotional and spam notifications."
                "priority" -> "Reading priority personal notifications."
                else -> "Reading recent notifications."
            }
            return ResolvedIntent(
                intent = AssistantIntent.READ_NOTIFICATIONS,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan(desc, listOf(ActionPlan("NOTIFICATIONS_READ", params)))
            )
        }

        // 11. Habit Suggestions ("what should i do", "suggest action", "kya karna chahiye", "kya karu", etc.)
        val habitSuggestTriggers = listOf(
            "what should i do", "suggest what to do", "suggest action", "proactive suggestion",
            "kya karna chahiye", "kuch suggest karo", "kya karu", "kya karun", "kya karein",
            "what do i usually do", "kya karta hoon"
        )
        if (habitSuggestTriggers.any { text.contains(it) }) {
            return ResolvedIntent(
                intent = AssistantIntent.HABIT_SUGGEST,
                confidence = 0.95f,
                params = emptyMap(),
                directPlan = ExecutionPlan("Generating personalized habit suggestion.", listOf(ActionPlan("HABIT_SUGGEST", emptyMap())))
            )
        }

        // 12. Habit List ("what are my habits", "my habits", "list habits", "meri habits", "learned habits")
        val habitListTriggers = listOf(
            "what are my habits", "my habits", "list habits", "show habits", "learned habits",
            "meri habits", "meri kya habits hain", "aadatein", "habits"
        )
        if (habitListTriggers.any { text.contains(it) }) {
            return ResolvedIntent(
                intent = AssistantIntent.HABIT_LIST,
                confidence = 0.95f,
                params = emptyMap(),
                directPlan = ExecutionPlan("Listing learned user habits.", listOf(ActionPlan("HABIT_LIST", emptyMap())))
            )
        }

        // 13. Smart Routine Run ("run bedtime routine", "start work routine", "bedtime routine chalu karo", etc.)
        val routineKeywords = listOf("routine", "mode", "chalu", "run", "start", "trigger")
        val routineTargets = listOf("bedtime", "night", "work", "office", "home", "low battery", "battery saver")
        if (routineTargets.any { text.contains(it) } && routineKeywords.any { text.contains(it) }) {
            val matchedId = when {
                text.contains("bedtime") || text.contains("night") -> "routine_bedtime"
                text.contains("work") || text.contains("office") -> "routine_work_mode"
                text.contains("home") -> "routine_home_arrival"
                text.contains("battery") -> "routine_low_battery"
                else -> ""
            }
            if (matchedId.isNotBlank()) {
                val params = mapOf("action" to "run", "routine_id" to matchedId)
                return ResolvedIntent(
                    intent = AssistantIntent.ROUTINE_RUN,
                    confidence = 0.96f,
                    params = params,
                    directPlan = ExecutionPlan("Running $matchedId.", listOf(ActionPlan("ROUTINE_MANAGE", params)))
                )
            }
        }

        // 14. Smart Routine List ("list automations", "show automations", "kya automations hain", "show routines", "list routines")
        val routineListTriggers = listOf(
            "list automations", "show automations", "active automations", "kya automations hain",
            "automations dikhao", "list smart routines", "smart routines",
            "routines", "my routines", "list routines", "show routines", "routine batao", "mera routine"
        )
        if (routineListTriggers.any { text.contains(it) }) {
            val params = mapOf("action" to "list")
            return ResolvedIntent(
                intent = AssistantIntent.ROUTINE_LIST,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan("Listing smart routines.", listOf(ActionPlan("ROUTINE_MANAGE", params)))
            )
        }

        // 15. Macro Run & List ("run macro ...", "macro chalu karo", "list macros")
        if (text.contains("macro") || text.contains("workflow")) {
            if (text.contains("list") || text.contains("show") || text.contains("dikhao") || text.contains("kya")) {
                val params = mapOf("action" to "list")
                return ResolvedIntent(
                    intent = AssistantIntent.MACRO_LIST,
                    confidence = 0.96f,
                    params = params,
                    directPlan = ExecutionPlan("Listing available UI macros.", listOf(ActionPlan("MACRO_WORKFLOW", params)))
                )
            }

            val targetMacroId = when {
                text.contains("clear") || text.contains("close all") -> "macro_clear_apps"
                text.contains("youtube") -> "macro_youtube_search"
                text.contains("update") -> "macro_software_update"
                text.contains("home") -> "macro_quick_home"
                text.contains("storage") || text.contains("clean") || text.contains("space") -> "macro_device_storage"
                text.contains("status") || text.contains("updates") -> "macro_whatsapp_status"
                else -> ""
            }

            if (targetMacroId.isNotBlank()) {
                val params = mapOf("action" to "run", "macro_id" to targetMacroId)
                return ResolvedIntent(
                    intent = AssistantIntent.MACRO_RUN,
                    confidence = 0.96f,
                    params = params,
                    directPlan = ExecutionPlan("Running $targetMacroId.", listOf(ActionPlan("MACRO_WORKFLOW", params)))
                )
            }
        }

        // 16. Telephony Call & Dial ("call mom", "dial 911", "papa ko call karo", "test calling doller")
        //     Exclude: reject/end/answer/kaat keywords so they fall through to rules 33-34
        val callAnswerRejectKeywords = listOf("kaat", "kato", "reject", "decline", "end call", "disconnect", "answer", "uthao", "receive")
        val isCallAnswerRejectContext = callAnswerRejectKeywords.any { text.contains(it) }
        if (!text.contains("whatsapp") && !isCallAnswerRejectContext &&
            (text.startsWith("call ") || text.startsWith("dial ") || text.startsWith("calling ") || text.startsWith("test calling ") ||
             text.contains("phone lagao") || text.contains("call karo") || text.contains("call lagao") || text.contains("ko call"))
        ) {
            val isDial = text.startsWith("dial")
            val target = text.replace(Regex("\\b(test|calling|call|dial|phone|lagao|karo|ko|please|karna hai)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            if (target.isNotBlank()) {
                val action = if (isDial) "dial" else "call"
                val params = mapOf("action" to action, "recipient" to target)
                return ResolvedIntent(
                    intent = AssistantIntent.TELEPHONY_CALL,
                    confidence = 0.96f,
                    params = params,
                    directPlan = ExecutionPlan("Initiating call to $target.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
                )
            }
        }

        // 17. Telephony SMS ("send sms to rahul", "text alex")
        if (!text.contains("whatsapp") && (text.startsWith("send sms") || text.startsWith("sms ") || text.startsWith("text "))) {
            val target = text.replace(Regex("\\b(send|sms|text|to|message)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            if (target.isNotBlank()) {
                val params = mapOf("action" to "sms_draft", "recipient" to target)
                return ResolvedIntent(
                    intent = AssistantIntent.TELEPHONY_SMS,
                    confidence = 0.95f,
                    params = params,
                    directPlan = ExecutionPlan("Opening SMS draft for $target.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
                )
            }
        }

        // 18. Calendar Agenda & Schedule ("today's agenda", "aaj ka schedule", "what is on my calendar")
        val agendaTriggers = listOf("today's agenda", "today agenda", "aaj ka schedule", "what is on my calendar", "my schedule today", "calendar agenda")
        if (agendaTriggers.any { text.contains(it) }) {
            val params = mapOf("action" to "today_agenda")
            return ResolvedIntent(
                intent = AssistantIntent.CALENDAR_AGENDA,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Retrieving today's agenda.", listOf(ActionPlan("CALENDAR_MANAGE", params)))
            )
        }

        if (text.startsWith("schedule meeting") || text.startsWith("schedule event")) {
            val details = text.replace("schedule", "").trim()
            val params = mapOf("action" to "add_event", "title" to details, "time" to text)
            return ResolvedIntent(
                intent = AssistantIntent.CALENDAR_SCHEDULE,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan("Scheduling: $details.", listOf(ActionPlan("CALENDAR_MANAGE", params)))
            )
        }

        // 19. Storage & App Permissions ("give storage permission", "storage permission do", "open permission settings")
        if (text.contains("storage permission") || text.contains("storage permissions") ||
            text.contains("grant storage") || text.contains("allow storage") ||
            text.contains("permission do") || text.contains("permission de") ||
            text in listOf("give permission", "give permissions", "grant permissions", "allow permissions", "open app settings", "permission settings")) {
            val params = mapOf("action" to "storage_permission")
            return ResolvedIntent(
                intent = AssistantIntent.DEVICE_SETTINGS,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Opening application storage permission settings.", listOf(ActionPlan("DEVICE_SETTINGS", params)))
            )
        }

        // 19.1 Security & Privacy Audit ("check app permissions", "privacy audit", "privacy score", "scan security")
        val securityTriggers = listOf("check permissions", "check app permissions", "privacy audit", "privacy score", "scan security", "who has access to my camera", "camera and mic permissions")
        if (securityTriggers.any { text.contains(it) }) {
            val action = if (text.contains("camera") || text.contains("mic")) "scan_camera_mic_apps" else if (text.contains("score")) "privacy_score" else "audit_permissions"
            val params = mapOf("action" to action)
            return ResolvedIntent(
                intent = AssistantIntent.SECURITY_AUDIT,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Performing security and privacy inspection.", listOf(ActionPlan("SECURITY_AUDIT", params)))
            )
        }

        // 20. File Search & Storage ("find file ...", "storage breakdown", "check storage")
        if (text.contains("storage breakdown") || text.contains("check storage") || text == "storage") {
            val params = mapOf("action" to "storage_breakdown")
            return ResolvedIntent(
                intent = AssistantIntent.FILE_SEARCH,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Retrieving device storage statistics.", listOf(ActionPlan("FILE_MANAGER", params)))
            )
        }

        if (text.startsWith("find file ") || text.startsWith("search file ")) {
            val query = text.replace(Regex("^(?:find|search)\\s+file\\s+"), "").trim()
            val params = mapOf("action" to "search_file", "query" to query)
            return ResolvedIntent(
                intent = AssistantIntent.FILE_SEARCH,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan("Searching for file: $query.", listOf(ActionPlan("FILE_MANAGER", params)))
            )
        }

        // 21. Autonomous Autopilot & Ambient Modes
        if (text in listOf("disable autopilot", "autopilot off", "turn off autopilot", "stop autopilot", "autopilot band karo", "disable autonomous mode")) {
            val params = mapOf("action" to "disable_autopilot")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Disengaging autonomous autopilot.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text in listOf("enable autopilot", "autopilot on", "turn on autopilot", "jarvis take over", "auto pilot on", "autonomous mode", "autonomous mode chalu karo")) {
            val params = mapOf("action" to "enable_autopilot")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Engaging autonomous autopilot.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text.contains("driving mode")) {
            val isOff = text.contains("off") || text.contains("disable") || text.contains("stop") || text.contains("band")
            val modeVal = if (isOff) "standby" else "driving"
            val params = mapOf("action" to "set_ambient_mode", "mode" to modeVal)
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Setting Driving Ambient Mode.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text.contains("meeting mode")) {
            val isOff = text.contains("off") || text.contains("disable") || text.contains("stop") || text.contains("band")
            val modeVal = if (isOff) "standby" else "meeting"
            val params = mapOf("action" to "set_ambient_mode", "mode" to modeVal)
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Setting Meeting Ambient Mode.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text.contains("focus mode") || text.contains("deep work")) {
            val isOff = text.contains("off") || text.contains("disable") || text.contains("stop") || text.contains("band")
            val modeVal = if (isOff) "standby" else "focus"
            val params = mapOf("action" to "set_ambient_mode", "mode" to modeVal)
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Setting Deep Focus Mode.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text.contains("night mode") || text.contains("sleep mode")) {
            val params = mapOf("action" to "set_ambient_mode", "mode" to "night")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Activating Night Wind-Down Mode.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text.contains("workout mode") || text.contains("gym mode")) {
            val params = mapOf("action" to "set_ambient_mode", "mode" to "workout")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Activating Workout Session Mode.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text.contains("auto mode") || text.contains("automatic mode") || text.contains("sensor mode")) {
            val params = mapOf("action" to "set_ambient_mode", "mode" to "auto")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Switching to Automatic Ambient Mode.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text in listOf("autopilot status", "autonomous status", "autopilot kya hai")) {
            val params = mapOf("action" to "status")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Checking autonomous autopilot status.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        if (text in listOf("decision logs", "autonomous logs", "autopilot history", "autopilot log", "decision log")) {
            val params = mapOf("action" to "recent_actions")
            return ResolvedIntent(
                intent = AssistantIntent.AUTONOMOUS_CONTROL,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Retrieving recent autonomous decisions.", listOf(ActionPlan("AUTONOMOUS_CONTROL", params)))
            )
        }

        // 27. RAG Knowledge Vault Index
        val indexPatterns = listOf(
            Regex("^(?:index|add to vault|save to vault|save document)\\s+(.+)$"),
            Regex("^(.+)\\s+(?:ko index karo|vault me dalo|vault me save karo)$")
        )
        for (p in indexPatterns) {
            val match = p.find(text)
            if (match != null) {
                val target = match.groupValues[1].trim()
                val params = mapOf("content" to target)
                return ResolvedIntent(
                    intent = AssistantIntent.RAG_INDEX,
                    confidence = 0.94f,
                    params = params,
                    directPlan = ExecutionPlan("Indexing into Knowledge Vault.", listOf(ActionPlan("RAG_INDEX", params)))
                )
            }
        }

        // 28. RAG Knowledge Vault Retrieval ("search documents for...", "find in notes...", "what do my notes say about...")
        val ragPatterns = listOf(
            Regex("^(?:search documents|search notes|search knowledge|find in documents|find in notes|vault search)\\s+(?:for\\s+)?(.+)$"),
            Regex("^(?:what do my notes say about|what does the doc say about|notes me kya hai)\\s+(.+)$"),
            Regex("^(.+)\\s+(?:documents me dhundo|notes me dhundo|vault me dhundo)$")
        )
        for (p in ragPatterns) {
            val match = p.find(text)
            if (match != null) {
                val q = match.groupValues[1].trim()
                val params = mapOf("query" to q)
                return ResolvedIntent(
                    intent = AssistantIntent.RAG_QUERY,
                    confidence = 0.95f,
                    params = params,
                    directPlan = ExecutionPlan("Searching Knowledge Vault for \"$q\".", listOf(ActionPlan("RAG_RETRIEVE", params)))
                )
            }
        }

        // 29. Camera Optical Vision & Real-Time Object Perception
        val cameraTriggers = listOf(
            "what am i looking at", "what's this object", "what is this object",
            "look at this", "identify this object", "describe what you see",
            "camera look", "camera scan", "what is in front of me",
            "camera se dekho", "camera on karke dekho", "samne kya hai", "camera se padho"
        )
        if (cameraTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val facing = if (text.contains("selfie") || text.contains("front")) "front" else "back"
            val params = mapOf("query" to text, "facing" to facing)
            return ResolvedIntent(
                intent = AssistantIntent.CAMERA_VISION,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Looking through camera lens to identify object.", listOf(ActionPlan("CAMERA_VISION", params)))
            )
        }

        // 30. WhatsApp Read & Message Aggregation ("read whatsapp", "whatsapp padho", "koi whatsapp aaya", "check unread whatsapp")
        val waReadTriggers = listOf(
            "read whatsapp", "check whatsapp", "read my whatsapp", "whatsapp padho",
            "whatsapp messages padho", "koi whatsapp aaya", "kisi ka whatsapp aaya",
            "unread whatsapp", "whatsapp par kya aaya", "check unread whatsapp",
            "whatsapp message padho"
        )
        if (waReadTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val params = mapOf("action" to "read_messages")
            return ResolvedIntent(
                intent = AssistantIntent.WHATSAPP_READ,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Reading recent WhatsApp messages.", listOf(ActionPlan("WHATSAPP", params)))
            )
        }

        // 31. WhatsApp Auto-Reply ("turn on auto reply", "whatsapp auto reply driving", "auto reply off")
        val autoReplyTriggers = listOf(
            "auto reply", "autoreply", "whatsapp auto reply"
        )
        if (autoReplyTriggers.any { text.contains(it) }) {
            val mode = when {
                text.contains("off") || text.contains("stop") || text.contains("band") -> "off"
                text.contains("driving") || text.contains("drive") -> "driving"
                text.contains("meeting") || text.contains("meet") -> "meeting"
                Regex("\\bai\\b").containsMatchIn(text) || text.contains("smart") -> "ai"
                text.contains("busy") -> "busy"
                else -> "driving"
            }
            val params = mapOf("action" to "auto_reply", "mode" to mode)
            return ResolvedIntent(
                intent = AssistantIntent.WHATSAPP_AUTO_REPLY,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Configuring WhatsApp auto-reply mode to $mode.", listOf(ActionPlan("WHATSAPP", params)))
            )
        }

        // 31.1 WhatsApp Send Message ("Doller ko hello send karo whatsapp pe", "send hello to Doller on whatsapp", "whatsapp Doller hello")
        if (text.contains("whatsapp") || text.contains("wa ")) {
            val waSendPatterns = listOf(
                Regex("^(.+?)\\s+ko\\s+(.+?)\\s+(?:send|bhejo|bhej do|message|msg)\\s*(?:karo)?\\s*(?:whatsapp|wa)\\s*(?:pe|par|me|in)?$"),
                Regex("^(?:whatsapp|wa)\\s+(?:pe|par|me)?\\s*(?:message|msg)?\\s*(?:karo)?\\s*(.+?)\\s+ko\\s+(.+?)(?:\\s+(?:send|bhejo|message|msg|karo)\\s*(?:karo)?)?$"),
                Regex("^(.+?)\\s+ko\\s+(?:whatsapp|wa)\\s*(?:pe|par|me)?\\s*(?:message|msg)?\\s*(.+?)(?:\\s+(?:send|bhejo|karo|bhej do))?$"),
                Regex("^(?:send|bhejo)\\s+(.+?)\\s+(?:to|ko)\\s+(.+?)\\s+(?:on|in|pe|par)\\s*(?:whatsapp|wa)$"),
                Regex("^(?:send\\s+)?(?:whatsapp|wa)\\s*(?:message|msg)?\\s*(?:to|ko)?\\s*([a-zA-Z0-9_]+)\\s+(?:saying|that|message|msg|:)?\\s*(.+)$")
            )
            for (p in waSendPatterns) {
                val m = p.find(text)
                if (m != null) {
                    val (rawRecipient, rawMessage) = if (p == waSendPatterns[3]) {
                        Pair(m.groupValues[2].trim(), m.groupValues[1].trim())
                    } else {
                        Pair(m.groupValues[1].trim(), m.groupValues[2].trim())
                    }
                    val recipient = rawRecipient.replace(Regex("\\b(whatsapp|wa|pe|par|me|ko|to|message|msg|send)\\b"), "").trim()
                    val message = rawMessage.replace(Regex("\\b(whatsapp|wa|pe|par|me|bhejo|send|karo|bhej do)\\b"), "").trim()
                    val messageIsPlaceholder = message.equals("message", ignoreCase = true) ||
                            message.equals("msg", ignoreCase = true)
                    if (recipient.isNotBlank() && message.isNotBlank() && !messageIsPlaceholder &&
                        recipient != "auto" && recipient != "read") {
                        val params = mapOf("action" to "send_message", "recipient" to recipient, "message" to message)
                        return ResolvedIntent(
                            intent = AssistantIntent.WHATSAPP_SEND,
                            confidence = 0.96f,
                            params = params,
                            directPlan = ExecutionPlan("Sending WhatsApp message to $recipient.", listOf(ActionPlan("WHATSAPP", params)))
                        )
                    }
                }
            }
        }

        // 31.2 WhatsApp & Messaging Quick Reply
        // e.g. "reply to Rahul saying I am on the way", "reply to John Doe that I'll be late", "reply to Alice: okay",
        //      "reply that I am driving", "reply saying I am busy", "reply I will be late", "Rahul ko reply karo call you soon"
        if (text.startsWith("reply ") || text.contains(" ko reply karo") || text.contains(" ko reply do") || text.startsWith("respond ")) {
            val replyToPattern = Regex("^(?:reply|respond)\\s+to\\s+(.+?)(?:\\s+(?:saying|that|with)|[:,]\\s*|\\s+[:,]\\s*)(.+)$", RegexOption.IGNORE_CASE)
            val replyToColonPattern = Regex("^(?:reply|respond)\\s+to\\s+(.+?):\\s*(.+)$", RegexOption.IGNORE_CASE)
            val replyHinglishPattern = Regex("^(.+?)\\s+ko\\s+reply\\s+(?:karo|do)\\s*(?:ki|saying)?\\s*(.+)$", RegexOption.IGNORE_CASE)
            val replyContextualPattern = Regex("^(?:reply|respond)\\s+(?:saying|that|with|:)?\\s*(.+)$", RegexOption.IGNORE_CASE)

            val rawUtterance = utterance.trim()
            val matchA = replyToPattern.find(rawUtterance) ?: replyToColonPattern.find(rawUtterance)
            val matchB = if (matchA == null) replyHinglishPattern.find(rawUtterance) else null
            val matchC = if (matchA == null && matchB == null && !text.contains("notification")) replyContextualPattern.find(rawUtterance) else null

            val (recipient, message) = when {
                matchA != null -> {
                    val r = matchA.groupValues[1].replace(Regex("^(?:to|mr|mrs|ms)\\s+", RegexOption.IGNORE_CASE), "").trim()
                    val m = matchA.groupValues[2].trim()
                    Pair(r, m)
                }
                matchB != null -> {
                    val r = matchB.groupValues[1].trim()
                    val m = matchB.groupValues[2].trim()
                    Pair(r, m)
                }
                matchC != null -> {
                    val m = matchC.groupValues[1].trim()
                    Pair("", m)
                }
                else -> Pair(null, null)
            }

            if (message != null && message.isNotBlank()) {
                val cleanRecipient = (recipient ?: "").trim().split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    }
                val params = mutableMapOf("action" to "reply", "message" to message)
                if (cleanRecipient.isNotBlank()) {
                    params["recipient"] = cleanRecipient
                }
                val desc = if (cleanRecipient.isNotBlank()) "Replying to $cleanRecipient: \"$message\"." else "Replying: \"$message\"."
                return ResolvedIntent(
                    intent = AssistantIntent.WHATSAPP_SEND,
                    confidence = 0.97f,
                    params = params,
                    directPlan = ExecutionPlan(desc, listOf(ActionPlan("WHATSAPP", params)))
                )
            }
        }

        // 32. System Logs & Telemetry ("read logs", "logs padho", "show logs", "check logs", "logcat")
        val logTriggers = listOf(
            "read logs", "read log", "show logs", "show log", "check logs", "check log",
            "system logs", "system log", "view logs", "device logs", "show logcat",
            "read logcat", "logs padho", "logs dikhao", "logcat dikhao", "kya error aaya",
            "error check karo", "kya hua tha", "recent logs", "check error logs",
            "error logs", "error log", "errors check karo", "error log dikhao"
        )
        if (logTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val filter = if (text.contains("error")) "error" else ""
            val params = if (filter.isNotBlank()) mapOf("filter" to filter) else emptyMap()
            return ResolvedIntent(
                intent = AssistantIntent.READ_LOGS,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Reading live system telemetry logs.", listOf(ActionPlan("LOGS_READ", params)))
            )
        }

        // 33. Telephony Answer Call ("answer call", "phone uthao", "call receive karo", "pick up call", "answer")
        val answerTriggers = listOf(
            "answer call", "answer the call", "pick up the call", "pick up call", "pick up",
            "phone uthao", "call uthao", "call receive karo", "phone receive karo", "call receive",
            "answer phone", "accept call", "call accept karo", "phone pick karo", "answer", "uthalo", "haan uthao"
        )
        if (answerTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val params = mapOf("action" to "answer_call")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_ANSWER,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Answering incoming call hands-free.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        // Telephony Caller Info ("who is calling", "kaun call kar raha hai", "kiska call hai")
        val callerInfoTriggers = listOf(
            "who is calling", "who's calling", "who is calling me", "who is on the phone",
            "kiska call hai", "kaun call kar raha hai", "kaun hai call pe", "kiska phone hai"
        )
        if (callerInfoTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val params = mapOf("action" to "caller_info")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_CALLER_INFO,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Checking incoming caller identity.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        // Telephony Reject Call with SMS
        if ((text.contains("reject") || text.contains("kaat") || text.contains("cut") || text.contains("decline") || text.contains("bol do")) &&
            (text.contains("busy") || text.contains("sms") || text.contains("message") || text.contains("meeting") || text.contains("driving"))) {
            val msg = when {
                text.contains("driving") -> "I am driving right now, will call you later."
                text.contains("meeting") -> "I am in a meeting, will get back to you soon."
                else -> "I am currently busy, will call you back later."
            }
            val params = mapOf("action" to "reject_call", "message" to msg)
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_REJECT,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Rejecting incoming call with auto-reply message.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        // 34. Telephony Reject Call ("reject call", "call kaat do", "decline call", "phone kato", "reject")
        val rejectTriggers = listOf(
            "reject call", "reject the call", "decline call", "decline the call", "cut the call",
            "call kaat do", "phone kaat do", "call reject karo", "phone reject karo", "phone kato",
            "call kato", "disconnect call", "end call", "reject", "decline", "cut", "kat do"
        )
        if (rejectTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val params = mapOf("action" to "reject_call")
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_REJECT,
                confidence = 0.98f,
                params = params,
                directPlan = ExecutionPlan("Rejecting incoming call.", listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        // 35. Read SMS & OTP ("read sms", "check sms", "sms padho", "what is my otp", "otp kya aaya")
        val smsReadTriggers = listOf(
            "read sms", "check sms", "read my sms", "read text message", "sms padho",
            "message padho", "koi sms aaya", "check message", "read messages", "what is my otp",
            "get otp", "read otp", "otp kya hai", "otp batao", "otp kya aaya", "kiska sms aaya",
            "latest sms", "recent sms"
        )
        if (smsReadTriggers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            val isOtp = text.contains("otp")
            val params = if (isOtp) mapOf("action" to "get_otp") else mapOf("action" to "sms_read")
            val desc = if (isOtp) "Extracting verification OTP code from recent SMS." else "Reading incoming SMS messages."
            return ResolvedIntent(
                intent = AssistantIntent.TELEPHONY_SMS_READ,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan(desc, listOf(ActionPlan("TELEPHONY_CONTROL", params)))
            )
        }

        // 36. Timer, Alarm & Reminder ("set a timer for 5 minutes", "set alarm 7 am", "remind me in 20 minutes to X", "timer chalao")
        if (text.contains("timer")) {
            val whenText = text.replace(Regex("\\b(set|a|for|timer|chalao|the)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            val params = mapOf("action" to "set_timer", "when" to whenText)
            return ResolvedIntent(
                intent = AssistantIntent.SET_TIMER,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Setting a timer.", listOf(ActionPlan("REMINDER_SCHEDULE", params)))
            )
        }

        if (text.contains("alarm")) {
            val whenText = text.replace(Regex("\\b(set|alarm|at|chalao|karo|the)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            val params = mapOf("action" to "set_alarm", "time" to whenText)
            return ResolvedIntent(
                intent = AssistantIntent.SET_ALARM,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Setting an alarm.", listOf(ActionPlan("REMINDER_SCHEDULE", params)))
            )
        }

        val cancelReminder = Regex("^(?:cancel|delete|remove)\\s+(?:the\\s+)?(?:reminder|routine reminder)(?:\\s+(?:for|about|matching))?\\s+(.+)$").find(text)
        if (cancelReminder != null) {
            val params = mapOf("action" to "cancel", "routine" to cancelReminder.groupValues[1].trim())
            return ResolvedIntent(
                intent = AssistantIntent.REMIND_ME,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Cancelling the matching reminder.", listOf(ActionPlan("REMINDER_SCHEDULE", params)))
            )
        }

        if (text.startsWith("remind me") || text.contains("yaad dila") || text.contains("reminder")) {
            val reachMatch = Regex("(?:remind me to|yaad dilana)\\s+(.+?)\\s+(?:when i reach|reach|at|jab pahunchu)\\s+(home|work|office|ghar)", RegexOption.IGNORE_CASE).find(text)
            if (reachMatch != null) {
                val reminderTask = reachMatch.groupValues[1].trim()
                val rawTarget = reachMatch.groupValues[2].trim().lowercase()
                val targetLoc = if (rawTarget == "ghar") "home" else if (rawTarget == "office") "work" else rawTarget
                val params = mapOf("action" to "remind_at", "target" to targetLoc, "reminder" to reminderTask)
                return ResolvedIntent(
                    intent = AssistantIntent.WHERE_AM_I,
                    confidence = 0.97f,
                    params = params,
                    directPlan = ExecutionPlan("Setting location reminder for $targetLoc.", listOf(ActionPlan("LOCATION", params)))
                )
            }
            val params = mapOf("action" to "remind_me", "what" to text)
            return ResolvedIntent(
                intent = AssistantIntent.REMIND_ME,
                confidence = 0.96f,
                params = params,
                directPlan = ExecutionPlan("Scheduling a reminder.", listOf(ActionPlan("REMINDER_SCHEDULE", params)))
            )
        }

        // 37. Where am I, Ambient Context & Geofence Configuration
        if (text.contains("set home location") || text.contains("set home wifi") || text.contains("this is home") || text.contains("save home")) {
            val params = mapOf("action" to "set_home")
            return ResolvedIntent(
                intent = AssistantIntent.WHERE_AM_I,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Saving current location as home.", listOf(ActionPlan("LOCATION", params)))
            )
        }

        if (text.contains("set work location") || text.contains("set work wifi") || text.contains("this is work") || text.contains("save work") || text.contains("set office")) {
            val params = mapOf("action" to "set_work")
            return ResolvedIntent(
                intent = AssistantIntent.WHERE_AM_I,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Saving current location as work.", listOf(ActionPlan("LOCATION", params)))
            )
        }

        if (text.contains("check context") || text.contains("what is my context") || text.contains("ambient mode") || text.contains("current context")) {
            val params = mapOf("action" to "check_context")
            return ResolvedIntent(
                intent = AssistantIntent.WHERE_AM_I,
                confidence = 0.97f,
                params = params,
                directPlan = ExecutionPlan("Checking ambient context.", listOf(ActionPlan("LOCATION", params)))
            )
        }

        if (text.contains("where am i") || text.contains("my location") || text.contains("mera location") ||
            (text.contains("location") && !text.contains("set"))) {
            val params = mapOf("action" to "where_am_i")
            return ResolvedIntent(
                intent = AssistantIntent.WHERE_AM_I,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan("Fetching your location.", listOf(ActionPlan("LOCATION", params)))
            )
        }

        // 38. Read messages ("read my messages", "read whatsapp", "check messages padho")
        if ((text.contains("read") || text.contains("check") || text.contains("padho")) &&
            (text.contains("message") || text.contains("msg") || text.contains("sms") || text.contains("whatsapp"))) {
            val action = when {
                text.contains("whatsapp") && !text.contains("sms") && !text.contains("message") -> "read_whatsapp"
                (text.contains("sms") || text.contains("text")) && !text.contains("whatsapp") && !text.contains("message") -> "read_sms"
                else -> "read_all"
            }
            val params = mapOf("action" to action)
            return ResolvedIntent(
                intent = AssistantIntent.READ_MESSAGES,
                confidence = 0.95f,
                params = params,
                directPlan = ExecutionPlan("Reading recent messages.", listOf(ActionPlan("MESSAGE_READER", params)))
            )
        }

        return null
    }

    /**
     * Builds a stable memory key from fact content: the leading words, lowercased,
     * hyphen-joined, capped at 5 words. Two facts that start the same way share a
     * slot; each distinct topic is archived separately when superseded.
     */
    fun deriveNoteKey(fact: String): String {
        val words = fact.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() && it !in listOf("the", "a", "an", "is", "are", "my", "mera", "mere", "ha") }
        val base = words.take(5).joinToString("-")
        return if (base.isBlank()) "fact" else base
    }

    private fun normalizeAppName(raw: String): String {
        val trimmed = raw.trim().lowercase(Locale.ROOT)
        if (com.jarvis.tools.AppRegistry.ALLOWED_PACKAGES.containsKey(trimmed)) {
            return trimmed
        }
        return when {
            trimmed in listOf("yt", "ytube") -> "youtube"
            trimmed in listOf("insta", "ig") -> "instagram"
            trimmed in listOf("wp", "wa", "whats app", "whats up") -> "whatsapp"
            trimmed in listOf("y music", "yt music", "youtube music") -> "ymusic"
            trimmed in listOf("mx", "mx player", "mxplayer", "mx pro", "mx player pro", "mxplayer pro") -> "mx player"
            trimmed in listOf("samsung note", "samsung notes", "s notes", "s-notes", "note", "notes") -> "samsung notes"
            trimmed in listOf("gaming hub", "game hub", "game launcher", "samsung game launcher", "samsung gaming hub") -> "gaming hub"
            trimmed in listOf("chatgpt", "chat gpt", "openai", "open ai") -> "chatgpt"
            trimmed in listOf("gemini", "google gemini", "bard") -> "gemini"
            trimmed in listOf("google", "google search", "google app") -> "google"
            trimmed in listOf("chrome", "google chrome", "chrome browser", "browser") -> "chrome"
            trimmed in listOf("cam", "camera", "photo camera") -> "camera"
            trimmed == "ymusic" || trimmed.startsWith("ymusic") -> "ymusic"
            trimmed == "youtube" || trimmed.startsWith("youtube") -> "youtube"
            trimmed == "whatsapp" || trimmed.startsWith("whatsapp") -> "whatsapp"
            trimmed == "instagram" || trimmed.startsWith("instagram") -> "instagram"
            trimmed == "music" || trimmed.startsWith("music") || trimmed.contains("gaana") -> "music"
            trimmed == "chrome" || trimmed.startsWith("chrome") -> "chrome"
            trimmed == "camera" || trimmed.startsWith("camera") -> "camera"
            trimmed == "settings" || trimmed.startsWith("settings") -> "settings"
            trimmed == "termux" || trimmed.startsWith("termux") -> "termux"
            else -> trimmed
        }
    }
}
