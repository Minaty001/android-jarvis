# Implementation Plan: Hands-Free Full Control (Phase 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 2 hands-free full control capabilities: Continuous Ambient Open-Mic (walkie-talkie mode), Default Voice Assistant settings integration, Hands-Free Camera voice triggers & capture, Media voice scrubbing (seek & speed control), and Touchless Proximity/Camera Air Gestures.

**Architecture:** Extend Jarvis's sub-50ms offline intent resolver (`IntentModel.kt`), voice state machine (`VoiceEngine.kt`), media session controller (`MediaPlaybackControlTool.kt`), camera background service (`HandsFreeCameraTool.kt`), and hardware sensor listeners (`AirGestureEngine.kt`) to deliver instantaneous, zero-touch execution across Android.

**Tech Stack:** Kotlin, Android SDK (Camera2, MediaSession, AudioManager, Proximity/Accelerometer Sensors, VoiceInteractionService), Coroutines, JUnit4.

**Spec Reference:** User feedback and preferences from hands-free requirements interview.

---

## Global Constraints
- Target Android 8.0+ (API 26+) with full Android 10-14 support.
- All voice navigation, scrubbing, camera trigger, and gesture actions MUST execute offline with sub-50ms latency via local fast-paths.
- Maintain JDK 17 compatibility (`JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11`).
- Never break existing tests or require network roundtrips for offline controls.

---

## Review Focus
1. **ASR audio session loop exhaustion**: Continuous open-mic mode must cleanly restart listening without leaking coroutines or overflowing ASR audio buffers.
2. **Camera resource conflicts**: Background camera capture must release Camera2 device locks immediately so other apps (e.g. system camera) are never locked out.
3. **MediaSession permission / null safety**: When third-party apps do not expose an active `MediaSession`, voice scrubbing must gracefully fall back to `KeyEvent` media dispatchers without crashing.
4. **Proximity sensor false positives**: Air gesture detector must distinguish intentional hand sweeps (150ms-750ms dwell window) from phone being placed in pocket or face-down on a desk.
5. **Default assistant role compatibility**: Opening assistant settings must check Android 10+ `RoleManager` first before falling back to `ACTION_VOICE_INPUT_SETTINGS`.

---

## File Structure & Responsibilities

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/jarvis/voice/VoiceEngine.kt` | Modify | Continuous Ambient Open-Mic loop logic & state persistence |
| `app/src/main/java/com/jarvis/conversation/ConversationManager.kt` | Modify | Exit utterance matcher for open-mic & sleep phrases |
| `app/src/main/java/com/jarvis/assistant/AssistantSettingsHelper.kt` | Modify | Helper methods to open assistant settings & check status |
| `app/src/main/java/com/jarvis/tools/HandsFreeCameraTool.kt` | Create | Camera capture, photo saving to Gallery, camera launch & shutter click |
| `app/src/main/java/com/jarvis/tools/MediaPlaybackControlTool.kt` | Modify | Seek forward/backward by seconds & playback speed adjustments via MediaSession |
| `app/src/main/java/com/jarvis/sensors/AirGestureEngine.kt` | Create | Low-power proximity sensor wave detector for touchless scrolling & media control |
| `app/src/main/java/com/jarvis/ai/IntentModel.kt` | Modify | Sub-50ms local fast-paths for open-mic, default assistant, camera, media seek/speed, and air gestures |
| `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt` | Modify | Tool registration for `HandsFreeCameraTool` and `AirGestureEngine` |
| `app/src/test/java/com/jarvis/voice/ContinuousOpenMicTest.kt` | Create | Unit tests for open-mic activation, recycling, and sleep exit commands |
| `app/src/test/java/com/jarvis/tools/HandsFreeCameraToolTest.kt` | Create | Unit tests for camera tool actions and parameter parsing |
| `app/src/test/java/com/jarvis/tools/MediaVoiceScrubbingTest.kt` | Create | Unit tests for seek forward, seek backward, and playback speed dispatch |
| `app/src/test/java/com/jarvis/sensors/AirGestureEngineTest.kt` | Create | Unit tests for proximity wave timing detection and gesture triggers |

---

## Task Decomposition

### Task 1: Continuous Ambient Open-Mic ("Walkie-Talkie Mode")
**Files:**
- Modify: `app/src/main/java/com/jarvis/conversation/ConversationManager.kt`
- Modify: `app/src/main/java/com/jarvis/voice/VoiceEngine.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/voice/ContinuousOpenMicTest.kt`

**Interfaces:**
- Consumes: `VoiceEngine.startListeningForCommand()`, `ConversationManager.isExitUtterance()`
- Produces: `VoiceEngine.isAmbientOpenMicActive: Boolean`, `VoiceEngine.setAmbientOpenMic(enabled: Boolean)`

- [ ] **Step 1: Write the failing unit tests for Continuous Open-Mic**
  Test activation phrase ("Jarvis open mic", "turn on open mic"), deactivation phrase ("Jarvis sleep", "stop listening", "exit open mic"), and state recycling in `ContinuousOpenMicTest.kt`.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.voice.ContinuousOpenMicTest`
  Expected: Compilation failure or missing members.
- [ ] **Step 3: Implement Open-Mic state & recycling in `VoiceEngine.kt` and `ConversationManager.kt`**
  - Add `@Volatile var isAmbientOpenMicActive: Boolean = false`.
  - In `handleAsrEmptyOrError` and silence timeout: if `isAmbientOpenMicActive` is true, re-arm `startListeningForCommand(isFollowUp = true)` after a brief backoff delay (350ms) instead of terminating to dormant wake-word state.
  - In `handleRecognizedTranscript`: check for open-mic toggles ("open mic", "start open mic", "turn on open mic" vs "jarvis sleep", "stop open mic", "exit open mic").
- [ ] **Step 4: Add fast-path offline intents in `IntentModel.kt`**
  Map "open mic", "turn on open mic", "walkie talkie mode" -> `AssistantIntent.AMBIENT_OPEN_MIC(action="start")`.
  Map "stop open mic", "turn off open mic", "jarvis sleep", "go to sleep" -> `AssistantIntent.AMBIENT_OPEN_MIC(action="stop")`.
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.voice.ContinuousOpenMicTest`
  Expected: PASS.

---

### Task 2: Default Voice Assistant Voice Routing & Settings Helper
**Files:**
- Modify: `app/src/main/java/com/jarvis/assistant/AssistantSettingsHelper.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Test: `app/src/test/java/com/jarvis/assistant/AssistantSettingsHelperTest.kt`

**Interfaces:**
- Consumes: `AssistantSettingsHelper.openDefaultAssistantSettings()`, `AssistantSettingsHelper.isDefaultAssistant()`
- Produces: Fast-path `AssistantIntent.DEFAULT_ASSISTANT(action="open"|"check")`

- [ ] **Step 1: Write unit tests in `AssistantSettingsHelperTest.kt`**
  Verify intent creation, package verification, and intent action resolution for "set jarvis as default assistant", "make jarvis default assistant", "open assistant settings".
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.assistant.AssistantSettingsHelperTest`
  Expected: FAIL.
- [ ] **Step 3: Update `AssistantSettingsHelper.kt` and add local fast-path in `IntentModel.kt`**
  Add `DEFAULT_ASSISTANT` intent and wire direct offline dispatch in `AssistantRuntime.kt` to launch assistant settings or return status string.
- [ ] **Step 4: Run test to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.assistant.AssistantSettingsHelperTest`
  Expected: PASS.

---

### Task 3: Hands-Free Camera Voice Triggers & Photo Capture
**Files:**
- Create: `app/src/main/java/com/jarvis/tools/HandsFreeCameraTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Test: `app/src/test/java/com/jarvis/tools/HandsFreeCameraToolTest.kt`

**Interfaces:**
- Consumes: `CameraPerceptionEngine.captureFrame()`, Android `MediaStore`
- Produces: `HandsFreeCameraTool` with actions: `take_photo`, `take_selfie`, `launch_camera`, `record_video`, `shutter`

- [ ] **Step 1: Write unit tests in `HandsFreeCameraToolTest.kt`**
  Verify action execution, lens facing parameter mapping (front/back), intent generation for camera/video launch, and shutter keycode dispatch.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.HandsFreeCameraToolTest`
  Expected: FAIL (class not found).
- [ ] **Step 3: Implement `HandsFreeCameraTool.kt`**
  - Implement `take_photo`: invokes `CameraPerceptionEngine.captureFrame(facing)` and saves the bitmap to `MediaStore.Images.Media` or cache, returning spoken confirmation "Photo captured and saved to Gallery."
  - Implement `launch_camera`: creates `Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)` or `INTENT_ACTION_VIDEO_CAMERA`.
  - Implement `shutter`: dispatches `KeyEvent.KEYCODE_CAMERA` or clicks shutter node via accessibility.
- [ ] **Step 4: Register `HANDS_FREE_CAMERA` intent in `IntentModel.kt` and `AssistantRuntime.kt`**
  Fast-path offline patterns:
  - "take a picture", "take a photo", "capture photo", "photo khicho" -> `CAMERA(action="take_photo", facing="back")`
  - "take a selfie", "click selfie", "selfie lo" -> `CAMERA(action="take_photo", facing="front")`
  - "open camera", "launch camera", "camera kholo" -> `CAMERA(action="launch_camera")`
  - "record video", "start recording video", "video shoot karo" -> `CAMERA(action="record_video")`
  - "press shutter", "click shutter", "capture" -> `CAMERA(action="shutter")`
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.HandsFreeCameraToolTest`
  Expected: PASS.

---

### Task 4: Media Voice Scrubbing & Playback Speed Control
**Files:**
- Modify: `app/src/main/java/com/jarvis/tools/MediaPlaybackControlTool.kt`
- Modify: `app/src/main/java/com/jarvis/media/MediaSessionManager.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/MediaVoiceScrubbingTest.kt`

**Interfaces:**
- Consumes: `MediaController.transportControls.seekTo()`, `setPlaybackSpeed()`, `AudioManager.dispatchMediaKeyEvent()`
- Produces: `MediaPlaybackControlTool` actions `seek_forward`, `seek_backward`, `seek_to`, `set_speed`

- [ ] **Step 1: Write unit tests in `MediaVoiceScrubbingTest.kt`**
  Verify parameter parsing for "skip 30 seconds", "forward 15 seconds", "rewind 1 minute", "go back 45 seconds", "set speed to 1.5x", "normal speed".
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.MediaVoiceScrubbingTest`
  Expected: FAIL.
- [ ] **Step 3: Implement scrubbing and speed adjustments in `MediaPlaybackControlTool.kt`**
  - Extract delta seconds (`params["seconds"]?.toLongOrNull() ?: 30L`).
  - Read active `MediaController` from `MediaSessionManager`: query playback position, calculate `newPosition = (currentPosition + deltaMs).coerceAtLeast(0L)`, call `seekTo(newPosition)`.
  - Fallback to `KeyEvent.KEYCODE_MEDIA_FAST_FORWARD` or `KEYCODE_MEDIA_REWIND` if `MediaController` is not bound.
  - Implement speed control: `params["speed"]?.toFloatOrNull() ?: 1.0f`, call `setPlaybackSpeed(speed)`.
- [ ] **Step 4: Update `IntentModel.kt` with scrubbing patterns**
  Regex matchers for:
  - "(?:skip|forward|fast forward)\\s*(\\d+)?\\s*(?:seconds?|secs?|minutes?|mins?)"
  - "(?:rewind|go back|replay)\\s*(\\d+)?\\s*(?:seconds?|secs?|minutes?|mins?)"
  - "(?:playback speed|speed|play at)\\s*([0-9.]+)?x?"
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.MediaVoiceScrubbingTest`
  Expected: PASS.

---

### Task 5: Touchless Proximity & Camera Air Gestures
**Files:**
- Create: `app/src/main/java/com/jarvis/sensors/AirGestureEngine.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Modify: `app/src/main/java/com/jarvis/service/JarvisForegroundService.kt`
- Test: `app/src/test/java/com/jarvis/sensors/AirGestureEngineTest.kt`

**Interfaces:**
- Consumes: Android `SensorManager` (`Sensor.TYPE_PROXIMITY`), `MediaPlaybackControlTool`, `UiAutomationManager`
- Produces: `AirGestureEngine(context, sensorManager)` with `start()`, `stop()`, `setEnabled(Boolean)`

- [ ] **Step 1: Write unit tests in `AirGestureEngineTest.kt`**
  Simulate sensor events: wave sequence (`near` followed by `far` in 250ms -> triggers wave action), lingering obstruction (>1200ms -> ignored as pocket/table). Verify callbacks.
- [ ] **Step 2: Run test to verify failure**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.sensors.AirGestureEngineTest`
  Expected: FAIL.
- [ ] **Step 3: Implement `AirGestureEngine.kt`**
  - Track `nearTimestamp` on proximity near transition.
  - On proximity far transition, check duration: if `duration in 150L..800L`:
    - If media is currently playing: dispatch media play/pause toggle.
    - If media is not playing: dispatch scroll down action (`UiAutomationManager.scrollDown()`).
  - Provide safe thread synchronization and lifecycle registration.
- [ ] **Step 4: Add voice commands and wiring in `IntentModel.kt` and `AssistantRuntime.kt`**
  Fast-path voice commands:
  - "turn on air gestures", "enable air gestures", "touchless control on" -> `AIR_GESTURES(action="enable")`
  - "turn off air gestures", "disable air gestures", "touchless control off" -> `AIR_GESTURES(action="disable")`
- [ ] **Step 5: Run tests to verify pass**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.sensors.AirGestureEngineTest`
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
  Document the newly implemented Phase 2 capabilities, voice command cheat sheet, and verified test results in `walkthrough.md`.
