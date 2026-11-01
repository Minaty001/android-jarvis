package com.jarvis.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helper to inspect and configure Jarvis as the Android System Default Digital Assistant.
 */
object AssistantSettingsHelper {

    private const val TAG = "AssistantSettingsHelper"

    /**
     * Checks if Jarvis is currently configured as the system default voice interaction / digital assistant service.
     */
    fun isDefaultAssistant(context: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                        return true
                    }
                }
            }

            val setting = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            if (setting != null) {
                val currentComponent = ComponentName.unflattenFromString(setting)
                if (currentComponent != null && currentComponent.packageName == context.packageName) {
                    return true
                }
            }

            val defaultAssist = Settings.Secure.getString(context.contentResolver, "assistant")
            if (defaultAssist != null) {
                val currentComponent = ComponentName.unflattenFromString(defaultAssist)
                if (currentComponent != null && currentComponent.packageName == context.packageName) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking default assistant status: ${e.message}")
        }
        return false
    }

    /**
     * Creates an [Intent] to open Android's Default Digital Assistant settings screen.
     */
    fun createDefaultAssistantSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens Android's Default Digital Assistant settings screen.
     */
    fun openDefaultAssistantSettings(context: Context): Boolean {
        // Priority 1: If an Activity context, attempt RoleManager with startActivityForResult
        if (context is android.app.Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                    context.startActivityForResult(roleIntent, 1001)
                    return true
                }
            } catch (roleEx: Exception) {
                Log.w(TAG, "RoleManager.createRequestRoleIntent failed: ${roleEx.message}")
            }
        }

        // Priority 2: Direct Voice Input / Assistant Settings intent
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_VOICE_INPUT_SETTINGS failed: ${e.message}")
        }

        // Priority 3: Manage Default Apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                return true
            } catch (fallbackEx: Exception) {
                Log.w(TAG, "ACTION_MANAGE_DEFAULT_APPS_SETTINGS failed: ${fallbackEx.message}")
            }
        }

        // Priority 4: Application details or General Settings
        return try {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetails)
            true
        } catch (e: Exception) {
            try {
                val mainSettings = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(mainSettings)
                true
            } catch (finalEx: Exception) {
                Log.e(TAG, "All assistant settings intents failed: ${finalEx.message}")
                false
            }
        }
    }

    /**
     * Returns a spoken status message describing whether Jarvis is the default digital assistant.
     */
    fun formatStatusMessage(isDefault: Boolean): String {
        return if (isDefault) {
            "Jarvis is configured as your system default digital assistant and active for long-press invocation."
        } else {
            "Jarvis is currently not the default assistant. Say 'Open assistant settings' to configure it in Android Settings."
        }
    }
}
