# Hands-Free Phase 7: Ambient Sound Detection & Safety Alerts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement offline, battery-efficient ambient sound detection for 5 critical safety signals (Baby Crying, Smoke Alarm, Doorbell, Glass Break, Car Honk) with multi-modal safety alert dispatching (spoken TTS audio, distinct haptics, emergency SMS, high-priority notifications) and bilingual voice controls, optimized for Samsung Galaxy A51 and LG K42.

**Architecture:**
- `AmbientSoundClassifier` performs zero-allocation acoustic feature extraction, spectral peak calculation, and Goertzel/band-energy frequency analysis on 16 kHz mono PCM frames to classify Baby Crying (300-600Hz fundamental + rhythmic cry cadence), Smoke Alarm (3000Hz pulsed T3/T4 pure tone), Doorbell/Chime (harmonic chime cadence), Glass Break (>4kHz high-frequency burst with fast attack), and Car Honk (400-500Hz dual continuous blast).
- `AmbientSoundMonitorEngine` coordinates background monitoring lifecycles, manages battery-aware duty cycling (continuous when charging vs 1s-active/2s-sleep duty cycle on battery for Helio P22 and Exynos 9611), acquires `PowerManager.PARTIAL_WAKE_LOCK` with auto-refresh guards, and executes temporal debounce filtering.
- `SoundAlertDispatcher` coordinates multi-modal alert delivery: spoken TTS announcement via earbuds/speaker, distinctive haptic vibration patterns per category, Android `NotificationManager` heads-up notification, and automated emergency SMS dispatch for life-critical alerts (Smoke Alarm, Baby Crying).
- `SoundDetectorTool` provides the agent runtime interface (`SOUND_DETECTOR`, `SOUND_MONITOR`) with `start`, `stop`, `status`, and `simulate` actions, exempt from manual confirmation in `PolicyEngine` and whitelisted in `LockScreenSecurityPolicy`.
- `IntentModel` fast-paths and `ToolRegistry` aliases provide sub-5ms bilingual regex resolution for English and Hindi voice controls.

**Tech Stack:** Kotlin 1.9, Android Jetpack, AudioRecord / ContinuousAudioStream, PowerManager WakeLock, Vibrator / VibratorManager, NotificationManager, Telephony/SmsManager, Coroutines, JUnit4.

**Spec:** Phase 7 Requirements: Hands-Free Ambient Sound Detection & Safety Alerts (Baby Crying, Smoke Alarm, Doorbell, Glass Break, Car Honk) for Samsung Galaxy A51 (Exynos 9611) & LG K42 (MediaTek Helio P22, 3GB RAM, Android 10 API 29).

## Global Constraints

- **Platform Floor:** Android 10 (API 29) compatibility for LG K42; Android 12 (API 31) support for Samsung A51.
- **Zero-Allocation PCM Processing:** No heap allocation inside the real-time audio classification inner loop (`classify(samples, count)`) to prevent GC stutter on 3GB RAM devices.
- **Battery-Aware Duty-Cycling:** On battery power, sound monitoring must default to duty-cycling (1000ms active window every 2000ms-3000ms) to conserve battery on MediaTek Helio P22 and Exynos 9611.
- **Debounce & Alert Cooldown:** Distinct safety alerts must be debounced with a minimum cooldown (default 10s) per category to avoid alert loops or audio-feedback howling.
- **Lock Screen Whitelist:** `SOUND_DETECTOR`, `SOUND_MONITOR`, `START_SOUND_DETECTION`, `STOP_SOUND_DETECTION` must execute seamlessly on locked screens without blocking for device unlock.
- **Zero Regressions:** 100% test pass rate across all existing 695 unit tests.

## Review Focus

1. **Silence and Normal Speech Non-Triggering:** Ordinary room background noise, silence, and human conversational speech must not trigger false alarms for smoke alarms or glass breaking.
2. **Duty-Cycling Transition:** Switching between battery power and USB/AC charging must dynamically adjust the duty cycle without stopping the monitor or dropping audio frames mid-analysis.
3. **Emergency SMS Delivery Failure Graceful Degradation:** If SMS dispatch fails (e.g. SIM card absent, telephony permission denied, or phone offline), the TTS, haptics, and notification alerts must still fire immediately without throwing exceptions.
4. **Debounce / Alert Storm Prevention:** Rapid repeated acoustic triggers (such as a 10-second continuous smoke alarm siren) must emit one primary alert followed by suppressed duplicates during the cooldown window.
5. **Clean Cancellation and Audio Release:** Calling `stop` or disabling monitoring must immediately terminate coroutine jobs, release the wake lock, and unregister audio frame consumers cleanly without leaking resources.

---

### Task 1: Acoustic Feature Extractor & Spectrogram Classifier

**Files:**
- Create: `app/src/main/java/com/jarvis/sound/AmbientSoundClassifier.kt`
- Test: `app/src/test/java/com/jarvis/sound/AmbientSoundClassifierTest.kt`

**Interfaces:**
- Consumes: 16 kHz 16-bit mono PCM arrays (`ShortArray`, `readCount: Int`)
- Produces:
  ```kotlin
  enum class SoundCategory {
      NONE,
      BABY_CRYING,
      SMOKE_ALARM,
      DOORBELL,
      GLASS_BREAK,
      CAR_HONK
  }

  data class SoundClassificationResult(
      val category: SoundCategory,
      val confidence: Float,
      val dominantFrequencyHz: Float = 0f,
      val energyRatio: Float = 0f,
      val timestamp: Long = System.currentTimeMillis()
  )

  class AmbientSoundClassifier(
      val sampleRate: Int = 16000,
      val windowSize: Int = 1024
  ) {
      fun classify(samples: ShortArray, readCount: Int): SoundClassificationResult
      fun reset()
  }
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/sound/AmbientSoundClassifierTest.kt` with synthetic audio generators:
  - Tests silence and conversational speech (returns `NONE`).
  - Tests 3000 Hz pulsed tone (returns `SMOKE_ALARM`, confidence >= 0.70).
  - Tests 450 Hz tone with harmonic crying cadence (returns `BABY_CRYING`, confidence >= 0.65).
  - Tests 700 Hz -> 500 Hz chime pulse (returns `DOORBELL`, confidence >= 0.65).
  - Tests 5000 Hz transient high-frequency energy burst (returns `GLASS_BREAK`, confidence >= 0.70).
  - Tests 440 Hz dual tone sustained blast (returns `CAR_HONK`, confidence >= 0.70).

- [ ] **Step 2: Run test to verify it fails**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.sound.AmbientSoundClassifierTest`
  Expected: FAIL (unresolved reference `AmbientSoundClassifier`).

- [ ] **Step 3: Implement `AmbientSoundClassifier.kt`**
  Implement `AmbientSoundClassifier.kt`:
  - Pre-allocated float buffers for windowing and Goertzel filter / Fast Hartley Transform / Real FFT.
  - Zero heap allocations inside `classify(samples: ShortArray, readCount: Int)`.
  - Energy band ratio detectors:
    - Smoke alarm: 2800 - 3200 Hz pure-tone energy ratio > 0.60.
    - Baby crying: 300 - 600 Hz fundamental with harmonic peaks and rhythmic envelope cadence.
    - Doorbell: Dual-tone chime (600-800 Hz and 400-550 Hz resonance).
    - Glass break: High-frequency energy > 4000 Hz with fast attack (< 40ms) and low sub-1000Hz energy.
    - Car honk: Sustained dual-tone 400 - 550 Hz energy > 300ms.
  - Return `SoundClassificationResult`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.sound.AmbientSoundClassifierTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/sound/AmbientSoundClassifier.kt app/src/test/java/com/jarvis/sound/AmbientSoundClassifierTest.kt
  git commit -m "feat(sound): implement zero-allocation ambient sound classifier for 5 safety categories"
  ```

---

### Task 2: Ambient Sound Monitor Engine

**Files:**
- Create: `app/src/main/java/com/jarvis/sound/AmbientSoundMonitorEngine.kt`
- Test: `app/src/test/java/com/jarvis/sound/AmbientSoundMonitorEngineTest.kt`

**Interfaces:**
- Consumes: `Context`, `PowerManager`, `AmbientSoundClassifier`, `SoundClassificationResult`
- Produces:
  ```kotlin
  data class SoundDetectionEvent(
      val category: SoundCategory,
      val confidence: Float,
      val dominantFrequencyHz: Float,
      val timestamp: Long = System.currentTimeMillis()
  )

  fun interface SoundDetectionListener {
      fun onSoundDetected(event: SoundDetectionEvent)
  }

  object AmbientSoundMonitorEngine {
      enum class DutyCycleMode { CONTINUOUS, BATTERY_SAVER }

      fun startMonitoring(
          context: Context? = null,
          categories: Set<SoundCategory> = SoundCategory.values().filter { it != SoundCategory.NONE }.toSet(),
          dutyCycleMode: DutyCycleMode = DutyCycleMode.BATTERY_SAVER
      ): Boolean

      fun stopMonitoring()
      fun isMonitoring(): Boolean
      fun getActiveCategories(): Set<SoundCategory>
      fun addListener(listener: SoundDetectionListener)
      fun removeListener(listener: SoundDetectionListener)
      fun clearListeners()
      fun processAudioFrame(samples: ShortArray, count: Int)
      fun simulateDetection(category: SoundCategory, confidence: Float = 0.95f)
      fun setCooldownMs(ms: Long)
      fun getDetectionCount(category: SoundCategory): Int
      fun resetStats()
  }
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/sound/AmbientSoundMonitorEngineTest.kt` testing:
  - `startMonitoring` and `stopMonitoring` lifecycle and state transitions.
  - Category filtering: ignoring categories outside `getActiveCategories()`.
  - Cooldown debouncing: rapid consecutive detections of the same sound within 10s only fire once.
  - Listener dispatch: registered `SoundDetectionListener` receives expected event.
  - `simulateDetection` triggers listeners for testing.
  - WakeLock acquisition when `context != null` with `PARTIAL_WAKE_LOCK`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.sound.AmbientSoundMonitorEngineTest`
  Expected: FAIL (unresolved reference `AmbientSoundMonitorEngine`).

- [ ] **Step 3: Implement `AmbientSoundMonitorEngine.kt`**
  Implement `AmbientSoundMonitorEngine.kt`:
  - `startMonitoring`: sets `isMonitoring = true`, acquires `PowerManager.PARTIAL_WAKE_LOCK`, configures duty cycle and target categories.
  - `processAudioFrame`: feeds samples to `AmbientSoundClassifier`, checks category filters and cooldown timestamps, notifies listeners if confidence >= threshold.
  - `stopMonitoring`: releases WakeLock, cancels background coroutines, resets active state.
  - Duty-cycling helper for Helio P22 / Exynos 9611 battery preservation.

- [ ] **Step 4: Run test to verify it passes**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.sound.AmbientSoundMonitorEngineTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/sound/AmbientSoundMonitorEngine.kt app/src/test/java/com/jarvis/sound/AmbientSoundMonitorEngineTest.kt
  git commit -m "feat(sound): implement ambient sound monitor engine with duty-cycling and debounce protection"
  ```

---

### Task 3: Multi-Modal Safety Alert Dispatcher & Tool

**Files:**
- Create: `app/src/main/java/com/jarvis/sound/SoundAlertDispatcher.kt`
- Create: `app/src/main/java/com/jarvis/tools/SoundDetectorTool.kt`
- Modify: `app/src/main/java/com/jarvis/foundation/LockScreenSecurityPolicy.kt:13-65`
- Modify: `app/src/main/java/com/jarvis/foundation/PolicyEngine.kt:50-80`
- Test: `app/src/test/java/com/jarvis/tools/SoundDetectorToolTest.kt`

**Interfaces:**
- Consumes: `Context`, `AmbientSoundMonitorEngine`, `TelephonyTool`, `Vibrator`, `NotificationManager`
- Produces:
  ```kotlin
  object SoundAlertDispatcher {
      fun formatAlertAnnouncement(category: SoundCategory): String
      fun getVibrationPattern(category: SoundCategory): LongArray
      suspend fun dispatchAlert(
          context: Context?,
          event: SoundDetectionEvent,
          emergencyContact: String? = null,
          ttsSpeaker: ((String) -> Unit)? = null
      ): Boolean
  }

  class SoundDetectorTool(private val context: Context? = null) : Tool {
      override val name: String = "SOUND_DETECTOR"
      override suspend fun execute(params: Map<String, String>): ToolResult
  }
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/tools/SoundDetectorToolTest.kt` testing:
  - Spoken TTS announcements formatted correctly for each `SoundCategory`.
  - Haptic vibration patterns mapped correctly (`SMOKE_ALARM`, `BABY_CRYING`, `GLASS_BREAK`, `CAR_HONK`, `DOORBELL`).
  - Automated SMS dispatch for `SMOKE_ALARM` when contact is supplied.
  - `SoundDetectorTool` execution:
    - `action="start"` starts monitoring.
    - `action="stop"` stops monitoring.
    - `action="status"` reports active categories and monitoring status.
    - `action="simulate"` dispatches alert.
  - `LockScreenSecurityPolicy.isActionAllowedOnLockScreen` permits `SOUND_DETECTOR`.
  - `PolicyEngine.evaluateAction` does not require user approval confirmation for `SOUND_DETECTOR`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.SoundDetectorToolTest`
  Expected: FAIL (unresolved reference `SoundDetectorTool`).

- [ ] **Step 3: Implement `SoundAlertDispatcher.kt`, `SoundDetectorTool.kt` and policy exemptions**
  - Implement `SoundAlertDispatcher.kt` with vibration cadences, spoken TTS text, and SMS dispatch.
  - Implement `SoundDetectorTool.kt` implementing `Tool`.
  - Add `SOUND_DETECTOR`, `SOUND_MONITOR`, `START_SOUND_DETECTION`, `STOP_SOUND_DETECTION` to `LockScreenSecurityPolicy.kt`.
  - Exempt `SOUND_DETECTOR` from confirmation in `PolicyEngine.kt`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.SoundDetectorToolTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/sound/SoundAlertDispatcher.kt app/src/main/java/com/jarvis/tools/SoundDetectorTool.kt app/src/main/java/com/jarvis/foundation/LockScreenSecurityPolicy.kt app/src/main/java/com/jarvis/foundation/PolicyEngine.kt app/src/test/java/com/jarvis/tools/SoundDetectorToolTest.kt
  git commit -m "feat(sound): implement multi-modal safety alert dispatcher and SoundDetectorTool with policy exemptions"
  ```

---

### Task 4: Fast-Path Intent Routing, Bilingual Triggers & ToolRegistry Aliases

**Files:**
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt:650-750`
- Modify: `app/src/main/java/com/jarvis/tools/ToolRegistry.kt:250-320`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt:400-470`
- Test: `app/src/test/java/com/jarvis/ai/SoundDetectorIntentTest.kt`

**Interfaces:**
- Consumes: Voice query strings (English and Hindi)
- Produces: Mapped Intent matches for `SOUND_DETECTOR` with parameters:
  - `action`: `start`, `stop`, `status`
  - `type`: `all`, `baby_crying`, `smoke_alarm`, `doorbell`, `glass_break`, `car_honk`

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/ai/SoundDetectorIntentTest.kt` testing:
  - English triggers:
    - "start sound monitoring", "enable sound detection", "detect ambient sounds" -> `action=start, type=all`
    - "listen for baby crying", "detect baby crying", "monitor baby" -> `action=start, type=baby_crying`
    - "detect smoke alarm", "listen for smoke alarm", "smoke detector on" -> `action=start, type=smoke_alarm`
    - "listen for doorbell", "detect glass break", "detect car honk" -> corresponding category types
    - "stop sound monitoring", "stop sound detection" -> `action=stop`
    - "sound detector status", "is sound detector running" -> `action=status`
  - Hindi triggers:
    - "sound detector chalu karo", "ambient sounds suno" -> `action=start, type=all`
    - "bacha ro raha hai suno", "baby crying detect karo" -> `action=start, type=baby_crying`
    - "smoke alarm suno", "alarm detection on karo" -> `action=start, type=smoke_alarm`
    - "sound detector band karo" -> `action=stop`
    - "sound detector status batao" -> `action=status`
  - ToolRegistry alias resolution: `SOUND_MONITOR`, `START_SOUND_DETECTION`, `STOP_SOUND_DETECTION` resolve to `SOUND_DETECTOR`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.ai.SoundDetectorIntentTest`
  Expected: FAIL.

- [ ] **Step 3: Update `IntentModel.kt`, `ToolRegistry.kt`, and `AssistantRuntime.kt`**
  - Add sound detector intent fast-paths in `IntentModel.kt`.
  - Register `SoundDetectorTool` and aliases in `ToolRegistry.kt`.
  - Add registration in `AssistantRuntime.kt`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.ai.SoundDetectorIntentTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/main/java/com/jarvis/tools/ToolRegistry.kt app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt app/src/test/java/com/jarvis/ai/SoundDetectorIntentTest.kt
  git commit -m "feat(intent): add bilingual fast-path voice triggers and ToolRegistry aliases for sound detection"
  ```

---

### Task 5: End-to-End Regression Verification & Artifact Documentation

**Files:**
- Modify: `app/src/test/java/com/jarvis/AcceptanceScenariosTest.kt`
- Modify: Walkthrough artifact `walkthrough.md`

- [ ] **Step 1: Add end-to-end acceptance scenarios**
  Add acceptance scenario in `AcceptanceScenariosTest.kt`:
  - User voice command "detect smoke alarm" -> fast-path resolves to `SOUND_DETECTOR` with `type=smoke_alarm`.
  - Monitor starts in battery saver mode.
  - Smoke alarm acoustic frequency (3000 Hz) triggers classification.
  - Dispatcher formats TTS alert and haptic vibration pattern.
  - User voice command "stop sound detection" terminates monitoring and releases resources.

- [ ] **Step 2: Run the full unit test suite**
  Run: `env JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest`
  Expected: 100% PASS with 715+ passing tests, 0 failures, 0 regressions.

- [ ] **Step 3: Update `walkthrough.md`**
  Document Phase 7 architecture, sound frequency bands, battery duty cycling, safety alert mechanics, and test metrics.

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/test/java/com/jarvis/AcceptanceScenariosTest.kt
  git commit -m "test(acceptance): add end-to-end acceptance tests for ambient sound detection and safety alerts"
  ```
