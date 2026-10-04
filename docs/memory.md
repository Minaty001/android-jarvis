# Codebase State & Memory Snapshot - Android J.A.R.V.I.S.

## 1. Project Health & Build Verification
- **Build Tool**: `./export_release.sh` & `./gradlew testDebugUnitTest`
- **Unit Test Status**: **100% PASS** (518+ unit tests passing).
- **Latest Production Release APK**: `dist/jarvis-v1.0-release.apk` (72MB).

---

## 2. Completed Modules & Active Components Matrix

### 2.1 UI Screens & Layouts (`com.jarvis.ui.screens`)
- `DashboardScreen.kt`: Arc Reactor hero, quick action chips, Voice Profile HUD card, live conversation transcript.
- `RoutinesScreen.kt`: Active routines list, toggle switches, "RUN NOW" buttons, "+ ADD TASK" dialog.
- `SkillsScreen.kt`: Registered skills catalog, capability matrix, test triggers.
- `MemoryScreen.kt`: Working memory inspect, conversation history, task audit logs.
- `LogsScreen.kt`: Live Logcat streaming, task audit logs, telemetry correlation traces.
- `SettingsScreen.kt`: Optimized settings (Wake Engine, Voice Engine, Notification policies, Theme, Reset Defaults).
- `ApiManagerScreen.kt`: LLM provider configuration (Groq, Anthropic, OpenAI, DeepSeek, Gemini).

### 2.2 Core Tools Matrix (`com.jarvis.tools`)
- `SystemSwitchboardTool.kt`: Wi-Fi, Bluetooth, Torch/LED, DND, Volume, Brightness, Ringer mode.
- `AppAutopilotTool.kt`: Accessibility UI navigation, button clicking, text typing, feed scrolling.
- `YouTubePlayTool.kt`: Direct video ID playback, query search, in-app first result click.
- `InstallAppTool.kt`: Play Store package search, direct install intent dispatch.
- `RoutineManageTool.kt`: Create, list, run, enable, disable, and delete automated routines.
- `ListInstalledAppsTool.kt`: AppScanner wrapper for listing, counting, and searching installed apps.
- `FlashlightTool.kt`: CameraManager torch toggle.
- `MusicPlayTool.kt`: YMusic, YouTube Music, Spotify audio playback dispatch.
- `CameraVisionTool.kt`: Optical OCR & live camera feed processing.
- `DailyBriefingTool.kt`: Morning briefing synthesis (Weather, Calendar, Battery, News).

### 2.3 Registered Skills (`com.jarvis.agent.skills`)
- `DeviceHardwareSkill.kt`: Hardware control intent parsing (Torch, Wi-Fi, BT, Vol, Brightness, Silent mode).
- `AppLauncherSkill.kt`: Installed app scanner & launcher intent parsing.
- `RoutineSkill.kt`: Automation routine execution & daily task scheduling.
- `AppControlSkill.kt`: Deep app intent execution.
- `AppAutopilotSkill.kt`: Accessibility feed navigation.
- `SongSearchAndPlaySkill.kt`: Universal audio playback intent resolution.
- `CommunicationSkill.kt` / `WhatsAppMessagingSkill.kt`: WhatsApp direct messaging & background macros.
- `ResearchSkill.kt`, `BrowserSkill.kt`, `VisionSkill.kt`, `TelecomSkill.kt`, `CalendarSkill.kt`, `SecuritySkill.kt`, `FileSkill.kt`, `AutonomousSkill.kt`, `RagSkill.kt`, `LogSkill.kt`, `TimeSkill.kt`, `LocationSkill.kt`.

### 2.4 Active App Session Tracking (`WorldState.kt` / `ForegroundAppSession`)
- Maps all 50+ registered apps in `AppRegistry` (ChatGPT, Gemini, Claude, DeepSeek, Play Store, Google Keep, Samsung Notes, PhonePe, Kotak 811, MobiKwik, Gallery, Settings, Camera, MX Player, Maps, YouTube, WhatsApp, Chrome, Telegram, Spotify, etc.) to active App Sessions with shared UI support.

---

## 3. Quick Reference & Context Restoration
When continuing work in a new session:
1. Refer to `docs/memory.md` for codebase index.
2. Refer to `docs/ARCHITECTURE.md` for system pipeline flow.
3. Refer to `docs/rules.md` for engineering constraints.
4. Verify changes via `./gradlew testDebugUnitTest` and `./export_release.sh`.
