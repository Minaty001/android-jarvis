# Wake Word Detection Optimization & Acoustic Pipeline Fix Plan (Completed)

**Goal:** Transform the wake word acoustic pipeline so hands-free wake word recognition ("Jarvis" and "Hey Jarvis") triggers reliably and promptly in natural indoor acoustic conditions on Android devices (including low-end / budget hardware like the LG LM-K420) without high false-positive rates.

**Status:** ✅ Fully Implemented, Tested, Installed, and Verified on Connected Device (`LMK420CUQOLFLNU8B6`).

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
   - `KwsTemporalConfirmation` required either a spike `>= 1.25 * threshold` (raw neural score `>= 0.8125`) or 2 of 3 consecutive windows `>= 1.0 * threshold` (raw score `>= 0.65`).

4. **Disconnected Sensitivity Settings:**
   - The user preference `wakeSensitivity` in `UiPreferencesStore` ("HIGH", "BALANCED", "LOW") was only wired into `BargeInDetector`, leaving `WakeWordEngine` locked into fixed parameters.

---

## Implemented Tasks & Progress

### Task 1: Adaptive Energy Floor & Decay-Based Confirmation
- [x] **Step 1.1: Write failing unit tests in `OpenWakeWordEnergyGateTest.kt`**
- [x] **Step 1.2: Run unit test to verify failure**
- [x] **Step 1.3: Update `WakeWordConfig.kt` and `OpenWakeWordDetector.kt`**
  - Lowered default `minEnergyRms` from `0.005` to `0.0003` (-70 dBFS).
  - Added `silenceFramesBeforeReset: Int = 5` (~400ms decay window) so brief syllable dips do not destroy temporal history.
- [x] **Step 1.4: Run unit test to verify pass** (`OpenWakeWordEnergyGateTest`: PASS)
- [x] **Step 1.5: Commit changes** (`6cce888`)

### Task 2: Temporal Confirmation Window & Threshold Recalibration
- [x] **Step 2.1: Write failing tests in `KwsTemporalConfirmationTest.kt`**
- [x] **Step 2.2: Run test to verify failure**
- [x] **Step 2.3: Implement changes in `KwsTemporalConfirmation.kt` and `openwakeword_models.json`**
  - Updated manifest thresholds: `"Hey Jarvis"` -> `0.45`, `"Jarvis"` -> `0.50` in `openwakeword_models.json`.
  - Added configurable `spikeMultiplier = 1.15f` and `windowCount = 4` in `KwsTemporalConfirmation.kt`.
- [x] **Step 2.4: Run test to verify pass** (`KwsTemporalConfirmationTest`: PASS)
- [x] **Step 2.5: Commit changes** (`2ea80e7`)

### Task 3: Sensitivity Settings & Hardware Profile Integration
- [x] **Step 3.1: Write failing test in `WakeWordSensitivityTest.kt`**
- [x] **Step 3.2: Run test to verify failure**
- [x] **Step 3.3: Implement sensitivity mapper and dynamic reconfiguration**
  - Added `WakeWordConfig.fromSensitivity(sensitivity, isLowEnd)` factory.
  - Added `setSensitivity(sensitivity)` and `getConfig()` to `WakeWordEngine.kt`.
  - Connected `loadPreferences()` in `VoiceEngine.kt` to forward `appSettings.wakeSensitivity`.
  - Added `HardwareProfileManager.isLowEndDevice(context)` scaling (25% lower energy floor on constrained microphones).
- [x] **Step 3.4: Run test to verify pass** (`WakeWordSensitivityTest`: PASS)
- [x] **Step 3.5: Commit changes** (`684660c`)

### Task 4: Acoustic Audio Injection Probe & Diagnostic Tooling
- [x] **Step 4.1: Write failing unit test in `AdbBridgeEngineTest.kt`**
- [x] **Step 4.2: Run test to verify failure**
- [x] **Step 4.3: Implement diagnostic enhancements in `AdbBridgeEngine.kt`**
  - Added `energyFloor` and `silenceDecayLimit` to `wake_diagnostics`.
  - Implemented `inject_audio_frame` tool to accept synthetic PCM/sine waves over ADB and feed into `WakeWordEngine.acceptAudio`.
  - Added `V-04: Acoustic Audio Injection & KWS Evaluation` to `scripts/test_adb_features.py`.
- [x] **Step 4.4: Run test to verify pass** (`AdbBridgeEngineTest`: PASS)
- [x] **Step 4.5: Commit changes** (`d1be6ee`)

### Task 5: End-to-End Live Device Acoustic Verification & Benchmark
- [x] **Step 5.1: Build debug APK and install on connected device**
  - `assembleDebug` completed in 59s.
  - Streamed install succeeded on `LMK420CUQOLFLNU8B6` (LG LM-K420, Android 12, API 31).
- [x] **Step 5.2: Launch service and query live acoustic diagnostics**
  - Telemetry: `mode=NEURAL_READY,modelAvailable=true,audioRms=0.00024,energyFloor=0.00023,silenceDecayLimit=5,isRecording=true,isHealthy=true,voiceState=WAITING_FOR_WAKE`.
- [x] **Step 5.3: Run full automated ADB Feature Test Suite**
  - `python3 scripts/test_adb_features.py --category voice`: **5 / 5 PASS (100%)**.
- [x] **Step 5.4: Live utterance logcat verification**
  - Verified live neural activations on physical device:
    ```
    D OpenWakeWord: openWakeWord candidate: Jarvis ratio=1.1069007 (thr=0.5)
    D OpenWakeWord: openWakeWord candidate: Jarvis ratio=1.3628726 (thr=0.5)
    I OpenWakeWord: openWakeWord CONFIRMED: phrase='Jarvis' score=1.3628726
    I WakeWordEngine: Wake event accepted for 'Jarvis' (score=1.3628726)
    ```

---

## Verification Summary

| Subsystem / Metric | Prior State | Fixed State | Result |
|---|---|---|---|
| **Energy Gate Floor** | `0.005` RMS (-46 dBFS) | `0.0003` RMS (-70 dBFS, 0.00023 on low-end) | ✅ Calibrated & Verified |
| **Silence Handling** | Wiped window on 1 quiet frame | Retains history across 5 frames (~400ms) | ✅ Tested & Passed |
| **"Jarvis" Manifest Threshold** | `0.65` (requires >0.8125 spike) | `0.50` (matches "Hey Jarvis") | ✅ Calibrated & Verified |
| **Confirmation Window** | 2 of 3 (>1.0 required) | 2 of 4 with 1.15x spike trigger | ✅ Tested & Passed |
| **UI Sensitivity Connection** | Disconnected | Controls KWS floor & windowing dynamically | ✅ Tested & Passed |
| **ADB Audio Injection Test** | N/A | Automated V-04 PCM injection test | ✅ 100% Pass Rate |
| **Live Device Wake Detection** | 0% trigger rate | Triggered and confirmed hands-free | ✅ Verified in Logcat |
