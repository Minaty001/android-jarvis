package com.jarvis.ui.data

import android.content.Context
import com.jarvis.ui.model.AiPersonalityStyle
import com.jarvis.ui.model.UserProfile
import org.json.JSONObject

class UserProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_user_profile", Context.MODE_PRIVATE)

    fun loadProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return runCatching {
            val json = JSONObject(raw)
            val default = UserProfile()
            val style = runCatching {
                AiPersonalityStyle.valueOf(json.optString("aiPersonalityStyle", default.aiPersonalityStyle.name))
            }.getOrDefault(default.aiPersonalityStyle)

            UserProfile(
                userName = json.optString("userName", default.userName),
                userTitle = json.optString("userTitle", default.userTitle),
                occupation = json.optString("occupation", default.occupation),
                personalNotes = json.optString("personalNotes", default.personalNotes),
                aiPersonaName = json.optString("aiPersonaName", default.aiPersonaName),
                aiPersonalityStyle = style,
                customSystemInstructions = json.optString("customSystemInstructions", default.customSystemInstructions)
            )
        }.getOrDefault(UserProfile())
    }

    fun saveProfile(profile: UserProfile) {
        val json = JSONObject().apply {
            put("userName", profile.userName)
            put("userTitle", profile.userTitle)
            put("occupation", profile.occupation)
            put("personalNotes", profile.personalNotes)
            put("aiPersonaName", profile.aiPersonaName)
            put("aiPersonalityStyle", profile.aiPersonalityStyle.name)
            put("customSystemInstructions", profile.customSystemInstructions)
        }
        prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    companion object {
        private const val KEY_PROFILE = "user_profile_data"
    }
}
