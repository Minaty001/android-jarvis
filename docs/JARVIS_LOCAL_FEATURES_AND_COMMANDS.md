# 🤖 J.A.R.V.I.S. — Master Local Features, Trained Commands, & System Architecture Catalog

This document is the definitive master catalog of all **local features, functions, subsystems, trained commands, and source code locations** in J.A.R.V.I.S. Android.

---

## 🏗️ 1. Architecture & Subsystem Mapping

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             J.A.R.V.I.S. ANDROID RUNTIME                        │
├────────────────────────────────────────┬─────────────────────────────────────────┤
│ 🎙️ Voice & Acoustic Engine             │ 🧠 Assistant Runtime & Multi-LLM Kernel │
│  • openWakeWord Continuous KWS         │  • Groq / Gemini / OpenRouter / Ollama  │
│  • Full-Duplex Barge-In Interrupt      │  • Multi-Step ReAct Task Planner        │
│  • Bluetooth SCO Earbud Audio Router   │  • Closed-Loop Outcome Verifier         │
├────────────────────────────────────────┼─────────────────────────────────────────┤
│ 📱 Hardware & OS Switchboard           │ 👁️ Vision & Accessibility Autopilot    │
│  • Wi-Fi, Bluetooth, Hotspot, DND, Vol │  • TFLite Object Detection & Vision OCR │
│  • Flashlight & Battery Saver          │  • Node-Based UI Screen Touch Driver    │
│  • Lock-Screen Cyber-Slate Overlay     │  • WhatsApp & Telephony Automation      │
├────────────────────────────────────────┼─────────────────────────────────────────┤
│ ⚡ Autonomous Daemons & Routines       │ 💾 Memory Vault & Local RAG             │
│  • ActivityMonitorEngine (App Tracker) │  • FTS4 SQLite MemoryStore              │
│  • AutoScheduleEngine (Habit Miner)    │  • LangChain4j Local Vector Index       │
│  • SmartRoutineEngine & Macro Workflows│  • Activity Moments Ledger              │
└────────────────────────────────────────┴─────────────────────────────────────────┘
```

---

## 🗂️ 2. Local Features & Functions Catalog (By Domain)

### A. System Switchboard & Hardware Control
* **System Switchboard**: Intelligently toggles device hardware (Wi-Fi, Bluetooth, Hotspot, Do Not Disturb, Flashlight) based on API level capabilities (`API 33+` fallback panel opening vs programmatic toggle).
* **Device Settings**: Adjusts media volume, ringer volume, screen brightness, and system orientation modes.
* **Flashlight Control**: Toggles rear LED camera flash programmatically via `CameraManager`.
* **Battery Saver & Monitoring**: Inspects battery percentage, charging state, temperature, and triggers low-battery saver protocols.

### B. Voice & Acoustic Engine
* **Continuous Wake-Word Detection**: Runs on-device `openWakeWord` classifier heads (`"Hey Jarvis"`, `"Jarvis"`) over continuous PCM stream without cloud dependency.
* **Full-Duplex Acoustic Barge-In**: Instantaneously interrupts TTS playback (<50ms cutoff) when human speech is detected.
* **Bluetooth SCO Audio Router**: Routes microphone input and speaker output seamlessly through Bluetooth headsets/earbuds.
* **Live Conversation Mode**: Zero-wake-word continuous turn-taking conversation loop.

### C. App Autopilot & Accessibility Screen Automation
* **App Autopilot**: Autonomous UI screen navigator using Android `AccessibilityService`. Inspects screen node hierarchy, performs physical taps, scrolls, and types text into third-party apps.
* **App Launcher & Closer**: Launches any installed application by label or package name using `AppScanner` dynamic package resolution; closes running background apps.
* **Global Actions**: Performs hardware-level back, home, recent apps, notifications shade, and lock screen actions.

### D. Media & Entertainment
* **Spotify Control**: Full playback control (play, pause, next, previous, search track/artist/playlist, track session history recording).
* **YouTube Playback**: Resolves YouTube video query IDs or direct URLs for immediate in-app or browser video playback.
* **Music Player Control**: Standard Android `MediaSession` control for local music players (play, pause, skip, rewind).

### E. Messaging & Communication
* **WhatsApp Automation**: Sends direct WhatsApp/WhatsApp Business messages, reads unread notifications, performs auto-replies (driving, meeting, busy modes), and digests incoming messages.
* **Telephony & Phone Calls**: Initiates phone calls, answers/declines incoming calls, reads caller ID, and screens unwanted calls.
* **Message Digest Reader**: Summarizes unread SMS and WhatsApp messages without displaying private notifications.

### F. Memory, RAG & Knowledge Vault
* **FTS4 MemoryStore**: SQLite full-text search database storing user preferences, structured facts, task artifacts, and episodic events.
* **LangChain4j RAG Vault**: Embeds and indexes local document files (`.txt`, `.md`, `.pdf`) for local retrieval-augmented generation.
* **Memory Decay & Garbage Collection**: Automatically decays memory importance scores over time and archives obsolete memories.

### G. Autonomous Daemon & Background Activity Monitor
* **ActivityMonitorEngine**: Background service daemon that tracks foreground app sessions, duration of use (in seconds), and active media playback, saving them as `ActivityMoment` entries.
* **AutoScheduleEngine**: Analyzes recorded activity moments to discover morning and daily habits, automatically synthesizing `SmartRoutine` schedules.
* **Proactive Context Engine**: Monitors charging events, network switches, and calendar events to deliver proactive voice announcements.

### H. Smart Routines & Macro Workflows
* **SmartRoutineEngine**: Event-driven and time-scheduled automation engine (e.g. Bedtime Routine, Low Battery Saver, Morning Briefing).
* **Macro Workflow Engine**: Records and executes multi-step UI action sequences across apps.

### I. Vision, Camera & Screen Inspection
* **Camera Vision Perception**: Captures photo frames via `Camera2` API and runs on-device TFLite MobileNet object detection and OCR.
* **Screen Vision Inspection**: Captures screen content and analyzes UI layout nodes.

---

## 🎙️ 3. Trained & Supported Commands Catalog

| Domain | Trained Command / Trigger Utterance | Handling Tool / Component | Source File Location |
| :--- | :--- | :--- | :--- |
| **System** | *"Turn on flashlight"* / *"Torch off"* | `FlashlightTool` | `app/src/main/java/com/jarvis/tools/FlashlightTool.kt` |
| **System** | *"Turn off Wi-Fi"* / *"Open Bluetooth settings"* | `SystemSwitchboardTool` | `app/src/main/java/com/jarvis/tools/SystemSwitchboardTool.kt` |
| **System** | *"Set volume to 80%"* / *"Set brightness to 50%"* | `DeviceSettingsTool` | `app/src/main/java/com/jarvis/tools/DeviceSettingsTool.kt` |
| **System** | *"Check battery percentage"* / *"Battery status"* | `BatteryStatusTool` | `app/src/main/java/com/jarvis/tools/BatteryStatusTool.kt` |
| **Apps** | *"Open YouTube"* / *"Launch WhatsApp"* | `OpenAppTool` | `app/src/main/java/com/jarvis/tools/OpenAppTool.kt` |
| **Apps** | *"Close Spotify"* / *"Close all apps"* | `CloseAppTool`, `AppsCloseAllTool` | `app/src/main/java/com/jarvis/tools/AppsCloseAllTool.kt` |
| **Apps** | *"List installed apps"* | `ListInstalledAppsTool` | `app/src/main/java/com/jarvis/tools/ListInstalledAppsTool.kt` |
| **Media** | *"Play Lofi Beats on Spotify"* | `SpotifyControlTool` | `app/src/main/java/com/jarvis/tools/SpotifyControlTool.kt` |
| **Media** | *"Play Interstellar trailer on YouTube"* | `YouTubePlayTool` | `app/src/main/java/com/jarvis/tools/YouTubePlayTool.kt` |
| **Media** | *"Pause music"* / *"Next track"* / *"Resume playback"* | `MediaPlaybackControlTool` | `app/src/main/java/com/jarvis/tools/MediaPlaybackControlTool.kt` |
| **Messaging** | *"Send WhatsApp message to Alex saying I will be late"* | `WhatsAppTool` | `app/src/main/java/com/jarvis/tools/WhatsAppTool.kt` |
| **Messaging** | *"Read my unread messages"* | `MessageReaderTool` | `app/src/main/java/com/jarvis/tools/MessageReaderTool.kt` |
| **Calls** | *"Call Mom"* / *"Answer call"* / *"Decline call"* | `TelephonyTool` | `app/src/main/java/com/jarvis/tools/TelephonyTool.kt` |
| **Memory** | *"Remember that my coffee preference is espresso"* | `MemoryTool` (`NoteTool`) | `app/src/main/java/com/jarvis/tools/MemoryTool.kt` |
| **Memory** | *"What is my coffee preference?"* | `MemoryTool` (`SearchMemoryTool`) | `app/src/main/java/com/jarvis/tools/MemoryTool.kt` |
| **Knowledge** | *"Search my notes for project roadmap"* | `RagQueryTool` | `app/src/main/java/com/jarvis/tools/RagQueryTool.kt` |
| **Knowledge** | *"Index document /sdcard/notes.txt"* | `RagIndexTool` | `app/src/main/java/com/jarvis/tools/RagIndexTool.kt` |
| **Automation** | *"Start bedtime routine"* / *"Create low battery routine"* | `RoutineManageTool` | `app/src/main/java/com/jarvis/tools/RoutineManageTool.kt` |
| **Automation** | *"Run macro record_whatsapp_story"* | `MacroWorkflowTool` | `app/src/main/java/com/jarvis/tools/MacroWorkflowTool.kt` |
| **Autopilot** | *"Click on the search bar in Instagram"* | `AppAutopilotTool`, `UiTools` | `app/src/main/java/com/jarvis/tools/AppAutopilotTool.kt` |
| **Vision** | *"What do you see through camera?"* | `CameraVisionTool` | `app/src/main/java/com/jarvis/tools/CameraVisionTool.kt` |
| **Vision** | *"Read text on screen"* / *"Inspect screen"* | `ScreenVisionTool` | `app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt` |
| **Utilities** | *"Calculate 450 * 12.5"* | `CalculatorTool` | `app/src/main/java/com/jarvis/tools/CalculatorTool.kt` |
| **Utilities** | *"What's the weather in Tokyo?"* | `WeatherTool` | `app/src/main/java/com/jarvis/tools/WeatherTool.kt` |
| **Utilities** | *"Translate Hello to Spanish"* | `TranslatorTool` | `app/src/main/java/com/jarvis/tools/TranslatorTool.kt` |
| **Utilities** | *"Set alarm for 7 AM"* / *"What time is it?"* | `ClockTool` | `app/src/main/java/com/jarvis/tools/ClockTool.kt` |
| **Utilities** | *"Take a quick note: buy groceries"* | `QuickNotesTool` | `app/src/main/java/com/jarvis/tools/QuickNotesTool.kt` |
| **Utilities** | *"Remind me to call John in 30 minutes"* | `ReminderSchedulerTool` | `app/src/main/java/com/jarvis/tools/ReminderSchedulerTool.kt` |
| **Utilities** | *"Navigate to Central Park"* | `NavigationTool` | `app/src/main/java/com/jarvis/tools/NavigationTool.kt` |
| **Utilities** | *"Convert 100 USD to EUR"* | `UnitCurrencyConverterTool` | `app/src/main/java/com/jarvis/tools/UnitCurrencyConverterTool.kt` |
| **Utilities** | *"Give me my daily briefing"* | `DailyBriefingTool` | `app/src/main/java/com/jarvis/tools/DailyBriefingTool.kt` |
| **Utilities** | *"Deep research quantum computing"* | `DeepResearchTool` | `app/src/main/java/com/jarvis/tools/DeepResearchTool.kt` |
| **System Info** | *"Show system logs"* / *"Read logcat"* | `LogsTool` | `app/src/main/java/com/jarvis/tools/LogsTool.kt` |
| **System Info** | *"Run security audit"* | `SecurityAuditorTool` | `app/src/main/java/com/jarvis/tools/SecurityAuditorTool.kt` |
| **System Info** | *"Inspect database stats"* | `JarvisDbTool` | `app/src/main/java/com/jarvis/tools/JarvisDbTool.kt` |

---

## 📍 4. Complete Source Code Location Index

### Core Voice Engine & Acoustic Subsystem
* `com.jarvis.voice.VoiceEngine` $\to$ [`app/src/main/java/com/jarvis/voice/VoiceEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/voice/VoiceEngine.kt)
* `com.jarvis.voice.AndroidTtsEngine` $\to$ [`app/src/main/java/com/jarvis/voice/AndroidTtsEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/voice/AndroidTtsEngine.kt)
* `com.jarvis.voice.AndroidSpeechRecognizerEngine` $\to$ [`app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt)
* `com.jarvis.voice.LiveConversationController` $\to$ [`app/src/main/java/com/jarvis/voice/LiveConversationController.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/voice/LiveConversationController.kt)
* `com.jarvis.voice.BargeInDetector` $\to$ [`app/src/main/java/com/jarvis/voice/BargeInDetector.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/voice/BargeInDetector.kt)
* `com.jarvis.wakeword.WakeWordEngine` $\to$ [`app/src/main/java/com/jarvis/wakeword/DsCnnWakeWordDetector.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/wakeword/DsCnnWakeWordDetector.kt)

### Autonomous Daemons & Monitoring
* `com.jarvis.autonomous.ActivityMonitorEngine` $\to$ [`app/src/main/java/com/jarvis/autonomous/ActivityMonitorEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/autonomous/ActivityMonitorEngine.kt)
* `com.jarvis.routine.AutoScheduleEngine` $\to$ [`app/src/main/java/com/jarvis/routine/AutoScheduleEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/routine/AutoScheduleEngine.kt)
* `com.jarvis.autonomous.AutonomousDaemon` $\to$ [`app/src/main/java/com/jarvis/autonomous/AutonomousDaemon.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/autonomous/AutonomousDaemon.kt)
* `com.jarvis.context.ProactiveContextEngine` $\to$ [`app/src/main/java/com/jarvis/context/ProactiveContextEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/context/ProactiveContextEngine.kt)

### Accessibility & Screen Automation Drivers
* `com.jarvis.accessibility.JarvisAccessibilityService` $\to$ [`app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt)
* `com.jarvis.tools.AppAutopilotTool` $\to$ [`app/src/main/java/com/jarvis/tools/AppAutopilotTool.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/tools/AppAutopilotTool.kt)
* `com.jarvis.tools.UiTools` $\to$ [`app/src/main/java/com/jarvis/tools/UiTools.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/tools/UiTools.kt)

### Memory & Knowledge Vault
* `com.jarvis.memory.MemoryStore` $\to$ [`app/src/main/java/com/jarvis/memory/MemoryStore.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/memory/MemoryStore.kt)
* `com.jarvis.memory.AugmentedMemoryPipeline` $\to$ [`app/src/main/java/com/jarvis/memory/AugmentedMemoryPipeline.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/memory/AugmentedMemoryPipeline.kt)
* `com.jarvis.storage.VectorDbManager` $\to$ [`app/src/main/java/com/jarvis/storage/VectorDbManager.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/storage/VectorDbManager.kt)
* `com.jarvis.rag.RagRetriever` $\to$ [`app/src/main/java/com/jarvis/rag/RagRetriever.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/rag/RagRetriever.kt)
* `com.jarvis.data.JarvisDatabase` $\to$ [`app/src/main/java/com/jarvis/data/JarvisDatabase.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/data/JarvisDatabase.kt)

### Smart Routines & Macro Engines
* `com.jarvis.routine.SmartRoutineEngine` $\to$ [`app/src/main/java/com/jarvis/routine/SmartRoutineEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/routine/SmartRoutineEngine.kt)
* `com.jarvis.macro.MacroWorkflowEngine` $\to$ [`app/src/main/java/com/jarvis/macro/MacroWorkflowEngine.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/macro/MacroWorkflowEngine.kt)

### Tool Registry & Execution Layer
* `com.jarvis.tools.ToolRegistry` $\to$ [`app/src/main/java/com/jarvis/tools/ToolRegistry.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/tools/ToolRegistry.kt)
* `com.jarvis.runtime.AssistantRuntime` $\to$ [`app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt)
* `com.jarvis.service.JarvisForegroundService` $\to$ [`app/src/main/java/com/jarvis/service/JarvisForegroundService.kt`](file:///home/shanu/Pictures/android-jarvis-main/app/src/main/java/com/jarvis/service/JarvisForegroundService.kt)
