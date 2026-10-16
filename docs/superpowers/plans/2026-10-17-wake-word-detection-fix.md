# Wake Word Detection Optimization & Acoustic Pipeline Fix Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the wake word acoustic pipeline so hands-free wake word recognition ("Jarvis" and "Hey Jarvis") triggers reliably and promptly in natural indoor acoustic conditions on Android devices (including low-end / budget hardware like the LG LM-K420) without high false-positive rates.

**Architecture:** 
1. Replace the rigid static `0.005` RMS energy floor with an adaptive acoustic floor and graceful decay (rather than instant reset) so natural speech phonemes are not prematurely discarded.
2. Calibrate model manifest thresholds and temporal confirmation windowing in `KwsTemporalConfirmation` to accept natural mobile speech cadence (~0.45–0.55 raw neural confidence).
3. Connect UI `wakeSensitivity` preferences ("HIGH", "BALANCED", "LOW") to the neural wake-word pipeline.
4. Provide deterministic ADB audio injection and diagnostics in `AdbBridgeEngine` for automated end-to-end acoustic verification.

**Tech Stack:** Kotlin, Android NDK/AudioRecord (16kHz PCM), TensorFlow Lite (`openWakeWord`), JUnit 4, ADB, Python 3.

**Spec:** Diagnosed from real-world telemetry on connected LG LM-K420 (Android 12, API 31).

---

## Root Cause Analysis & Empirical Findings

During Phase 1 live-device testing on the connected LG LM-K420 (`LMK420CUQOLFLNU8B6`), we captured the following ground truth:

1. **Acoustic Noise Floor Mismatch:**
   - Physical device telemetry via `wake_diagnostics` reported ambient room RMS of `0.00017` to `0.00043` (-76 dBFS to -68 dBFS).
   - Normal conversational speech from a distance of 1–2 feet on this microphone yields RMS between `0.001` and `0.003` (-60 dBFS to -50 dBFS).
   - `WakeWordConfig.minEnergyRms` defaulted to `0.005` (-46 dBFS). As a result, `energyPassed` was `false` for nearly all normal utterances, blocking inference at line 203 of `OpenWakeWordDetector.kt`.

2. **Destructive Temporal Confirmation Reset:**
   - In `OpenWakeWordDetector.kt`:
     ```kotlin
     if (!energyPassed) {
         confirmation.reset()
         latestScore = 0f
         detectedPhrase = null
     }
     ```
   - In human speech, intra-word transitions (such as between "Hey" and "Jarvis", or the consonants 'r'/'v'/'s') routinely dip in amplitude. An immediate `confirmation.reset()` wiped out all accumulated confidence frames whenever a single 80ms chunk dipped below the floor.

3. **Impractically High Activation Ratio Thresholds:**
   - `openwakeword_models.json` set `jarvis.tflite` threshold to `0.65`.
   - `OpenWakeWordDetector.kt` passed `bestRatio = score / head.threshold` into `KwsTemporalConfirmation.add(bestRatio, 1f)`.
   - `KwsTemporalConfirmation` requires either:
     - A single spike `>= 1.25 * threshold` (meaning raw neural score `>= 0.8125`!), OR
     - 2 of 3 consecutive 80ms windows `>= 1.0 * threshold` (raw score `>= 0.65` in both windows).
   - Real-world device logs recorded valid utterances reaching `ratio=0.7513` and `ratio=0.7100` (raw score ~`0.48`), which were discarded as non-triggers.

4. **Disconnected Sensitivity Settings:**
   - The user preference `wakeSensitivity` in `UiPreferencesStore` ("HIGH", "BALANCED", "LOW") was only wired into `BargeInDetector`, leaving `WakeWordEngine` locked into fixed, brittle parameters.

---

## Global Constraints

- **Audio Format:** 16,000 Hz, 16-bit PCM Mono (`FRAME_SAMPLES = 1280` shorts / 80ms chunks).
- **Latency Budget:** Inference latency per 80ms frame must remain `< 25ms` on low-end devices (`com.jarvis.device.HardwareProfileManager.isLowEndDevice()`).
- **Zero Regression on False Positives:** Normal conversation without the wake word must not trigger phantom activations.
- **JDK Requirement:** Gradle test commands must execute with `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11`.
- **Preserve Documentation Integrity:** Maintain all existing class comments and contract definitions.

---

## Review Focus

1. **Whispered or Low-Volume Speech:** RMS ~`0.0008` must not be gated out when sensitivity is "HIGH" or "BALANCED".
2. **Natural Intra-Word Pauses:** A 40–80ms dip in energy between syllables must not wipe accumulated temporal confirmation history.
3. **Background Fan/HVAC Hum:** Constant ambient noise up to `0.0006` RMS must not cause false-positive trigger storms.
4. **Hardware Tier Adaptation:** Low-tier devices with weaker microphones must dynamically scale sensitivity or threshold to ensure parity with flagship hardware.
5. **Simultaneous Audio Stream Safety:** Audio injection and diagnostic probes must not create memory leaks or lock up `ContinuousAudioStream`.

---

## Implementation Tasks

### Task 1: Adaptive Energy Floor & Decay-Based Confirmation

**Files:**
- Modify: `app/src/main/java/com/jarvis/wakeword/WakeWordConfig.kt`
- Modify: `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt`
- Test: `app/src/test/java/com/jarvis/wakeword/OpenWakeWordEnergyGateTest.kt`

**Interfaces:**
- `WakeWordConfig`:
  - `minEnergyRms: Double = 0.0003` (calibrated floor for mobile microphones, down from `0.005`)
  - `silenceFramesBeforeReset: Int = 5` (400ms grace period before temporal confirmation is cleared)
- `OpenWakeWordDetector`:
  - Tracks `consecutiveSilenceFrames: Int`
  - Only calls `confirmation.reset()` when `consecutiveSilenceFrames >= config.silenceFramesBeforeReset`
  - Allows neural head evaluation whenever features are ready and energy gate passes, without zeroing state on transient dips.

- [ ] **Step 1.1: Write failing unit tests in `OpenWakeWordEnergyGateTest.kt`**
  Add tests for:
  - Ambient mobile audio (RMS `0.0004`) passing the energy gate.
  - Quiet audio (RMS `0.0001`) failing the energy gate.
  - Brief silence frame (1–2 frames) preserving temporal history without calling `confirmation.reset()`.
  - Sustained silence (>= 5 frames) resetting confirmation.

- [ ] **Step 1.2: Run unit test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.wakeword.OpenWakeWordEnergyGateTest"`
  Expected: FAIL (assertion error on RMS floor and silence reset count).

- [ ] **Step 1.3: Update `WakeWordConfig.kt` and `OpenWakeWordDetector.kt`**
  - Lower default `minEnergyRms` to `0.0003` in `WakeWordConfig.kt`.
  - Add `silenceFramesBeforeReset: Int = 5` to `WakeWordConfig.kt`.
  - In `OpenWakeWordDetector.kt`, increment `consecutiveSilenceFrames` on low energy; only reset `confirmation` when `consecutiveSilenceFrames >= config.silenceFramesBeforeReset`. Reset counter to 0 on energy pass.

- [ ] **Step 1.4: Run unit test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.wakeword.OpenWakeWordEnergyGateTest"`
  Expected: BUILD SUCCESSFUL and all tests PASS.

- [ ] **Step 1.5: Commit changes**
  ```bash
  git add app/src/main/java/com/jarvis/wakeword/WakeWordConfig.kt \
          app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt \
          app/src/test/java/com/jarvis/wakeword/OpenWakeWordEnergyGateTest.kt
  git commit -m "fix(wakeword): calibrate acoustic energy floor and add silence decay window"
  ```

---

### Task 2: Temporal Confirmation Window & Threshold Recalibration

**Files:**
- Modify: `app/src/main/assets/openwakeword_models.json`
- Modify: `app/src/main/java/com/jarvis/wakeword/KwsTemporalConfirmation.kt`
- Test: `app/src/test/java/com/jarvis/wakeword/KwsTemporalConfirmationTest.kt`

**Interfaces:**
- `openwakeword_models.json`:
  - `hey_jarvis_v0.1.tflite`: threshold = `0.45` (tuned for high recall on conversational speech)
  - `jarvis.tflite`: threshold = `0.50` (recalibrated from `0.65` so ratio reaches 1.0 on standard pronunciation)
- `KwsTemporalConfirmation`:
  - `windowCount: Int = 4` (320ms temporal window, up from 3)
  - `requiredPositiveWindows: Int = 2`
  - `spikeThresholdMultiplier: Float = 1.15f` (down from 1.25f, allowing high-confidence single spikes to trigger)
  - `confidenceRetentionFactor: Float = 0.85f` (sliding score accumulator)

- [ ] **Step 2.1: Write failing tests in `KwsTemporalConfirmationTest.kt`**
  Add tests for:
  - Spike trigger at `1.15f * threshold`.
  - Two positive windows within a 4-frame window with an intervening low frame (e.g., `[0.95f, 0.40f, 1.05f]`).
  - Edge cases where scores hover at `0.90f` without false-triggering below threshold.

- [ ] **Step 2.2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.wakeword.KwsTemporalConfirmationTest"`
  Expected: FAIL on spike threshold and 4-frame windowing.

- [ ] **Step 2.3: Implement changes in `KwsTemporalConfirmation.kt` and `openwakeword_models.json`**
  - Update `openwakeword_models.json` thresholds: `"Hey Jarvis"` -> `0.45`, `"Jarvis"` -> `0.50`.
  - Update `KwsTemporalConfirmation.kt` to support configurable `spikeMultiplier = 1.15f` and window count defaults.

- [ ] **Step 2.4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.wakeword.KwsTemporalConfirmationTest"`
  Expected: PASS.

- [ ] **Step 2.5: Commit changes**
  ```bash
  git add app/src/main/assets/openwakeword_models.json \
          app/src/main/java/com/jarvis/wakeword/KwsTemporalConfirmation.kt \
          app/src/test/java/com/jarvis/wakeword/KwsTemporalConfirmationTest.kt
  git commit -m "feat(wakeword): calibrate KWS thresholds and soften temporal confirmation spike requirement"
  ```

---

### Task 3: Sensitivity Settings & Hardware Profile Integration

**Files:**
- Modify: `app/src/main/java/com/jarvis/wakeword/WakeWordConfig.kt`
- Modify: `app/src/main/java/com/jarvis/voice/WakeWordEngine.kt`
- Modify: `app/src/main/java/com/jarvis/voice/VoiceEngine.kt`
- Test: `app/src/test/java/com/jarvis/voice/WakeWordSensitivityTest.kt` (New)

**Interfaces:**
- `WakeWordConfig.fromSensitivity(sensitivity: String, isLowEnd: Boolean = false): WakeWordConfig`:
  - `"HIGH"`: `minEnergyRms = 0.00015`, `requiredPositiveWindows = 2`, `confirmationWindowCount = 4`
  - `"BALANCED"`: `minEnergyRms = 0.00030`, `requiredPositiveWindows = 2`, `confirmationWindowCount = 4`
  - `"LOW"`: `minEnergyRms = 0.00100`, `requiredPositiveWindows = 3`, `confirmationWindowCount = 5`
  - Low-end hardware adjustment: reduces `minEnergyRms` by 25% if AGC / microphone gain is known to be weaker.
- `WakeWordEngine.setSensitivity(sensitivity: String)`:
  - Reconfigures active detector configuration dynamically.
- `VoiceEngine.loadPreferences()`:
  - Passes `appSettings.wakeSensitivity` to `wakeEngine.setSensitivity(...)`.

- [ ] **Step 3.1: Write failing test in `WakeWordSensitivityTest.kt`**
  Verify that switching sensitivity between "HIGH", "BALANCED", and "LOW" correctly sets energy gates and confirmation window parameters in `WakeWordConfig`.

- [ ] **Step 3.2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.voice.WakeWordSensitivityTest"`
  Expected: FAIL (unresolved reference / method missing).

- [ ] **Step 3.3: Implement sensitivity mapper and dynamic reconfiguration**
  - Add factory method `WakeWordConfig.fromSensitivity(...)`.
  - Add `setSensitivity(sensitivity: String)` to `WakeWordEngine.kt`.
  - Connect `loadPreferences()` in `VoiceEngine.kt` to update `wakeEngine`.

- [ ] **Step 3.4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.voice.WakeWordSensitivityTest"`
  Expected: PASS.

- [ ] **Step 3.5: Commit changes**
  ```bash
  git add app/src/main/java/com/jarvis/wakeword/WakeWordConfig.kt \
          app/src/main/java/com/jarvis/voice/WakeWordEngine.kt \
          app/src/main/java/com/jarvis/voice/VoiceEngine.kt \
          app/src/test/java/com/jarvis/voice/WakeWordSensitivityTest.kt
  git commit -m "feat(voice): propagate UI wake sensitivity preference into WakeWordEngine"
  ```

---

### Task 4: Acoustic Audio Injection Probe & Diagnostic Tooling

**Files:**
- Modify: `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`
- Modify: `scripts/test_adb_features.py`
- Test: `app/src/test/java/com/jarvis/service/AdbBridgeEngineTest.kt`

**Interfaces:**
- `AdbBridgeEngine`:
  - Extend `wake_diagnostics` output to include `energyGateFloor`, `consecutiveSilence`, and `activeSensitivity`.
  - Add `inject_audio_frame` tool to accept base64-encoded PCM or synthetic impulse wave for automated acoustic pipeline verification over ADB.
- `scripts/test_adb_features.py`:
  - Add `V-05: Acoustic Audio Injection & KWS Evaluation` to verify that real PCM samples pass through the full neural detector via ADB.

- [ ] **Step 4.1: Write failing unit test in `AdbBridgeEngineTest.kt`**
  Verify that `wake_diagnostics` exposes the updated diagnostic fields and `inject_audio_frame` dispatches to `VoiceEngine`.

- [ ] **Step 4.2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.service.AdbBridgeEngineTest"`
  Expected: FAIL on missing diagnostic properties.

- [ ] **Step 4.3: Implement diagnostic enhancements in `AdbBridgeEngine.kt`**
  - Update `wake_diagnostics` data map with `energyFloor`, `wakeSensitivity`.
  - Implement `inject_audio_frame` handler to feed samples into `wakeEngine.acceptAudio(samples)`.

- [ ] **Step 4.4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests "com.jarvis.service.AdbBridgeEngineTest"`
  Expected: PASS.

- [ ] **Step 4.5: Commit changes**
  ```bash
  git add app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt \
          app/src/test/java/com/jarvis/service/AdbBridgeEngineTest.kt \
          scripts/test_adb_features.py
  git commit -m "feat(adb): add acoustic injection probe and enhanced telemetry to AdbBridgeEngine"
  ```

---

### Task 5: End-to-End Live Device Acoustic Verification & Benchmark

**Files:**
- Test / Verify on physical device: `LMK420CUQOLFLNU8B6` (LG LM-K420)

- [ ] **Step 5.1: Build debug APK and install on connected device**
  ```bash
  JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew assembleDebug
  adb -s LMK420CUQOLFLNU8B6 install -r app/build/outputs/apk/debug/app-debug.apk
  ```

- [ ] **Step 5.2: Launch service and query live acoustic diagnostics**
  ```bash
  adb -s LMK420CUQOLFLNU8B6 shell am start -n com.jarvis/.ui.MainActivity
  sleep 3
  adb -s LMK420CUQOLFLNU8B6 shell am broadcast -a com.jarvis.action.EXECUTE_TOOL --es tool wake_diagnostics
  ```
  Verify that `audioRms` is within expected operational range and `energyFloor` matches `0.00030`.

- [ ] **Step 5.3: Run full automated ADB Feature Test Suite**
  ```bash
  python3 scripts/test_adb_features.py --category voice
  ```
  Verify 100% PASS across all voice tests including V-01, V-02, V-03, and new injection tests.

- [ ] **Step 5.4: Live utterance logcat verification**
  Monitor logcat while uttering "Hey Jarvis" and "Jarvis" to confirm:
  - `openWakeWord candidate: Jarvis ratio=...` logs show candidate evaluation.
  - `openWakeWord CONFIRMED: phrase='Jarvis'` fires smoothly.
  - State transitions to `ACTIVE_LISTENING`.

---

## Verification & Acceptance Criteria

| Subsystem / Metric | Prior State (Failing) | Expected State (Fixed) | Verification Method |
|---|---|---|---|
| **Energy Gate Floor** | `0.005` RMS (-46 dBFS) | `0.0003` RMS (-70 dBFS) | Unit test + `wake_diagnostics` |
| **Silence Handling** | Wipes window on 1 quiet frame | Retains history across 5 frames | `OpenWakeWordEnergyGateTest` |
| **"Jarvis" Manifest Threshold**| `0.65` (requires >0.8125 spike) | `0.50` (matches "Hey Jarvis") | `openwakeword_models.json` check |
| **Confirmation Window** | 2 of 3 (>1.0 required) | 2 of 4 with 1.15x spike trigger | `KwsTemporalConfirmationTest` |
| **UI Sensitivity Connection** | Disconnected (only barge-in) | Controls KWS floor & windowing | `WakeWordSensitivityTest` |
| **ADB Automated Test Suite** | 4 voice tests | 5 voice tests (with audio injection) | `python3 scripts/test_adb_features.py` |
