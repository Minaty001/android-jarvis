# Project Requirements Document (PRD) - Android J.A.R.V.I.S.

## 1. Executive Summary & Vision
Android J.A.R.V.I.S. is an autonomous, context-aware voice AI assistant built natively for Android devices. Designed to function as a hands-free personal intelligence system, JARVIS combines local hardware-accelerated wake word detection, real-time voice Activity Detection (VAD), multi-provider LLM reasoning (Groq, Anthropic, OpenAI, DeepSeek, Gemini), and autonomous Accessibility UI navigation (App Autopilot).

---

## 2. Target Users & Operating Environment
- **Primary Audience**: Power users, developers, and hands-free smartphone users seeking an intelligent voice assistant that executes real device actions.
- **Operating System**: Android 8.0 (API level 26) through Android 14+ (API level 34).
- **Form Factors**: Smartphones, foldable devices, tablets, and WearOS companion smartwatches.

---

## 3. Core Feature Requirements

### 3.1 Wake Engine & Audio Contract
- **Continuous Local KWS**: Local keyword spotter monitoring for phrase `"Jarvis"` with hybrid MFCC + neural classification.
- **Biometric Speaker Verification**: Owner voice profile matching with cosine similarity thresholding (90%+ match score).
- **Full-Duplex Voice Pipeline**: Low-latency VAD (Silero/WebRTC) and continuous follow-up listening windows.
- **Audio Ducking**: Automatic media volume ducking when assistant speaks.

### 3.2 Reasoning & Intent Engine (`AgentKernel`)
- **Intent Resolution**: Fast-path local rule resolver for high-frequency actions (<50ms execution latency).
- **Multi-Provider LLM Integration**: Multi-provider planner executing via Groq Llama 3, Anthropic Claude 3.5, OpenAI GPT-4o, and DeepSeek.
- **Closed-Loop Execution**: Autonomous plan execution with strict pre/post-condition verification via `VerificationEngine`.

### 3.3 Deep Device Automation & Switchboard
- **Hardware Controls**: Voice control for Torch/Flashlight, Wi-Fi, Bluetooth, Screen Brightness, Volume, Ringer mode (Silent/Vibrate/Normal), and Battery Saver.
- **Direct App Control**: Deep intent execution for WhatsApp messaging, YouTube video playback, Camera OCR, Spotify/Music playback, and PhonePe/MobiKwik.
- **Universal App Sessions**: Active session state tracking across 50+ registered apps (`WorldState.kt`).

### 3.4 Autonomous UI Autopilot (`AppAutopilotTool`)
- **Accessibility Navigation**: Node-based UI tree traversal via `JarvisAccessibilityService`.
- **Dynamic Interaction**: Auto-clicking buttons, typing text, scrolling feeds (YouTube/Shorts/Instagram), and multi-step UI macros.

### 3.5 Daily Tasks & Smart Routine Automation
- **Time-Scheduled Tasks**: `SmartRoutineEngine` executing daily scheduled briefings, reminders, battery alerts, and Wi-Fi arrival routines.
- **Routines UI Screen**: Glassmorphic dashboard tab (`RoutinesScreen.kt`) for managing active automation rules.

---

## 4. Non-Functional Requirements (NFRs)
- **Honesty Contract**: Never report successful action execution when system APIs or permissions block the change.
- **Performance**: Instant local intent execution under 100ms; cloud LLM fallback under 1.5s.
- **Security & Privacy**: Biometric PIN unlock for sensitive actions; local secure credential storage.
