# Application Architecture & System Flow - Android J.A.R.V.I.S.

## 1. High-Level System Architecture

```
                                 ┌───────────────────────────┐
                                 │   JarvisForegroundService │
                                 └─────────────┬─────────────┘
                                               │
               ┌───────────────────────────────┼───────────────────────────────┐
               ▼                               ▼                               ▼
    ┌────────────────────┐          ┌────────────────────┐          ┌────────────────────┐
    │    VoiceEngine     │          │    AgentKernel     │          │   WorldState.kt    │
    │ (KWS, VAD, TTS)    │          │ (Planner, Verifier)│          │ (Sensors, Sessions)│
    └──────────┬─────────┘          └──────────┬─────────┘          └──────────┬─────────┘
               │                               │                               │
               └───────────────────────────────┼───────────────────────────────┘
                                               ▼
                                  ┌──────────────────────────┐
                                  │   Skill & Tool Registry  │
                                  │  (50+ Tools / 20+ Skills)│
                                  └────────────┬─────────────┘
                                               │
               ┌───────────────────────────────┼───────────────────────────────┐
               ▼                               ▼                               ▼
    ┌────────────────────┐          ┌────────────────────┐          ┌────────────────────┐
    │ System Switchboard │          │ AppAutopilotTool   │          │ SmartRoutineEngine │
    │ (Wi-Fi, Torch, Vol)│          │(Accessibility UI)  │          │ (Scheduled Tasks)  │
    └────────────────────┘          └────────────────────┘          └────────────────────┘
```

---

## 2. Tech Stack & Dependencies

- **Language & Runtime**: Kotlin 1.9+ / JVM 17.
- **UI Framework**: Native Android AndroidX, Material 3, Cyber-Slate Glassmorphic custom views (`Ui.kt`).
- **Concurrency**: Kotlin Coroutines (`CoroutineScope`, `Dispatchers.Main` / `Default`, `Job`, `StateFlow`).
- **Database & Persistence**: Room SQLite DB (`JarvisDatabase.kt`), SharedPreferences (`UiPreferencesStore.kt`), Encrypted SharedPrefs (`SecureCredentialStore.kt`).
- **Voice & Speech**:
  - Speech Recognition: `AndroidSpeechEngine` (Android `SpeechRecognizer` API + offline ASR fallback).
  - Neural TTS: `GroqTtsEngine` (PlayAI / Groq API) + `AndroidTtsEngine` fallback.
  - Keyword Spotting: Hybrid MFCC + Neural KWS (`WakeWordDetector.kt`).
  - VAD: `RollingPcmBuffer.kt` & `JarvisWakeGate.kt`.
- **Accessibility & Autopilot**: `JarvisAccessibilityService.kt` traversing Android `AccessibilityNodeInfo` tree.

---

## 3. Directory & Folder Structure

```
app/src/main/java/com/jarvis/
├── accessibility/       # Accessibility Service & Node Navigation
├── agent/               # AgentKernel, Loop Guard, Working Memory
│   └── skills/          # Skill interface & 20+ skill implementations
├── ai/                  # Multi-Provider LLM Clients (Groq, Claude, GPT-4, DeepSeek)
├── automation/          # Daily Briefing Scheduler & Receivers
├── clipboard/           # Clipboard Intelligence Engine
├── context/             # Proactive Context Engine
├── data/                # Room DB Entities, Repositories, DAOs
├── execution/           # VerificationEngine & Result Contracts
├── foundation/          # Policy Engine, Autonomy Policy, WorldState
├── logs/                # Telemetry Log Streamer & Logger
├── macro/               # Macro Workflow Engine & Custom Step Runners
├── media/               # Media Session Manager & Audio Ducking
├── reminder/            # Alarm Receiver & Reminder System
├── routine/             # SmartRoutineEngine & Custom Routine Repositories
├── runtime/             # AssistantRuntime lifecycle wiring
├── security/            # Security Auditor & Lock Screen Security Policy
├── service/             # JarvisForegroundService & Boot Receiver
├── tools/               # 50+ Execution Tools (SystemSwitchboard, YouTubePlay, AppAutopilot)
├── ui/                  # Cyber-Slate Glassmorphic UI
│   ├── components/      # Ui.kt design tokens & ResponsiveNav.kt
│   ├── data/            # UiPreferencesStore.kt
│   ├── dialogs/         # Voice Enrollment & Routine Dialogs
│   └── screens/         # Dashboard, Routines, Skills, Memory, Logs, Settings
├── voice/               # VoiceEngine, VAD, KWS, TTS Engines
├── wakeword/            # Biometric Voice Profile & MFCC Extractor
└── wear/                # WearOS Companion Dispatcher
```

---

## 4. Key Subsystem Workflows

### 4.1 Voice Command Pipeline
1. `JarvisWakeGate` detects wake phrase `"Jarvis"`.
2. `AndroidSpeechEngine` listens to user utterance.
3. `VoiceEngine` routes transcript to `AgentKernel`.
4. `AgentKernel` selects matching `Skill` or queries `IntentResolver` fast-path.
5. Action executes via target `Tool` registered in `ToolRegistry`.
6. `VerificationEngine` checks system state and reports empirical outcome.
7. Response is spoken via `GroqTtsEngine` or `AndroidTtsEngine`.
