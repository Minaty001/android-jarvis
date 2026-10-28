# Wake Word & Listening Feature Rebuild Design

**Date:** 2026-10-26  
**Status:** Approved by Builder Review / Awaiting User Approval  
**Topic:** Acoustic Wake Word Re-calibration & Robust Listening Pipeline Rebuild

---

## 1. Intent and Why

### The Problem
The Jarvis voice system currently suffers from two critical UX defects:
1. **Spurious False Wake Word Activations ("Ghost Triggers"):** In quiet or normal indoor environments (HVAC noise, conversation, TV in background), the wake word engine repeatedly false-triggers every 15–30 seconds without anyone speaking the keyword.
2. **Clunky / Broken Listening Transitions:** Following wake-up, the command listening pipeline clips user speech, experiences microphone lockups or audio collisions (when voice acknowledgment "Yes boss" speaks over the microphone), and maintains an excessively long 8-second silence hangover, making the assistant feel sluggish and unresponsive.

### Why It Happened (Root Cause Analysis)
- **Acoustic Energy Gate Permeability:** In a previous change, `minEnergyRms` in `WakeWordConfig` was reduced from `0.005` down to `0.00030` (and `0.00015` on HIGH sensitivity). Normal indoor room ambient noise measures `0.0004` to `0.0025` RMS, rendering the silence filter completely inert and feeding background noise continuously into the neural classifier.
- **Overly Low Classifier Thresholds:** In `app/src/main/assets/openwakeword_models.json`, "Hey Jarvis" is set to `0.45` and "Jarvis" to `0.50`. In openWakeWord, general conversational speech, phonetically similar words (e.g. *service*, *harvest*, *always*), and room noise regularly score in the `0.40–0.55` range.
- **Temporal Debounce Single-Frame Spike Bypass:** In `KwsTemporalConfirmation.kt`, the logic `if (score >= threshold * spikeMultiplier)` immediately fires on a single 80 ms frame if score ratio $\ge 1.15$. Because $0.52 / 0.45 = 1.155$, any transient 80 ms sound with a raw score of just 0.52 triggers an instant wake event, bypassing multi-frame confirmation. A spoken keyword requires 500–900 ms (6–11 frames); firing on a single 80 ms window causes false alarm storms.
- **Microphone Handoff & Acoustic Collision:** During wake transitions, if `wakeAcknowledgment` is "VOICE" or "BOTH", `speakUtterance("Yes boss")` was called concurrently with ASR initialization. The speaker output bled into the microphone. Furthermore, when `AndroidSpeechRecognizerEngine` is used, the hardware suspension of `ContinuousAudioStream` introduces latency that clips same-breath commands (e.g., *"Jarvis play music"*).

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Eliminate False Wake Activations:** Rejection of ambient room silence, HVAC/fan hum, television dialogue, and non-target conversational speech with $< 1$ false trigger per 12 hours of passive listening in typical room conditions.
- **Retain Responsive Wake-up on Real Keyword:** Real user utterances of *"Hey Jarvis"* or *"Jarvis"* spoken within 0.5–2 meters must trigger consistently with latency $\le 250$ ms from word completion.
- **Clean Listening Transition & Zero Audio Collision:**
  - Earcon/chime plays with proper audio ducking.
  - If voice response (*"Yes boss"*) is enabled, ASR microphone listening only commences *after* the TTS speech has completed.
  - Microphone handover is optimized between `ContinuousAudioStream` and Android `SpeechRecognizer`.
- **Snappy End-of-Speech Finalization:** When the user finishes speaking, ASR finalization triggers within 700–800 ms of silence (configured via `RecognizerIntent` silence extras and coroutine watchdog), eliminating the sluggish 8000 ms lag.
- **Clear Sensitivity Calibration:** UI settings (`HIGH`, `BALANCED`, `LOW`) map to well-defined acoustic and neural thresholds.

### Non-Goals
- Replacing openWakeWord with an entirely new ML framework or cloud wake-word API (we keep the on-device openWakeWord TFLite architecture).
- Retraining the base openWakeWord melspectrogram and embedding models from scratch (the frontend models remain `melspectrogram.tflite` and `embedding_model.tflite`).

### Anti-Goals
- Do not make the wake word so deaf that users have to shout or repeat the wake word multiple times.
- Do not block the Android UI thread during audio routing or model inference.
- Do not introduce memory allocations on the 16 kHz audio streaming hot path.

---

## 3. Architecture & Reconstruction Design

### A. Wake Word Engine & Temporal Confirmation Rebuild

#### 1. Calibrated Acoustic Energy Floor (`WakeWordConfig`)
- Re-tune `minEnergyRms` in `WakeWordConfig`:
  - `BALANCED` (default): `0.00150` (rejects typical room silence $\le 0.0010$, accepts normal speech $\ge 0.0020$).
  - `HIGH`: `0.00080` (for quiet speakers or distant placement).
  - `LOW`: `0.00300` (for noisy environments with continuous background TV/music).
- Low-end hardware scaling: On detected low-end devices (`HardwareProfileManager.isLowEndDevice`), adjust threshold by $\times 0.85$ to account for weaker microphone preamps without opening the gate to pure noise.
- Silence decay: When energy falls below floor for $\ge 3$ consecutive frames (~240 ms), reset confirmation buffer.

#### 2. Upstream-Aligned Neural Thresholds (`openwakeword_models.json`)
- Upstream openWakeWord production thresholds:
  - **"Hey Jarvis" (`hey_jarvis_v0.1.tflite`):** Raise threshold from `0.45` to `0.60`.
  - **"Jarvis" (`jarvis.tflite`):** Raise threshold from `0.50` to `0.65`.
- Manifest SHA-256 hashes verified and maintained in assets.

#### 3. Strict Multi-Frame Persistence in `KwsTemporalConfirmation`
- **Eliminate the Single-Frame Spike Bypass:**
  - Real human speech for "Hey Jarvis" spans 600–900 ms (7–11 consecutive 80 ms windows). Firing on a single 80 ms chunk was the primary cause of phantom activations.
  - Require at least **2 positive windows out of 4** (or 3 out of 5 for LOW sensitivity) where raw neural score exceeds the model threshold.
  - Bypassing multi-frame confirmation on a single 80 ms frame is completely removed.
- Add post-activation cooldown of $1500$ ms to prevent echo re-triggering from trailing speech.

---

### B. Listening Pipeline & Audio Routing Rebuild

#### 1. Wake Acknowledgment & ASR Sequencing
- In `VoiceEngine.kt`:
  - Default wake feedback: Ultra-low latency earcon chime (`playWakeTone()`, $< 100$ ms).
  - When `wakeAcknowledgment == "VOICE"` or `"BOTH"`:
    - Keep audio route `MUTED` while speaking *"Yes boss"*.
    - Await completion of `speakUtterance("Yes boss")` before starting ASR listening.
    - Only after TTS finishes, play the listening earcon (`playListeningTone()`) and begin ASR listening.
    - This completely prevents Jarvis from hearing and transcribing its own voice.

#### 2. Microphone Handover & ASR Routing
- For `AndroidSpeechRecognizerEngine` (production):
  - Suspends `ContinuousAudioStream` hardware cleanly before starting `SpeechRecognizer`.
  - In `AndroidSpeechRecognizerEngine`, configure `RecognizerIntent` silence extras:
    - `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 800L`
    - `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 800L`
    - `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 1500L`
  - In `VoiceEngine.kt`, adjust listening watchdog: initial silence timeout is 5000 ms (down from 8000 ms). Once partial speech is received, silence timeout drops to 1200 ms to finalize quickly.
- For raw-audio ASR engines (`usesRawAudio = true` / test stubs):
  - Retain `ContinuousAudioStream` and route pre-roll audio from `RollingPcmBuffer` directly.

#### 3. Clean Failure Recovery & Reset
- When ASR finishes with empty or unrecognized input:
  - Cancel listening jobs cleanly.
  - Reset `wakeTransitionGate` and `commandFinalizationGate`.
  - Return to `WAITING_FOR_WAKE` without lingering retry loops.

---

## 4. Hardware & System Constraints

- **Audio Specs:** 16,000 Hz, 16-bit PCM, mono (80 ms chunk = 1280 samples).
- **Execution Budget:** Neural KWS inference per 80 ms chunk must complete in $< 20$ ms on Cortex-A53 / low-end ARM cores.
- **Compatibility:** Android API 26+ (tested on Android 12, LG K42 / LM-K420).
- **Tooling:** Gradle tests run with Java 17 (`/home/shanu/jdk/jdk-17.0.13+11`).

---

## 5. Verification & Testing Strategy

1. **Unit Tests:**
   - `OpenWakeWordEnergyGateTest`: Validate quiet ambient noise ($< 0.0010$ RMS) is rejected, conversational speech ($\ge 0.0020$ RMS) passes.
   - `KwsTemporalConfirmationTest`: Verify single-frame transient spikes do NOT trigger wake; sustained positive frames across windows DO trigger wake.
   - `WakeWordSensitivityTest`: Verify HIGH, BALANCED, and LOW sensitivity parameter mappings.
   - `ListeningOptimizationTest`: Verify ASR silence intent extras and non-blocking state recovery.
2. **Deterministic Audio Injection via ADB / Script:**
   - Inject silence / fan noise PCM -> Confirm 0 wake events.
   - Inject positive wake-word audio PCM -> Confirm 100% trigger rate within cooldown window.
3. **End-to-End Build Verification:**
   - `./gradlew testDebugUnitTest` passing with zero regressions.
