package com.jarvis.ui.data

import android.content.Context
import com.jarvis.ui.model.ApiConfig
import com.jarvis.ui.model.AppSettings
import com.jarvis.ui.model.AuthMethod
import org.json.JSONArray
import org.json.JSONObject

class UiPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_ui", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { jsonToAppSettings(JSONObject(raw)) }.getOrDefault(AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, settings.toJson().toString()).apply()
    }

    fun loadApis(): List<ApiConfig> {
        val raw = prefs.getString(KEY_APIS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { jsonToApiConfig(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveApis(apis: List<ApiConfig>) {
        val arr = JSONArray()
        apis.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_APIS, arr.toString()).apply()
    }

    fun exportSettings(): String {
        val root = JSONObject()
        root.put("settings", loadSettings().toJson())
        val apisArray = JSONArray()
        loadApis().forEach { apisArray.put(it.toJson()) }
        root.put("apis", apisArray)
        val varsObj = JSONObject()
        loadCustomVariables().forEach { (k, v) -> varsObj.put(k, v) }
        root.put("customVariables", varsObj)
        return root.toString()
    }

    /**
     * Imports only user configuration. Runtime state, credentials and logs are
     * deliberately excluded so an exported file cannot silently restore secrets.
     */
    fun importSettings(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        if (root.has("settings")) {
            val sObj = root.optJSONObject("settings") ?: JSONObject(root.getString("settings"))
            saveSettings(jsonToAppSettings(sObj))
        }
        if (root.has("apis")) {
            val aArray = root.optJSONArray("apis") ?: JSONArray(root.getString("apis"))
            val apis = (0 until aArray.length()).map { jsonToApiConfig(aArray.getJSONObject(it)) }
            saveApis(apis)
        }
        if (root.has("customVariables")) {
            val vObj = root.optJSONObject("customVariables") ?: JSONObject(root.getString("customVariables"))
            val vars = mutableMapOf<String, String>()
            val keys = vObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                vars[k] = vObj.optString(k, "")
            }
            val storedObj = JSONObject()
            vars.forEach { (k, v) -> storedObj.put(k, v) }
            prefs.edit().putString(KEY_CUSTOM_VARIABLES, storedObj.toString()).apply()
        }
        true
    }.getOrDefault(false)

    fun saveCustomVariable(key: String, value: String) {
        val current = loadCustomVariables().toMutableMap()
        current[key] = value
        val obj = JSONObject()
        current.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_CUSTOM_VARIABLES, obj.toString()).apply()
    }

    fun deleteCustomVariable(key: String) {
        val current = loadCustomVariables().toMutableMap()
        current.remove(key)
        val obj = JSONObject()
        current.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_CUSTOM_VARIABLES, obj.toString()).apply()
    }

    fun loadCustomVariables(): Map<String, String> {
        val raw = prefs.getString(KEY_CUSTOM_VARIABLES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optString(k, "")
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun saveLastApiStatus(apiId: String, code: Int, message: String = "OK") {
        prefs.edit().putString("api_status_$apiId", "$code|${message.take(180)}").apply()
    }

    fun lastApiStatus(apiId: String): String? = prefs.getString("api_status_$apiId", null)

    fun appendLog(entry: String) {
        val current = prefs.getStringSet(KEY_LOGS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current += entry
        while (current.size > 200) current.remove(current.first())
        prefs.edit().putStringSet(KEY_LOGS, current).apply()
    }

    fun logs(): List<String> = prefs.getStringSet(KEY_LOGS, emptySet())?.toList()?.sortedDescending() ?: emptyList()

    fun clearLogs() { prefs.edit().remove(KEY_LOGS).apply() }

    private fun AppSettings.toJson(): JSONObject = JSONObject().apply {
        put("theme", theme)
        put("notificationsEnabled", notificationsEnabled)
        put("defaultTimeoutMs", defaultTimeoutMs)
        put("maxRetryAttempts", maxRetryAttempts)
        put("logVerbosity", logVerbosity)
        put("wakeWord", wakeWord)
        put("preferredLlmModel", preferredLlmModel)
        put("developerMode", developerMode)
        put("continuousConversation", continuousConversation)
        put("followUpTimeoutSeconds", followUpTimeoutSeconds)
        put("bargeInEnabled", bargeInEnabled)
        put("offlineAsrPreferred", offlineAsrPreferred)
        put("batteryAnnouncementsEnabled", batteryAnnouncementsEnabled)
        put("proactiveCalendarAlertsEnabled", proactiveCalendarAlertsEnabled)
        put("proactivePreBriefingMinutes", proactivePreBriefingMinutes)
        put("proactiveBatteryAlertsEnabled", proactiveBatteryAlertsEnabled)
        put("wakeSensitivity", wakeSensitivity)
        put("wakeDetectionTimeoutSeconds", wakeDetectionTimeoutSeconds)
        put("mediaDuckingEnabled", mediaDuckingEnabled)
        put("mediaDuckingPercent", mediaDuckingPercent)
        put("mediaDuckingMusic", mediaDuckingMusic)
        put("mediaDuckingPodcasts", mediaDuckingPodcasts)
        put("mediaDuckingVideos", mediaDuckingVideos)
        put("mediaDuckingCalls", mediaDuckingCalls)
        put("waveformMode", waveformMode)
        put("waveformFrequencyBands", waveformFrequencyBands)
        put("animationIntensity", animationIntensity.toDouble())
        put("requireConfirmationForMediumRisk", requireConfirmationForMediumRisk)
        put("requireConfirmationForHighRisk", requireConfirmationForHighRisk)
        put("activeProfileId", activeProfileId)
        put("wakeAcknowledgment", wakeAcknowledgment)
        put("dailyBriefingEnabled", dailyBriefingEnabled)
        put("dailyBriefingTime", dailyBriefingTime)
        put("lockScreenVoiceEnabled", lockScreenVoiceEnabled)
        put("whatsAppAutoReturnEnabled", whatsAppAutoReturnEnabled)
        put("acousticFeedbackEnabled", acousticFeedbackEnabled)
        put("hapticFeedbackEnabled", hapticFeedbackEnabled)
        put("bluetoothScoRoutingEnabled", bluetoothScoRoutingEnabled)
        put("headsetHookActivationEnabled", headsetHookActivationEnabled)
        put("wearableSyncEnabled", wearableSyncEnabled)
        put("floatingOverlayEnabled", floatingOverlayEnabled)
        put("ttsProvider", ttsProvider)
        put("ttsVoice", ttsVoice)
        put("callScreeningEnabled", callScreeningEnabled)
        put("clipboardIntelligenceEnabled", clipboardIntelligenceEnabled)
        put("deviceUnlockPin", deviceUnlockPin)
        put("autoLearnEnabled", autoLearnEnabled)
    }

    private fun jsonToAppSettings(json: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = json.optString("theme", d.theme),
            notificationsEnabled = json.optBoolean("notificationsEnabled", d.notificationsEnabled),
            defaultTimeoutMs = json.optLong("defaultTimeoutMs", d.defaultTimeoutMs),
            maxRetryAttempts = json.optInt("maxRetryAttempts", d.maxRetryAttempts),
            logVerbosity = json.optString("logVerbosity", d.logVerbosity),
            wakeWord = json.optString("wakeWord", d.wakeWord),
            preferredLlmModel = json.optString("preferredLlmModel", d.preferredLlmModel),
            developerMode = json.optBoolean("developerMode", d.developerMode),
            continuousConversation = json.optBoolean("continuousConversation", d.continuousConversation),
            followUpTimeoutSeconds = json.optInt("followUpTimeoutSeconds", d.followUpTimeoutSeconds),
            bargeInEnabled = json.optBoolean("bargeInEnabled", d.bargeInEnabled),
            offlineAsrPreferred = json.optBoolean("offlineAsrPreferred", d.offlineAsrPreferred),
            batteryAnnouncementsEnabled = json.optBoolean("batteryAnnouncementsEnabled", d.batteryAnnouncementsEnabled),
            proactiveCalendarAlertsEnabled = json.optBoolean("proactiveCalendarAlertsEnabled", d.proactiveCalendarAlertsEnabled),
            proactivePreBriefingMinutes = json.optInt("proactivePreBriefingMinutes", d.proactivePreBriefingMinutes),
            proactiveBatteryAlertsEnabled = json.optBoolean("proactiveBatteryAlertsEnabled", d.proactiveBatteryAlertsEnabled),
            wakeSensitivity = json.optString("wakeSensitivity", d.wakeSensitivity),
            wakeDetectionTimeoutSeconds = json.optInt("wakeDetectionTimeoutSeconds", d.wakeDetectionTimeoutSeconds),
            mediaDuckingEnabled = json.optBoolean("mediaDuckingEnabled", d.mediaDuckingEnabled),
            mediaDuckingPercent = json.optInt("mediaDuckingPercent", d.mediaDuckingPercent),
            mediaDuckingMusic = json.optBoolean("mediaDuckingMusic", d.mediaDuckingMusic),
            mediaDuckingPodcasts = json.optBoolean("mediaDuckingPodcasts", d.mediaDuckingPodcasts),
            mediaDuckingVideos = json.optBoolean("mediaDuckingVideos", d.mediaDuckingVideos),
            mediaDuckingCalls = json.optBoolean("mediaDuckingCalls", d.mediaDuckingCalls),
            waveformMode = json.optString("waveformMode", d.waveformMode),
            waveformFrequencyBands = json.optInt("waveformFrequencyBands", d.waveformFrequencyBands),
            animationIntensity = json.optDouble("animationIntensity", d.animationIntensity.toDouble()).toFloat(),
            requireConfirmationForMediumRisk = json.optBoolean("requireConfirmationForMediumRisk", d.requireConfirmationForMediumRisk),
            requireConfirmationForHighRisk = json.optBoolean("requireConfirmationForHighRisk", d.requireConfirmationForHighRisk),
            activeProfileId = json.optString("activeProfileId", d.activeProfileId),
            wakeAcknowledgment = json.optString("wakeAcknowledgment", d.wakeAcknowledgment),
            dailyBriefingEnabled = json.optBoolean("dailyBriefingEnabled", d.dailyBriefingEnabled),
            dailyBriefingTime = json.optString("dailyBriefingTime", d.dailyBriefingTime),
            lockScreenVoiceEnabled = json.optBoolean("lockScreenVoiceEnabled", d.lockScreenVoiceEnabled),
            whatsAppAutoReturnEnabled = json.optBoolean("whatsAppAutoReturnEnabled", d.whatsAppAutoReturnEnabled),
            acousticFeedbackEnabled = json.optBoolean("acousticFeedbackEnabled", d.acousticFeedbackEnabled),
            hapticFeedbackEnabled = json.optBoolean("hapticFeedbackEnabled", d.hapticFeedbackEnabled),
            bluetoothScoRoutingEnabled = json.optBoolean("bluetoothScoRoutingEnabled", d.bluetoothScoRoutingEnabled),
            headsetHookActivationEnabled = json.optBoolean("headsetHookActivationEnabled", d.headsetHookActivationEnabled),
            wearableSyncEnabled = json.optBoolean("wearableSyncEnabled", d.wearableSyncEnabled),
            floatingOverlayEnabled = json.optBoolean("floatingOverlayEnabled", d.floatingOverlayEnabled),
            ttsProvider = json.optString("ttsProvider", d.ttsProvider),
            ttsVoice = json.optString("ttsVoice", d.ttsVoice),
            callScreeningEnabled = json.optBoolean("callScreeningEnabled", d.callScreeningEnabled),
            clipboardIntelligenceEnabled = json.optBoolean("clipboardIntelligenceEnabled", d.clipboardIntelligenceEnabled),
            deviceUnlockPin = json.optString("deviceUnlockPin", d.deviceUnlockPin),
            autoLearnEnabled = json.optBoolean("autoLearnEnabled", d.autoLearnEnabled)
        )
    }

    private fun ApiConfig.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("authMethod", authMethod.name)
        put("credentialId", credentialId)
        put("apiKeyHeader", apiKeyHeader)
        put("active", active)
        put("timeoutMs", timeoutMs)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun jsonToApiConfig(json: JSONObject): ApiConfig {
        val d = ApiConfig()
        val auth = runCatching { AuthMethod.valueOf(json.optString("authMethod", d.authMethod.name)) }.getOrDefault(d.authMethod)
        return ApiConfig(
            id = json.optString("id", d.id),
            name = json.optString("name", d.name),
            baseUrl = json.optString("baseUrl", d.baseUrl),
            authMethod = auth,
            credentialId = if (json.has("credentialId") && !json.isNull("credentialId")) json.optString("credentialId") else null,
            apiKeyHeader = json.optString("apiKeyHeader", d.apiKeyHeader),
            active = json.optBoolean("active", d.active),
            timeoutMs = json.optLong("timeoutMs", d.timeoutMs),
            createdAt = json.optLong("createdAt", d.createdAt),
            updatedAt = json.optLong("updatedAt", d.updatedAt)
        )
    }

    fun loadVoiceProfile(): com.jarvis.wakeword.UserVoiceProfile {
        val raw = prefs.getString(KEY_VOICE_PROFILE, null) ?: return com.jarvis.wakeword.UserVoiceProfile.DEFAULT
        return try {
            val json = JSONObject(raw)
            com.jarvis.wakeword.UserVoiceProfile(
                isEnrolled = json.optBoolean("isEnrolled", false),
                enrolledAt = json.optLong("enrolledAt", 0L),
                wakeWordPhrase = json.optString("wakeWordPhrase", "Jarvis"),
                sampleCount = json.optInt("sampleCount", 0),
                averageScore = json.optDouble("averageScore", 0.0).toFloat(),
                calibratedThreshold = json.optDouble("calibratedThreshold", 0.65).toFloat(),
                calibratedMinEnergyRms = json.optDouble("calibratedMinEnergyRms", 15.0),
                ambientNoiseRms = json.optDouble("ambientNoiseRms", 12.0)
            )
        } catch (e: Exception) {
            com.jarvis.wakeword.UserVoiceProfile.DEFAULT
        }
    }

    fun saveVoiceProfile(profile: com.jarvis.wakeword.UserVoiceProfile) {
        val json = JSONObject().apply {
            put("isEnrolled", profile.isEnrolled)
            put("enrolledAt", profile.enrolledAt)
            put("wakeWordPhrase", profile.wakeWordPhrase)
            put("sampleCount", profile.sampleCount)
            put("averageScore", profile.averageScore.toDouble())
            put("calibratedThreshold", profile.calibratedThreshold.toDouble())
            put("calibratedMinEnergyRms", profile.calibratedMinEnergyRms)
            put("ambientNoiseRms", profile.ambientNoiseRms)
        }
        prefs.edit().putString(KEY_VOICE_PROFILE, json.toString()).apply()
    }

    fun resetVoiceProfile() {
        prefs.edit().remove(KEY_VOICE_PROFILE).apply()
    }

    companion object {
        private const val KEY_SETTINGS = "settings"
        private const val KEY_APIS = "apis"
        private const val KEY_CUSTOM_VARIABLES = "custom_variables"
        private const val KEY_LOGS = "logs"
        private const val KEY_VOICE_PROFILE = "user_voice_profile"
    }
}
