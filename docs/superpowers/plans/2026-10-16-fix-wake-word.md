# Fix Wake Word Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Goal Description
Fix wake-word detection across the entire Jarvis audio pipeline so that saying "Hey Jarvis" or "Jarvis" reliably triggers the assistant in real-time. Specifically, resolve the Bluetooth SCO microphone capture hijacking bug that silences the phone's built-in microphone, fix the temporal confirmation algorithm in `KwsTemporalConfirmation` that discards positive detections, prevent 40ms alternating frame score clobbering, optimize microphone gain/routing on physical devices (like LG K42), and provide live ADB wake word diagnostics.

---

## User Review Required

> [!IMPORTANT]
> **Bluetooth Audio Routing Behavior**:
> Previously, if *any* Bluetooth audio device was connected (even media-only headphones like `ZEB-THUNDER`), `ContinuousAudioStream` unconditionally forced `AudioRecord` to route to `TYPE_BLUETOOTH_SCO`. On Android devices, this silenced the phone's microphone because no SCO audio was streaming.
> With this fix, `ContinuousAudioStream` will strictly use the **phone's built-in microphone** (or wired headset if plugged in) for wake-word listening, and will only switch to Bluetooth SCO when an active two-way conversation begins (`isInteractionActive == true`).

> [!TIP]
> **Single-Frame High-Confidence Thresholding**:
> In addition to requiring multi-window confirmation, `KwsTemporalConfirmation` will now immediately confirm if a single frame achieves a high-confidence spike (`ratio >= 1.25f`), ensuring snappy detection when saying "Hey Jarvis" clearly without waiting for multi-frame trailing decay.

---

## Open Questions

None blocking. The default threshold in `openwakeword_models.json` is 0.35 for "Hey Jarvis" and 0.40 for "Jarvis", which are well-tuned for openWakeWord TFLite heads.

---

## Proposed Changes

### Component 1: Audio Capture & Preferred Device Routing

#### [MODIFY] `app/src/main/java/com/jarvis/voice/audio/ContinuousAudioStream.kt`
- Add `private var isBluetoothScoActive: Boolean = false`.
- Update `setBluetoothScoActive(active: Boolean)` to update this state and trigger `updatePreferredDevice()`.
- Fix `createAndStartAudioRecord()` and `updatePreferredDevice()`:
  - Do NOT select `TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET` unless `isBluetoothScoActive` is `true` AND `audioManager.isBluetoothScoOn` is `true`.
  - If `isBluetoothScoActive` is false, check for `TYPE_WIRED_HEADSET` or `TYPE_USB_HEADSET`. If none, pass `null` to `setPreferredDevice(null)` so Android defaults to the built-in microphone (`MediaRecorder.AudioSource.VOICE_RECOGNITION` / `MIC`).
- Add mic audio level energy diagnostics (`fun getLatestRms(): Double`).

---

### Component 2: KWS Temporal Confirmation & Chunk Alignment

#### [MODIFY] `app/src/main/java/com/jarvis/wakeword/KwsTemporalConfirmation.kt`
- Fix `add(score: Float, threshold: Float): Float`:
  1. Add `score` to `scores` ring buffer.
  2. If `requiredPositiveWindows == 1` and `score >= threshold`: return `score`.
  3. If `score >= threshold * 1.25f` (high-confidence single-frame spike): return `score`.
  4. Count positive frames in `scores`: `val positives = scores.filter { it >= threshold }`.
  5. If `positives.size >= requiredPositiveWindows`:
     - Return `positives.maxOrNull() ?: score` (DO NOT return the trailing frame's score, which is often post-utterance silence < threshold).
     - Clear `scores` to prevent duplicate re-triggers.
  6. Return `0f` otherwise.

#### [MODIFY] `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt`
- Fix chunk alignment:
  - Only update `latestScore` when a full 1280-sample frame (`FRAME_SAMPLES`) is processed by `processChunk()`. Do not overwrite `latestScore` with 0 on 640-sample partial reads.
  - Return the confirmed ratio from `pushAudio` when confirmed.
- Enhance diagnostic logging: log `bestPhrase` and `bestRatio` when ratio exceeds `0.5f` (half threshold) so developers can observe near-miss scores in logcat.

#### [MODIFY] `app/src/main/java/com/jarvis/voice/WakeWordEngine.kt`
- Preserve `latestScore` across partial audio reads.
- Ensure `getDiagnostics()` accurately reports `audioRms`, `latestScore`, `threshold`, and `detectorMode`.

---

### Component 3: ADB Wake Word Diagnostics & Testing Probe

#### [MODIFY] `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`
- Add wake diagnostics probe in `handleBroadcast`:
  - When tool is `wake_diagnostics`, query `wakeEngine.getDiagnostics()` and `continuousAudioStream.isRecording()`.
  - Format response: `STATUS:SUCCESS|mode=NEURAL_READY|score=...|rms=...|mic=...`
- Add wake trigger tool:
  - When tool is `trigger_wake`, dispatch simulated phrase (e.g. `Hey Jarvis`) through `voiceEngine.onWakeDetectedDirect(phrase)` to verify downstream ASR/TTS flow.

---

## Verification Plan

### Automated Tests
1. **Unit Tests for Temporal Confirmation:**
   - Run: `python3 -c "import subprocess; subprocess.run(['./gradlew', 'testDebugUnitTest', '--tests', 'com.jarvis.wakeword.KwsTemporalConfirmationTest'])"`
   - Assert: Positive window confirmation returns max score even if trailing frame drops to 0.2f.
   - Assert: High-confidence single-frame hit (>= 1.25f) triggers immediately.
2. **Unit Tests for Audio Routing:**
   - Assert: `ContinuousAudioStream` does NOT select Bluetooth SCO when `isBluetoothScoActive` is false.
3. **Asset Verification:**
   - Run: `python3 verify_wakeword_assets.py` (Must report all heads and feature models OK).

### Manual Verification on Connected Device (`LM-K420`)
1. Rebuild and install APK via `./scripts/install_on_phone.sh`.
2. Inspect logcat:
   `adb logcat -d -s VoiceEngine:V WakeWordEngine:V ContinuousAudioStream:V`
   Verify:
   - `ContinuousAudioStream started successfully at 16000Hz`
   - Preferred device is Built-in Mic (not BTCVSD/SCO).
   - `AudioALSACaptureDataProviderBTCVSD` skip messages cease.
3. Run ADB Wake Diagnostics Probe:
   `adb shell am broadcast -n com.jarvis/.service.CommandReceiver -a com.jarvis.action.EXECUTE_TOOL --es tool wake_diagnostics`
   Verify: reports active recording and valid audio RMS.
4. Speak "Hey Jarvis" or "Jarvis" near the phone:
   Verify: `VOICE session=... event=WAKE_DETECTED phrase="Hey Jarvis"` appears in logcat, screen wakes/audio ducks, and assistant begins listening.
