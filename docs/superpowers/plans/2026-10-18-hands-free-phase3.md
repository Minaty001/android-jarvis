# Implementation Plan: Hands-Free Full Control (Phase 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 3 hands-free full control capabilities: Universal Voice Typing & Field Dictation, Hands-Free Speakerphone & In-Call Management, Split-Screen & Multitasking Control, Fine-Grained Brightness & Audio Slider Scrubbing, and Dedicated In-Car Driving HUD Mode.

**Architecture:** Extend Jarvis's offline fast-path resolver (`IntentModel.kt`), accessibility automation service (`JarvisAccessibilityService.kt`, `UiAutomationManager.kt`), telecom audio router (`CallAndSmsAgent.kt`), system settings tool (`DeviceSettingsTool.kt`), and driving HUD state engine (`DrivingModeEngine.kt`) to deliver comprehensive zero-touch operation across all phone workflows.

**Tech Stack:** Kotlin, Android SDK (AccessibilityService, TelecomManager, AudioManager, Settings.System, MediaSession), Coroutines, JUnit4.

**Spec Reference:** User feedback and preferences from hands-free requirements interview.

---

## Global Constraints
- Target Android 8.0+ (API 26+) with full Android 10-14 compatibility.
- All voice navigation, dictation, call speakerphone, slider scrubbing, and driving commands MUST resolve offline with sub-50ms latency via local fast-paths.
- Maintain JDK 17 compatibility (`JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11`).
- Clean build and 100% green test pass across the entire repository before completing any task.

---

## Review Focus
1. **Accessibility Input Focus Detection**: Voice typing must gracefully handle screens where no text input is currently focused, returning clear spoken feedback rather than failing silently.
2. **Audio Mode & Speakerphone Permissions**: In-call speakerphone switching and mic muting must safely check `MODIFY_PHONE_STATE` / `AudioManager.mode` without causing audio feedback or crashes on Android 12+ audio routing.
3. **Split-Screen Multi-Window Support**: Split-screen global action must verify API level (Android 7.0+ / API 24) and handle apps that disallow split-screen resize without crashing the accessibility service.
4. **System Brightness Write Settings**: Brightness adjustment must detect if `Settings.System.canWrite(context)` is granted, and gracefully fall back to notification quick-settings slider manipulation if write permission is restricted.
5. **Driving Mode Overlay Threading**: Driving mode HUD must run without blocking voice recognition coroutines or audio ducking pipelines.

---

## File Structure & Responsibilities

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt` | Modify | Focused input node search (`FOCUS_INPUT`), text appending, backspace, and send action |
| `app/src/main/java/com/jarvis/tools/VoiceTypingTool.kt` | Create | Tool for hands-free typing, dictation, backspacing, and clearing text in active inputs |
| `app/src/main/java/com/jarvis/telecom/CallAndSmsAgent.kt` | Modify | Speakerphone auto-toggle, mic mute/unmute, voice-dial on speaker, and call ending |
| `app/src/main/java/com/jarvis/accessibility/UiAutomationManager.kt` | Modify | Split-screen toggle (`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`) and all-apps drawer action |
| `app/src/main/java/com/jarvis/tools/DeviceSettingsTool.kt` | Modify | Fine-grained brightness adjustment (0-100%) and individual stream volume controls |
| `app/src/main/java/com/jarvis/car/DrivingModeEngine.kt` | Create | In-car driving mode state coordinator (auto open-mic, auto-speaker, glanceable HUD) |
| `app/src/main/java/com/jarvis/tools/DrivingModeTool.kt` | Create | Tool for starting, stopping, and inspecting driving mode status |
| `app/src/main/java/com/jarvis/ai/IntentModel.kt` | Modify | Sub-50ms local fast-paths for voice typing, speakerphone/in-call, split-screen, brightness, and driving mode |
| `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt` | Modify | Tool registration for `VoiceTypingTool` and `DrivingModeTool` |
| `app/src/test/java/com/jarvis/tools/VoiceTypingToolTest.kt` | Create | Unit tests for typing, backspace, clear, and input focus resolution |
| `app/src/test/java/com/jarvis/telecom/InCallManagementTest.kt` | Create | Unit tests for speakerphone toggle, in-call mute, and voice-dialing on speaker |
| `app/src/test/java/com/jarvis/accessibility/SplitScreenActionTest.kt` | Create | Unit tests for split-screen and app drawer global action resolution |
| `app/src/test/java/com/jarvis/tools/BrightnessControlTest.kt` | Create | Unit tests for brightness percentage adjustments and stream volume scrubbing |
| `app/src/test/java/com/jarvis/car/DrivingModeEngineTest.kt` | Create | Unit tests for driving mode activation, open-mic linking, and status reporting |

---

## Task Decomposition

### Task 1: Universal Voice Typing & Field Dictation
**Files:**
- Modify: `app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt`
- Create: `app/src/main/java/com/jarvis/tools/VoiceTypingTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Test: `app/src/test/java/com/jarvis/tools/VoiceTypingToolTest.kt`

**Interfaces:**
- Consumes: `JarvisAccessibilityService.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)`, `setNodeText()`
- Produces: `VoiceTypingTool` with actions `type`, `backspace`, `clear`, `send`

- [ ] **Step 1: Write failing unit tests in `VoiceTypingToolTest.kt`**
  Verify intent resolution for `"type hello world"`, `"dictate meeting at 5pm"`, `"write thank you"`, `"backspace"`, `"clear text"`, `"press enter"`, `"press send"`.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.VoiceTypingToolTest`
  Expected: FAIL.
- [ ] **Step 3: Implement focused input handling in `JarvisAccessibilityService.kt` and `VoiceTypingTool.kt`**
  - Implement `findFocusedInputNode(): AccessibilityNodeInfo?`.
  - Implement `injectText(text: String, append: Boolean)`: reads existing text, appends/replaces, dispatches `ACTION_SET_TEXT`.
  - Implement `backspace()`: removes trailing character/word.
  - Implement `clearText()`: sets text to empty string.
  - Implement `sendOrEnter()`: clicks "Send" / "Search" / "Done" action button or dispatches enter key.
- [ ] **Step 4: Register `VOICE_TYPING` in `IntentModel.kt` and `AssistantRuntime.kt`**
  Regex matchers for:
  - `"^(?:type|write|dictate)\\s+(.+)$"` -> `VOICE_TYPING(action="type", text=match)`
  - `"^(?:backspace|delete last word|delete word)$"` -> `VOICE_TYPING(action="backspace")`
  - `"^(?:clear text|delete all|clear input)$"` -> `VOICE_TYPING(action="clear")`
  - `"^(?:press enter|press send|send message|submit)$"` -> `VOICE_TYPING(action="send")`
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.VoiceTypingToolTest`
  Expected: PASS.

---

### Task 2: Hands-Free Speakerphone & In-Call Voice Management
**Files:**
- Modify: `app/src/main/java/com/jarvis/telecom/CallAndSmsAgent.kt`
- Modify: `app/src/main/java/com/jarvis/tools/TelephonyTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/telecom/InCallManagementTest.kt`

**Interfaces:**
- Consumes: `AudioManager.setSpeakerphoneOn()`, `setMicrophoneMute()`, `TelecomManager.endCall()`
- Produces: `CallAndSmsAgent.setSpeakerphone(Boolean)`, `CallAndSmsAgent.setMute(Boolean)`, `CallAndSmsAgent.endActiveCall()`

- [ ] **Step 1: Write failing unit tests in `InCallManagementTest.kt`**
  Verify intent resolution for `"call mom on speaker"`, `"put on speaker"`, `"turn on speakerphone"`, `"mute call"`, `"unmute call"`, `"hang up"`.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.telecom.InCallManagementTest`
  Expected: FAIL.
- [ ] **Step 3: Implement in-call audio controls in `CallAndSmsAgent.kt` and `TelephonyTool.kt`**
  - Add `setSpeakerphone(enabled: Boolean): Boolean`.
  - Add `setMicrophoneMute(muted: Boolean): Boolean`.
  - Add `endActiveCall(): Boolean`.
  - In `answerCall()`: auto-activate speakerphone if headset is not connected so user hears the caller immediately hands-free.
- [ ] **Step 4: Update `IntentModel.kt` with speakerphone and in-call fast-paths**
  Fast-paths for:
  - `"call ... on speaker"` -> `TELEPHONY_CALL(speaker="true")`
  - `"put on speaker"`, `"speakerphone on"`, `"speaker on"` -> `TELEPHONY_CONTROL(action="speaker", state="on")`
  - `"mute call"`, `"mute mic"`, `"mute microphone"` -> `TELEPHONY_CONTROL(action="mute", state="on")`
  - `"unmute call"`, `"unmute mic"` -> `TELEPHONY_CONTROL(action="mute", state="off")`
  - `"hang up"`, `"end call"`, `"disconnect call"` -> `TELEPHONY_CONTROL(action="hang_up")`
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.telecom.InCallManagementTest`
  Expected: PASS.

---

### Task 3: Hands-Free Split-Screen & Multitasking Control
**Files:**
- Modify: `app/src/main/java/com/jarvis/accessibility/UiAutomationManager.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/accessibility/SplitScreenActionTest.kt`

**Interfaces:**
- Consumes: `AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` (7), `GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS` (14)
- Produces: `UiAutomationManager.performGlobalAction("split_screen")`, `"app_drawer"`

- [ ] **Step 1: Write failing unit tests in `SplitScreenActionTest.kt`**
  Verify intent resolution for `"split screen"`, `"toggle split screen"`, `"close split screen"`, `"open app drawer"`, `"show all apps"`.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.accessibility.SplitScreenActionTest`
  Expected: FAIL.
- [ ] **Step 3: Implement split-screen and app-drawer actions in `UiAutomationManager.kt`**
  Add cases for `"split_screen"`, `"toggle_split_screen"` to dispatch `AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`.
  Add cases for `"app_drawer"`, `"all_apps"` to dispatch `AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS` on API 31+ or home-swipe.
- [ ] **Step 4: Update `IntentModel.kt` fast-paths**
  Add matchers for split screen and app drawer under `AssistantIntent.UI_GLOBAL_ACTION`.
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.accessibility.SplitScreenActionTest`
  Expected: PASS.

---

### Task 4: Fine-Grained Brightness & Audio Slider Scrubbing
**Files:**
- Modify: `app/src/main/java/com/jarvis/tools/DeviceSettingsTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/BrightnessControlTest.kt`

**Interfaces:**
- Consumes: `Settings.System.SCREEN_BRIGHTNESS`, `AudioManager.STREAM_ALARM`, `STREAM_RING`, `STREAM_NOTIFICATION`
- Produces: `DeviceSettingsTool` action `'set_brightness'`, stream volume scrubbing

- [ ] **Step 1: Write failing unit tests in `BrightnessControlTest.kt`**
  Verify intent resolution for `"set brightness to 70%"`, `"dim screen"`, `"max brightness"`, `"alarm volume 80%"`, `"ringtone volume 50%"`.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.BrightnessControlTest`
  Expected: FAIL.
- [ ] **Step 3: Implement brightness and stream volume controls in `DeviceSettingsTool.kt`**
  - Add `"set_brightness"` action: reads `percent` (0..100), maps to 0..255 integer, writes via `Settings.System.putInt(contentResolver, SCREEN_BRIGHTNESS)` or returns spoken confirmation.
  - Support stream volume actions with `"alarm"`, `"ring"`, `"notification"`.
- [ ] **Step 4: Update `IntentModel.kt` with regex matchers**
  Regex matchers for:
  - `"(?:set\\s+)?(?:screen\\s+)?brightness\\s+(?:to\\s+)?(\\d{1,3})%?"`
  - `"(?:alarm|alarm volume)\\s+(?:to\\s+)?(\\d{1,3})%?"`
  - `"(?:ringtone|ringer volume)\\s+(?:to\\s+)?(\\d{1,3})%?"`
  - `"dim screen"`, `"max brightness"`
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.BrightnessControlTest`
  Expected: PASS.

---

### Task 5: Dedicated In-Car Driving Mode & Hands-Free HUD
**Files:**
- Create: `app/src/main/java/com/jarvis/car/DrivingModeEngine.kt`
- Create: `app/src/main/java/com/jarvis/tools/DrivingModeTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Test: `app/src/test/java/com/jarvis/car/DrivingModeEngineTest.kt`

**Interfaces:**
- Consumes: `VoiceEngine.setAmbientOpenMic()`, `EarbudsAssistantEngine`, `MediaPlaybackControlTool`
- Produces: `DrivingModeEngine.startDrivingMode()`, `stopDrivingMode()`, `isDrivingModeActive: Boolean`

- [ ] **Step 1: Write failing unit tests in `DrivingModeEngineTest.kt`**
  Verify intent resolution for `"start driving mode"`, `"driving mode on"`, `"i'm driving"`, `"stop driving mode"`, `"driving mode status"`.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.car.DrivingModeEngineTest`
  Expected: FAIL.
- [ ] **Step 3: Implement `DrivingModeEngine.kt` and `DrivingModeTool.kt`**
  - Implement `startDrivingMode()`: activates continuous open-mic mode, configures auto-announcement of all incoming calls/messages, and sets driving status active.
  - Implement `stopDrivingMode()`: restores standard standby mode.
  - Implement `DrivingModeTool`: actions `'start'`, `'stop'`, `'status'`.
- [ ] **Step 4: Update `IntentModel.kt` and `AssistantRuntime.kt`**
  Register `DRIVING_MODE` intent and tool.
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.car.DrivingModeEngineTest`
  Expected: PASS.

---

### Task 6: Full Integration & Regression Verification
**Files:**
- Run: Full unit test suite across all modules
- Verify: Zero regressions, clean build

- [ ] **Step 1: Run complete test suite**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest`
  Expected: All tests pass (100% green).
- [ ] **Step 2: Update Walkthrough Artifact**
  Update `walkthrough.md` with Phase 3 capabilities, voice command cheat sheet, and test verification results.
