package com.jarvis.ui.model

import java.util.UUID
import com.jarvis.wakeword.WakeWordModelMetadata

enum class AuthMethod { API_KEY, OAUTH2, BEARER, BASIC }

data class ApiConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New API",
    val baseUrl: String = "",
    val authMethod: AuthMethod = AuthMethod.API_KEY,
    val credentialId: String? = null,
    val apiKeyHeader: String = "Authorization",
    val active: Boolean = true,
    val timeoutMs: Long = 30_000L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AppSettings(
    val theme: String = "system",
    val notificationsEnabled: Boolean = true,
    val reminderNotificationsEnabled: Boolean = true,
    val proactiveAlertsEnabled: Boolean = true,
    val dailyBriefingNotificationsEnabled: Boolean = true,
    val defaultTimeoutMs: Long = 30_000L,
    val maxRetryAttempts: Int = 3,
    val logVerbosity: String = "INFO",
    val wakeWord: String = WakeWordModelMetadata.ACTIVE_OPEN_WAKE_PHRASE,
    val preferredLlmModel: String = "openai/gpt-oss-20b",
    val developerMode: Boolean = false,
    val continuousConversation: Boolean = true,
    val followUpTimeoutSeconds: Int = 6,
    val bargeInEnabled: Boolean = true,
    val offlineAsrPreferred: Boolean = true,
    val batteryAnnouncementsEnabled: Boolean = true,
    val proactiveCalendarAlertsEnabled: Boolean = true,
    val proactiveBatteryAlertsEnabled: Boolean = true,
    val proactivePreBriefingMinutes: Int = 15,
    val autoMediaControlEnabled: Boolean = true,
    val speedControlEnabled: Boolean = true,
    val debounceIntervalMs: Long = 60_000L, // default 1 minute debounce
    val wakeSensitivity: String = "BALANCED",
    val wakeDetectionTimeoutSeconds: Int = 8,
    val mediaDuckingEnabled: Boolean = true,
    val mediaDuckingPercent: Int = 45,
    val mediaDuckingMusic: Boolean = true,
    val wakeAcknowledgment: String = "CHIME",
    val dailyBriefingEnabled: Boolean = true,
    val dailyBriefingTime: String = "08:00",
    val lockScreenVoiceEnabled: Boolean = true,
    val whatsAppAutoReturnEnabled: Boolean = true,
    val acousticFeedbackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val bluetoothScoRoutingEnabled: Boolean = true,
    val headsetHookActivationEnabled: Boolean = true,
    val wearableSyncEnabled: Boolean = true,
    val floatingOverlayEnabled: Boolean = false,
    val ttsProvider: String = "android",
    val ttsVoice: String = "Fritz-PlayAI",
    val callScreeningEnabled: Boolean = true,
    val clipboardIntelligenceEnabled: Boolean = true,
    val deviceUnlockPin: String = "",
    val autoLearnEnabled: Boolean = true,
    val autopilotEnabled: Boolean = false,
    val autopilotTimeoutMs: Long = 30_000L,
    val emotionalPresentationEnabled: Boolean = true,
    val humorEnabled: Boolean = true,
    val voiceExpressionEnabled: Boolean = true,
    val uiEmotionAnimationsEnabled: Boolean = true,
    val sentimentConfidenceThreshold: Float = 0.65f,
    val reducedMotionEnabled: Boolean = false,
    val jciEnabled: Boolean = false,
    val jciShadow: Boolean = true,
    val jciProactive: Boolean = false,
    val backendServerEnabled: Boolean = true,
    val backendServerUrl: String = "https://android-jarvis.onrender.com",
    val backendAuthToken: String = "",
    val backendFallbackToDirect: Boolean = true
)

fun String.safeDisplay(): String = replace(Regex("[\\r\\n\\t]"), " ").trim()
