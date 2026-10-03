# Project Development Phases & Roadmap - Android J.A.R.V.I.S.

## Overview
Android J.A.R.V.I.S. development is structured into incremental, verified phases to ensure system stability, low latency, and zero regression.

---

## Phase Breakdown

### Phase 1: Core Voice Pipeline & Wake Engine (Completed)
- **Milestones**:
  - Local keyword spotter (`"Jarvis"`) with hybrid MFCC classifier (`WakeWordDetector.kt`).
  - Biometric owner voice enrollment & verification (`UserVoiceProfile.kt`).
  - Continuous conversation listening windows and audio ducking (`MediaDuckingTest.kt`).
  - Dual TTS engine integration (`GroqTtsEngine` + `AndroidTtsEngine`).

### Phase 2: Switchboard, Hardware Controls & Deep Tools (Completed)
- **Milestones**:
  - System switchboard tool (`SystemSwitchboardTool.kt`) for Torch/Flashlight, Wi-Fi, Bluetooth, Volume, Brightness, and Ringer mode.
  - Deep app automation tools: `YouTubePlayTool`, `InstallAppTool`, `MusicPlayTool`, `CameraVisionTool`.
  - Security auditor (`SecurityAuditor.kt`) & Lock-screen execution policy (`LockScreenSecurityPolicy.kt`).

### Phase 3: Accessibility UI Autopilot & Closed-Loop Agent (Completed)
- **Milestones**:
  - Node-based UI navigation service (`JarvisAccessibilityService.kt`).
  - Interactive UI Autopilot (`AppAutopilotTool.kt`) for clicking, typing, scrolling feeds, and multi-step macros.
  - Verification Engine (`VerificationEngine.kt`) ensuring empirical post-condition checks.

### Phase 4: Daily Tasks, Routines & Universal Sessions (Completed)
- **Milestones**:
  - Daily tasks & routine scheduling engine (`SmartRoutineEngine.kt`).
  - Dedicated Routines UI screen (`RoutinesScreen.kt`) with live enable/disable toggles and "+ ADD TASK" dialog.
  - Universal App Session tracking (`WorldState.kt` / `ForegroundAppSession`) for all 50+ registered apps.
  - Smart hardware controls skill (`DeviceHardwareSkill.kt`) & Dynamic app launcher skill (`AppLauncherSkill.kt`).
  - Voice Profile HUD Card on `DashboardScreen.kt`.

### Phase 5: Advanced Multimodal & Local LLM Offline Fallback (Active / Upcoming)
- **Milestones**:
  - Offline local small LLM (On-device GGUF / Llama.cpp fallback).
  - Enhanced spatial screen vision & video understanding.
  - Multi-device WearOS companion sync and remote command relay.
