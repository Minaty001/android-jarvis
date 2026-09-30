# 🤖 J.A.R.V.I.S. — Autonomous Android AI Super-Agent & Voice Assistant

<div align="center">

[![Build Status](https://img.shields.io/badge/Build-Passing-00E676?style=for-the-badge&logo=android&logoColor=white)](https://github.com/1209codex/android-jarvis)
[![Unit Tests](https://img.shields.io/badge/Unit%20Tests-503%20Passed-00E676?style=for-the-badge&logo=pytest&logoColor=white)](https://github.com/1209codex/android-jarvis)
[![Platform](https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-00E5FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/1209codex/android-jarvis)
[![Google Stitch](https://img.shields.io/badge/Design-Google%20Stitch%20Material%203-7B1FA2?style=for-the-badge&logo=materialdesign&logoColor=white)](https://github.com/1209codex/android-jarvis)
[![Release APK](https://img.shields.io/badge/Release%20APK-9.7%20MB-blue?style=for-the-badge&logo=googleplay&logoColor=white)](dist/jarvis-v1.1-release.apk)

<p align="center">
  <b>Closed-loop Android AI super-agent featuring continuous openWakeWord activation, multi-provider LLM reasoning, deep OS-level accessibility automation, Bluetooth SCO earbud routing, WearOS synchronization, and Google Stitch Material 3 cyber-slate design.</b>
</p>

</div>

---

## 📌 Table of Contents
- [📱 Visual Showcase (Google Stitch Material 3)](#-visual-showcase-google-stitch-material-3)
- [💎 Navigation Tabs & App Screens](#-navigation-tabs--app-screens)
- [📖 Feature Guide & Capabilities](#-feature-guide--capabilities)
  - [1. 🎙️ Continuous openWakeWord & Acoustic Engine](#1-️-continuous-openwakeword--acoustic-engine)
  - [2. 🎛️ Bluetooth Earbud SCO & WearOS Sync](#2-️-bluetooth-earbud-sco--wearos-sync)
  - [3. 💬 Telephony, SMS & Autonomous WhatsApp](#3--telephony-sms--autonomous-whatsapp)
  - [4. 👁️ Vision Perception & Proactive Context](#4-️-vision-perception--proactive-context)
  - [5. 🧠 Neural Memory Core & Local Semantic RAG](#5--neural-memory-core--local-semantic-rag)
- [🗣️ Bilingual Command Reference](#️-bilingual-command-reference)
- [📦 Download & Installation](#-download--installation)
- [🔨 Building & Verification](#-building--verification)
- [📐 Clean Architecture](#-clean-architecture)
- [📜 Technology Stack](#-technology-stack)

---

## 📱 Visual Showcase (Google Stitch Material 3)

The user interface is engineered on **Google Stitch** (*Project `3145573286617872806`*), embodying Material 3 cyber-slate aesthetics: `#05090D` kinetic background, `#63EFFF` holographic cyan glow, glassmorphic container panels, fluid live frequency waveforms, and voice-first ergonomics.

<div align="center">
  <table align="center" width="100%">
    <tr>
      <th align="center" width="50%"><b>Dashboard HUD (Live Device)</b></th>
      <th align="center" width="50%"><b>Skills Command Deck (Live Device)</b></th>
    </tr>
    <tr>
      <td align="center">
        <a href="docs/stitch/device_screenshot_fixed.png">
          <img src="docs/stitch/device_screenshot_fixed.png" alt="J.A.R.V.I.S. Dashboard Live Screen" width="90%" />
        </a><br/>
        <sub><b>Arc Reactor Orb • Live Waveform • Smart Command Bar • Active Protocol Tracker</b></sub>
      </td>
      <td align="center">
        <a href="docs/stitch/device_screenshot_skills.png">
          <img src="docs/stitch/device_screenshot_skills.png" alt="J.A.R.V.I.S. Skills Deck Live Screen" width="90%" />
        </a><br/>
        <sub><b>Modular Capability Decks • Instant One-Touch Automations • Telephony & WhatsApp Hub</b></sub>
      </td>
    </tr>
  </table>
</div>

---

## 💎 Navigation Tabs & App Screens

J.A.R.V.I.S. provides a streamlined **5-tab navigation bar** alongside a persistent HUD:

| Tab Name | Scope & Key Capabilities |
| :--- | :--- |
| **🏠 HOME** | Interactive `JarvisOrbView` Arc Reactor core, live `JarvisWaveformView` audio spectrum, real-time multi-step task protocol tracker (`PLANNING`, `EXECUTING`, `VERIFYING`), and smart command vector bar. |
| **⚡ SKILLS** | Modular one-touch automation cards: Music Transport Controls & Presets, Telephony Quick Actions, WhatsApp Comm Link, Optical Vision AI, Driving/Meeting Autopilot switches, and NL Calculator. |
| **🧠 MEMORY** | Health Metrics HUD, structured SQLite memory browser (`USER_PREFERENCE`, `EPISODIC_EVENT`, `FACTUAL_KNOWLEDGE`), BM25 keyword relevance, half-life decay visualization, and automatic garbage collector. |
| **🔑 BRAIN** | Unified provider key management (Groq, Gemini, Ollama), personal wake-word sample enrollment, hardware AES-GCM-256 Keystore status, model chip selection, and live ping latency verifier. |
| **⚙️ CONFIG** | Acoustic options, lock-screen HUD toggle, continuous conversation & full-duplex barge-in switches, Android permission shortcuts, Bluetooth SCO routing, morning briefing alarm picker, and live embedded logcat telemetry viewer. |

---

## 📖 Feature Guide & Capabilities

<details open>
<summary><b>1. 🎙️ Continuous openWakeWord & Acoustic Engine</b></summary>

- **Multi-Head Wake Detector**: The single `ContinuousAudioStream` microphone path feeds openWakeWord's shared TFLite feature models (`melspectrogram.tflite`, `embedding_model.tflite`) and classifier heads (`hey_jarvis_v0.1.tflite` and custom `jarvis_openwakeword.tflite`). 2-of-3 temporal confirmation prevents false triggers.
- **Lock-Screen HUD Overlay**: Screen turns on automatically (`showWhenLocked="true"`) upon hearing the wake word, displaying the Arc Reactor HUD over the lock screen for hands-free queries.
- **Personal Dataset Enrollment**: Record real 16 kHz PCM16 WAV samples directly in-app and export dataset packages. Use `scripts/package_openwakeword.py` to compile and package custom TFLite classifier heads with verified `openwakeword_models.json` manifests.
- **Full-Duplex Acoustic Barge-In**: Sub-50ms speech interruption allows you to speak over J.A.R.V.I.S. mid-sentence to interrupt or re-direct commands.
</details>

<details>
<summary><b>2. 🎛️ Bluetooth Earbud SCO & WearOS Sync</b></summary>

- **Bluetooth SCO Audio Routing**: Automatically routes bidirectional audio to connected earbuds/headsets (Galaxy Buds, AirPods, Sony WH/WF, Bose, OnePlus) using low-latency SCO communication channels.
- **Physical Headset Hook Interception**: Single click on earbud media button (`KEYCODE_HEADSETHOOK`) wakes assistant into active listening; clicking while J.A.R.V.I.S. is speaking acts as a physical barge-in mute button.
- **WearOS Companion Synchronization**: Broadcast intent bridge (`WEAR_WAKE`, `WEAR_COMMAND`, `WEAR_STOP`) syncs watch tiles, Tasker macros, and rich WearableExtender notification action cards.
</details>

<details>
<summary><b>3. 💬 Telephony, SMS & Autonomous WhatsApp</b></summary>

- **Hands-Free Telephony**: Caller ID resolution announces incoming calls out loud (*"Incoming call from Rahul. Say answer or reject"*); accepts (`TelecomManager.acceptRingingCall()`) or declines calls via voice.
- **SMS Reading & OTP Extraction**: Parses incoming text messages out loud and automatically extracts 4-to-8 digit verification OTP codes from banks and 2FA services.
- **3-Tier Autonomous WhatsApp Engine**:
  - *Tier 1*: Zero-screen background quick-reply via `RemoteInput` notification channels.
  - *Tier 2*: Deep-link direct chat URI dispatch with auto-clicking `JarvisAccessibilityService` across English and Hindi button selectors (`"Send"`, `"भेजें"`).
  - *Tier 3*: In-app contact search fallback and auto-return to home screen (`GLOBAL_ACTION_HOME`).
</details>

<details>
<summary><b>4. 👁️ Vision Perception & Proactive Context</b></summary>

- **Multimodal Camera Vision**: Single-frame Camera2 snapshots evaluated via on-device TFLite edge heuristics and cloud vision LLMs (`qwen/qwen3.6-27b` / `gemini-2.5-flash`) for real-time object identification, text OCR, and scene description.
- **Proactive Contextual Guard**: Autonomous background warnings for critical battery levels ($\le 15\%$), battery charge completion (100%), and calendar meeting pre-briefings 10–15 minutes prior to scheduled events.
- **Executive Daily Morning Briefing**: AlarmManager-backed scheduled executive briefing summarizing calendar agenda, weather, battery health, and unread priority notifications.
</details>

<details>
<summary><b>5. 🧠 Neural Memory Core & Local Semantic RAG</b></summary>

- **Hybrid SQLite Vector Vault**: Combines FTS BM25 keyword relevance with dense cosine vector embeddings, maintaining sliding conversation turn history and persistent user facts.
- **Subsystem Container & RAM Optimization**: Boot-critical subsystems load in <50ms while heavy RAG vector stores and vision models lazy-load on demand. Listens to Android `ComponentCallbacks2.onTrimMemory()` signals to automatically flush vector caches during OS RAM pressure.
</details>

---

## 🗣️ Bilingual Command Reference

| Domain | English Voice Commands | Hindi / Hinglish Voice Commands |
| :--- | :--- | :--- |
| **Telephony** | *"Answer call"*, *"Reject call"*, *"Who is calling"*, *"Call Rahul"*, *"Dial 9876543210"* | *"Phone uthao"*, *"Call receive karo"*, *"Call kaat do"*, *"Rahul ko phone lagao"* |
| **SMS & OTP** | *"What is my OTP"*, *"Read SMS"*, *"Check latest message"*, *"Send SMS to Papa I reached"* | *"OTP batao"*, *"OTP kya aaya"*, *"SMS padho"*, *"Papa ko SMS bhejo main pahunch gaya"* |
| **WhatsApp** | *"Read WhatsApp messages"*, *"Send WhatsApp to Priya on my way"*, *"Auto reply on"* | *"WhatsApp padho"*, *"Priya ko WhatsApp karo raste me hoon"*, *"Auto reply chalu karo"* |
| **Camera Vision**| *"What am I looking at?"*, *"Identify this object"*, *"Read this text label"* | *"Ye kya hai"*, *"Camera se dekh ke batao"*, *"Ye padho"*, *"Samne kya hai"* |
| **Media & Ducking**| *"Play Bohemian Rhapsody"*, *"Play Synthwave on Spotify"*, *"Pause music"*, *"Next track"* | *"Gaana bajao"*, *"Agla gaana"*, *"Gaana roko"*, *"Song band karo"* |
| **Hardware Radios**| *"Turn on flashlight"*, *"Set volume to 80%"*, *"Turn off Wi-Fi"*, *"Check battery"* | *"Flashlight chalu karo"*, *"Awaaz tez karo"*, *"Wi-Fi band karo"*, *"Battery kitni hai"* |
| **Daily Briefing** | *"Good morning"*, *"Daily briefing"*, *"What is my schedule today"*, *"Add meeting"* | *"Aaj ka schedule kya hai"*, *"Daily briefing sunao"*, *"Calendar check karo"* |
| **Memory Core** | *"Remember car is on level B2"*, *"Where did I park my car?"*, *"What's my passport number"* | *"Yaad rakhna car level B2 par hai"*, *"Car kahan park ki thi?"*, *"Mera passport number kya hai"* |
| **Autopilot Modes**| *"Driving mode on"*, *"Meeting mode on"*, *"Autonomous status"*, *"Cancel all tasks"* | *"Driving mode chalu karo"*, *"Meeting mode lagao"*, *"Status batao"*, *"Ruk jao"* |

---

## 📦 Download & Installation

### Option 1: Direct Production Release APK
- 📥 **[`dist/jarvis-v1.1-release.apk`](dist/jarvis-v1.1-release.apk)** (**9.7 MB**, R8 minified, production signed)

#### Android Setup Steps:
1. Download `jarvis-v1.1-release.apk` directly on your Android device.
2. Open **Files / Downloads** and tap the APK to install.
3. Grant requested runtime permissions: **Microphone**, **Phone & Call Logs**, **SMS**, **Camera**, **Notification Access**, and **Jarvis Accessibility Service**.

### Option 2: ADB Command Line Installation
```bash
# Install APK directly via connected ADB interface:
adb install -r -d dist/jarvis-v1.1-release.apk

# Launch J.A.R.V.I.S. main activity:
adb shell am start -n com.jarvis/.ui.MainActivity
```

---

## 🔨 Building & Verification

### Build Requirements
- Android Studio Ladybug / Koala / Iguana
- Android SDK Target API 34 (Min API 28 / Android 9.0+)
- Java 17 / OpenJDK 17

### Build & Test Commands
```bash
# Run complete unit test suite (503 passed tests across 91 test suites)
./gradlew testDebugUnitTest

# Build optimized debug APK
./gradlew assembleDebug

# Build production R8 minified release package (9.7 MB)
./gradlew assembleRelease
```

---

## 📐 Clean Architecture

J.A.R.V.I.S. is architected using strict **Clean Architecture** boundaries organized into 9 system layers:

| Layer # | Layer Name | Architectural Scope & Core Components |
|:---:|---|---|
| **1** | **Presentation Layer** | Google Stitch Material 3 HUD, cyber-slate navigation screens, interactive `JarvisOrbView` & `JarvisWaveformView`. |
| **2** | **Application & Runtime** | Persistent `JarvisForegroundService`, `AssistantRuntime` coordinator, `SubsystemManager`, and `JarvisEventBus`. |
| **3** | **Domain & Autonomous Reasoning**| `MultiTaskDecomposer` planning engine, `HallucinationFirewall` safety bounds, and `AutonomousDaemon` supervisor. |
| **4** | **Device Tools & Automation** | 44+ automated capabilities, Android Accessibility Services, synthetic UI automation, WhatsApp, and telephony drivers. |
| **5** | **Voice & Acoustic Perception** | `WakeWordEngine` stream, openWakeWord TFLite heads (`hey_jarvis_v0.1.tflite`, `jarvis_openwakeword.tflite`), owner voice verifier, and Bluetooth SCO. |
| **6** | **AI Inference & Computer Vision** | Multi-provider LLM client (Groq `openai/gpt-oss-20b`/`120b`, Gemini `gemini-2.5-flash`, OpenRouter, Ollama) and Camera2 perception (`qwen/qwen3.6-27b`). |
| **7** | **Neural Memory & Vector RAG** | Dense vector embeddings, BM25 relevance re-ranking, half-life decay garbage collection, and AES-GCM-256 Keystore encryption. |
| **8** | **Test & Verification Suite** | 91 test suites containing 503 automated unit and integration tests with a 100% pass rate. |
| **9** | **Build & Infrastructure** | Gradle R8 code/resource shrinking, ProGuard optimizations, and native binary compression. |

---

## 📜 Technology Stack

| Category | Technologies & Tooling |
| :--- | :--- |
| **AI Models & Frameworks** | TensorFlow Lite (Quantized INT8 openWakeWord KWS), Groq Cloud LLMs (`gpt-oss-20b`/`120b`), Google Gemini (`gemini-2.5-flash`), Sherpa-ONNX |
| **Audio & Acoustics** | Android AudioRecord (`VOICE_RECOGNITION`), Bluetooth SCO (`setCommunicationDevice`), Native `MediaSession`, `AcousticEchoCanceler` |
| **UI & Design System** | **Google Stitch Material 3**, Custom Holographic Canvas (`JarvisOrbView`, `JarvisWaveformView`), Jetpack Components |
| **Storage & Security** | SQLite v4 (FTS + BM25 vector index), Android Keystore (AES-GCM-256), Encrypted SharedPreferences |
| **OS Automation** | Android Accessibility Service (`GLOBAL_ACTION_HOME`), Telecom Manager, Notification Listener (`RemoteInput`), AlarmManager |
| **Language & Concurrency** | Kotlin 1.9+, Kotlin Coroutines & Flow, SupervisorJob Scopes |

---

<div align="center">
  <sub>Built with ❤️ for autonomous AI research & hands-free mobile ergonomics</sub>
</div>
