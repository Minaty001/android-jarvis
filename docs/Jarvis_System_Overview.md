# JARVIS Android System Overview

This document provides a comprehensive overview of all functions, features, systems, settings, available actions, and tools/tasks within the Jarvis Android application codebase.

---

## 1. System Architecture & Core Services

Jarvis integrates deeply into the Android OS using a suite of background services and broadcast receivers to achieve full autonomy and contextual awareness.

*   **JarvisForegroundService (Microphone):** The core always-on engine. It maintains a persistent notification and routes the microphone to `WakeWordEngine`; validated openWakeWord TFLite models (`melspectrogram.tflite`, `embedding_model.tflite`, `hey_jarvis_v0.1.tflite`) plus `openwakeword_models.json` must be installed for wake detection. Without them, the detector reports unavailable.
*   **JarvisAccessibilityService:** The autonomous UI navigator. It reads the screen (View hierarchy) and performs UI actions (clicks, swipes, scrolling, text entry) on behalf of the user to execute "App Autopilot" macros.
*   **JarvisVoiceInteractionService & JarvisAssistActivity:** Replaces Google Assistant as the system's default Digital Assistant. It handles long-press power button, home button long-press, and lock-screen voice invocation.
*   **JarvisRecognitionService:** Integrates Jarvis as the default Voice Typing engine for Android keyboards.
*   **JarvisNotificationListenerService:** Intercepts incoming notifications to read messages aloud, analyze urgency, and perform auto-replies (e.g., WhatsApp).
*   **JarvisCallScreeningService:** Intercepts incoming phone calls for spam filtering, caller ID analysis, and potential auto-answering or blocking.
*   **JarvisFloatingOverlayService & OverlayTileService:** Provides a visual "Orb" overlay that floats above other apps, and a Quick Settings tile for easy toggling.
*   **Broadcast Receivers:**
    *   `JarvisBootReceiver`: Auto-starts Jarvis on device boot.
    *   `DailyBriefingReceiver`: Triggers morning summaries.
    *   `PhoneCallReceiver` & `SmsReceiver`: Real-time telecom event handling.
    *   `WearableCompanionReceiver`: Handles Bluetooth and Smartwatch integration commands.

---

## 2. Core Features & Capabilities

*   **Continuous Conversation & Barge-in:** Jarvis keeps listening after speaking to allow follow-up questions without repeating the wake word. You can interrupt Jarvis mid-sentence (barge-in).
*   **Media Ducking:** Intelligently lowers the volume of music, podcasts, or videos when Jarvis is speaking or listening, and restores it afterward.
*   **Lock Screen Intelligence:** Safely executes voice commands while the device is locked, bypassing PIN requirements only for non-sensitive actions (controlled by PolicyEngine).
*   **Skill Learning (Procedural Memory):** Automatically remembers sequences of UI actions. If Jarvis successfully navigates an app once, it saves the steps as a reusable "macro" for future use.
*   **Proactive Alerts & Daily Briefing:** Can interrupt the user with critical contextual alerts (e.g., low battery, upcoming calendar events) and provides a scheduled daily briefing.
*   **Bluetooth & Wearable Routing:** Routes audio seamlessly through Bluetooth headsets and syncs commands from WearOS companions.

---

## 3. Agent Actions & Tools (Skills)

The LLM Agent powering Jarvis has access to an extensive "Tool Registry". These tools allow the AI to interact with the device and the internet.

### Device Control & Automation
*   **App Autopilot & Macro Workflow:** Opens apps and autonomously clicks, swipes, and enters text to achieve a goal.
*   **Foreground App Sessions:** Tracks WhatsApp, Instagram, Chrome, Telegram, and YouTube from accessibility window events. Follow-up scroll and search commands use the active app, while screen reading inspects that same foreground screen. Enable Jarvis Accessibility Service in Android settings for session detection and in-app UI controls; YouTube playback commands remain available.
*   **DeviceSettingsTool:** Reads media-volume and ringer status; changes media volume, mute state, and ringer mode; opens Jarvis's app permission settings when requested.
*   **FlashlightTool & BatteryStatusTool:** Hardware control and monitoring.
*   **SystemSwitchboardTool:** Deep integration for hardware state changes.

### Vision, Context & Search
*   **ScreenVisionTool:** Takes a screenshot, parses the UI using an OCR/Vision model, and understands what is currently on the screen.
*   **CameraVisionTool:** Captures photos to identify objects, text, or surroundings.
*   **LocationTool & NavigationTool:** Retrieves GPS coordinates and initiates Google Maps navigation.
*   **DeepResearchTool:** Performs web searches and crawls webpages to answer complex queries.

### Communication & Telephony
*   **MessageReaderTool & NotificationsTool:** Reads WhatsApp, SMS, and system notifications.
*   **TelephonyTool:** Places phone calls, answers, or hangs up.
*   **WhatsApp Automation:** Deep integration to send messages, read chats, and auto-reply.

### Organization & Productivity
*   **CalendarTool:** Checks schedule and adds events.
*   **ReminderSchedulerTool:** Sets alarms and timers.
*   **JarvisDbTool & FileManagerTool:** Saves notes/memories to a local database and manages files on the SD card.
*   **UnitCurrencyConverterTool:** Handles live conversions.
*   **MediaPlaybackControlTool & SpotifyControlTool:** Plays, pauses, and searches for music/podcasts.

---

## 4. Settings & Preferences

Users can customize Jarvis heavily via the `UiPreferencesStore` and Settings Screen.

*   **Wake Word & Voice Dataset:**
    *   Supported classifier heads include upstream `Hey Jarvis` and custom trained `Jarvis`.
    *   Enrollment stores real 16 kHz mono PCM16 WAVs and dataset provenance. It does not train weights or set the wake threshold.
    *   Offline openWakeWord training generates the classifier head and packages it via `scripts/package_openwakeword.py`. Manifest validation thresholds are authoritative.
    *   Optional owner verification is a separate speaker-identity check. It is disabled by default; if explicitly required, an unavailable model or embedding rejects the wake event.
*   **Android Permission Shortcuts:** The Settings screen can open Android's default-assistant, overlay, accessibility, and notification-access settings. Users enable those permissions in Android settings.
*   **Safety & Security Rules:**
    *   `requireConfirmationForMediumRisk` / `HighRisk`: Forces Jarvis to ask permission before taking actions like sending emails or deleting files.
    *   `lockScreenVoiceEnabled`: Toggles lock-screen functionality.
    *   `deviceUnlockPin`: Optional PIN caching for autonomous unlocking (if permitted by policy).
*   **Conversational AI Settings:**
    *   `preferredLlmModel`: Switch between different AI models (e.g., Groq, Gemini, local Ollama).
    *   `ttsProvider` & `ttsVoice`: Configures the Text-to-Speech voice and engine.
    *   `offlineAsrPreferred`: Forces on-device speech recognition for privacy.
*   **Behavioral Toggles:**
    *   `mediaDuckingEnabled`, `continuousConversation`, `bargeInEnabled`, `wearableSyncEnabled`.
*   **Developer & Debug:**
    *   `developerMode`: Enables deep logging and raw LLM prompt visibility.
    *   `logVerbosity`: Controls the amount of debug output.

---

## 4. Newly Integrated Autonomous Subsystems (Recent Updates)

*   **FinancialTrackerEngine & Room Database**: Processes SMS events locally via Regex to track banking income and expenditure without external API calls.
*   **ProactiveMessagingEngine & ConversationManager**: Enables continuous, multi-turn background SMS interaction where Jarvis waits for human replies before sending follow-ups.
*   **JarvisSecurityManager**: An overarching security auditor that masks PII locally before sending prompts to the cloud and enforces strict consent for destructive commands.
*   **PassiveMemoryEngine & ScreenContextAnalyzer**: Autonomously parses foreground app text and user habits to log a passive context trail in the secure SQLite backend.
*   **Comprehensive Permissions Manager**: Integrated directly into `SettingsScreen.kt`, offering a 1-tap Hub to manage all 11+ standard and special OS permissions.
