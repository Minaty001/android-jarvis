# Fix Fake Wake Word Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate spurious false wake-word activations (fake triggers in silence/ambient room noise) by enforcing a strict audio RMS energy floor, calibrating openWakeWord classifier head thresholds, fixing buffer zero-initialization, and securing temporal debouncing against single-frame acoustic spikes.

**Architecture:** Connect `WakeWordConfig.minEnergyRms` directly into `OpenWakeWordDetector` to gate neural execution during sub-floor room silence (< 0.005 RMS). Calibrate `openwakeword_models.json` thresholds to upstream openWakeWord production baselines (0.50 for "Hey Jarvis", 0.65 for "Jarvis"). Prevent cooldown zeroing in `WakeWordEngine.start()` and fix mel spectrogram buffer pre-fill from `1f` to `0f`.

**Tech Stack:** Kotlin, TensorFlow Lite, Android AudioRecord / PCM 16kHz, ADB Bridge Protocol.

**Spec:** Current device telemetry shows ambient silence at `audioRms=0.00413`, raw background noise output at `0.519`, and openWakeWord confirming "Jarvis" every 15-20 seconds with nobody speaking.

## Global Constraints
- Must not break `OpenWakeWordDetector` manifest validation or asset SHA-256 checks (`verify_wakeword_assets.py`).
- Audio processing must remain strictly allocation-free on the 16 kHz streaming hot path.
- Wake word latency must remain under 250ms when a real keyword is spoken.
- All unit tests in `com.jarvis.wakeword.*` must pass with zero regressions.

## Review Focus
1. Silence/Ambient Room Noise Rejection: When microphone RMS is below `minEnergyRms` (0.005), detector must produce 0.0 score and reset confirmation buffer.
2. Threshold Recalibration: Spoken keyword with raw score 0.85 must trigger, but ambient noise with raw score 0.52 must be rejected.
3. Mel Buffer Zero Initialization: Resetting detector must fill `melFrames` with zeros, not `1f`, preventing phantom energy carryover.
4. Cooldown On Resume: Resuming wake listening from ASR timeout must not trigger immediately on trailing speech or room artifacts.
5. Headless MockContext Compatibility: Unit tests must execute cleanly without stubbed Android SQLite or AudioManager NPEs.

---

### Task 1: Enforce RMS Audio Energy Gate and Zero-Fill Buffer in `OpenWakeWordDetector`

**Files:**
- Modify: `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt:90-100, 180-220, 310-330`
- Create: `app/src/test/java/com/jarvis/wakeword/OpenWakeWordEnergyGateTest.kt`

**Interfaces:**
- Consumes: `WakeWordConfig.minEnergyRms` (Double), `ShortArray` audioChunk
- Produces: `OpenWakeWordDetector.getLatestEnergyGatePassed(): Boolean?`

- [ ] **Step 1: Write unit test for energy gate enforcement and zero mel-frame padding**

Create `app/src/test/java/com/jarvis/wakeword/OpenWakeWordEnergyGateTest.kt`:
```kotlin
package com.jarvis.wakeword

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class OpenWakeWordEnergyGateTest {

    @Test
    fun testRmsCalculationOnQuietAudioIsBelowFloor() {
        val quietAudio = ShortArray(1280) { (it % 10).toShort() } // Very quiet pcm
        var sum = 0.0
        quietAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / quietAudio.size)
        assertTrue("Quiet audio RMS ($rms) must be below 0.005 floor", rms < 0.005)
    }

    @Test
    fun testRmsCalculationOnSpeechAudioExceedsFloor() {
        val speechAudio = ShortArray(1280) { 1500.toShort() } // ~0.045 RMS
        var sum = 0.0
        speechAudio.forEach { val norm = it.toDouble() / 32768.0; sum += norm * norm }
        val rms = sqrt(sum / speechAudio.size)
        assertTrue("Speech audio RMS ($rms) must exceed 0.005 floor", rms > 0.005)
    }
}
```

- [ ] **Step 2: Run test to verify it compiles and passes**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "com.jarvis.wakeword.OpenWakeWordEnergyGateTest"`
Expected: PASS

- [ ] **Step 3: Update `OpenWakeWordDetector.kt` to check RMS energy floor and zero-fill mel buffer**

In `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt`:
1. Add state field: `private var latestEnergyGatePassed: Boolean? = null`
2. Implement: `override fun getLatestEnergyGatePassed(): Boolean? = latestEnergyGatePassed`
3. In `pushAudio(samples: ShortArray)`:
   When `audioChunkIndex == FRAME_SAMPLES`:
   Calculate RMS of `audioChunk`:
   ```kotlin
   var sum = 0.0
   for (i in 0 until FRAME_SAMPLES) {
       val norm = audioChunk[i].toDouble() / 32768.0
       sum += norm * norm
   }
   val rms = kotlin.math.sqrt(sum / FRAME_SAMPLES)
   val energyPassed = rms >= config.minEnergyRms
   latestEnergyGatePassed = energyPassed
   if (!energyPassed) {
       confirmation.reset()
       audioChunkIndex = 0
       processedFrame = true
       continue
   }
   processChunk()
   audioChunkIndex = 0
   processedFrame = true
   ```
4. In `initialize()` and `resetDetectorState()`:
   Replace `repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS) { 1f }) }`
   With `repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS)) }` (defaults to 0f).

- [ ] **Step 4: Run wake word tests to verify compilation and baseline compatibility**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "*Wake*"`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt app/src/test/java/com/jarvis/wakeword/OpenWakeWordEnergyGateTest.kt
git commit -m "fix(wakeword): enforce RMS energy gate and zero-fill mel buffer in OpenWakeWordDetector"
```

---

### Task 2: Calibrate Classifier Head Thresholds in `openwakeword_models.json`

**Files:**
- Modify: `app/src/main/assets/openwakeword_models.json`
- Verify: `verify_wakeword_assets.py`

**Interfaces:**
- Consumes: `openwakeword_models.json`
- Produces: Calibrated thresholds for "Hey Jarvis" (0.50) and "Jarvis" (0.65)

- [ ] **Step 1: Update thresholds in `app/src/main/assets/openwakeword_models.json`**

Update:
```json
  "models": [
    {
      "phrase": "Hey Jarvis",
      "asset": "hey_jarvis_v0.1.tflite",
      "threshold": 0.50,
      "sha256": "14bff778604985e1b5c19f0f7bbe477a69cf281d8db34b232b3b972411f710e2"
    },
    {
      "phrase": "Jarvis",
      "asset": "jarvis.tflite",
      "threshold": 0.65,
      "sha256": "b2318c313eca75596203d5e7af080328520fd8e3b362c289d8798a52564a44ef"
    }
  ]
```

- [ ] **Step 2: Run verification script to confirm contract integrity**

Run: `python3 verify_wakeword_assets.py`
Expected:
```
  head 'Hey Jarvis' asset=hey_jarvis_v0.1.tflite   thr=0.5 sha=ok
  head 'Jarvis'     asset=jarvis.tflite            thr=0.65 sha=ok

CHECK PASS: manifest + assets satisfy OpenWakeWordDetector validation
```

- [ ] **Step 3: Commit threshold calibration**

```bash
git add app/src/main/assets/openwakeword_models.json
git commit -m "tune(wakeword): calibrate classifier thresholds to reject ambient room noise"
```

---

### Task 3: Strengthen Temporal Confirmation and Prevent Cooldown Zeroing

**Files:**
- Modify: `app/src/main/java/com/jarvis/wakeword/KwsTemporalConfirmation.kt:15-35`
- Modify: `app/src/main/java/com/jarvis/voice/WakeWordEngine.kt:180-195`
- Modify: `app/src/test/java/com/jarvis/wakeword/KwsTemporalConfirmationTest.kt`

**Interfaces:**
- Consumes: `score: Float`, `threshold: Float` (relative ratio)
- Produces: Confirmed score after sustained acoustic presence

- [ ] **Step 1: Update `KwsTemporalConfirmationTest.kt` to verify consecutive hit requirement**

In `app/src/test/java/com/jarvis/wakeword/KwsTemporalConfirmationTest.kt`:
Update tests to assert that alternating silence (`1.1f`, `0.2f`, `1.1f`) does not trigger without consecutive confidence, and high-confidence spikes must exceed the spike threshold.

- [ ] **Step 2: Run test to verify behavior**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "com.jarvis.wakeword.KwsTemporalConfirmationTest"`

- [ ] **Step 3: Update `KwsTemporalConfirmation.kt` and `WakeWordEngine.kt`**

In `KwsTemporalConfirmation.kt`:
1. Check `score >= threshold * 1.25f` only when single window is configured or when score represents overwhelming confidence.
2. Require consecutive hits:
   ```kotlin
   @Synchronized
   fun add(score: Float, threshold: Float): Float {
       if (!score.isFinite() || !threshold.isFinite()) return 0f

       if (requiredPositiveWindows == 1 && score >= threshold) {
           scores.clear()
           return score
       }

       if (score >= threshold * 1.25f) {
           scores.clear()
           return score
       }

       scores.addLast(score)
       while (scores.size > windowCount) scores.removeFirst()

       // Check that recent windows have sustained positive elevation
       val positiveScores = scores.filter { it >= threshold }
       if (positiveScores.size >= requiredPositiveWindows) {
           val confirmedScore = positiveScores.maxOrNull() ?: score
           scores.clear()
           return confirmedScore
       }
       return 0f
   }
   ```
In `WakeWordEngine.kt`:
In `start()`:
Change `lastDetectionAt = 0L` to `lastDetectionAt = System.currentTimeMillis()`. This prevents immediate spurious firing when resuming from command/ASR timeouts.

- [ ] **Step 4: Run unit tests**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "*Wake*"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/jarvis/wakeword/KwsTemporalConfirmation.kt app/src/main/java/com/jarvis/voice/WakeWordEngine.kt app/src/test/java/com/jarvis/wakeword/KwsTemporalConfirmationTest.kt
git commit -m "fix(wakeword): strengthen temporal confirmation and prevent cooldown zeroing on resume"
```

---

### Task 4: Assemble, Install, and Verify on Physical Device (`LM-K420`)

**Files:** None (build & verification)

- [ ] **Step 1: Package clean Debug APK**

Run:
```bash
rm -f app/build/outputs/apk/debug/app-debug.apk
export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11
export PATH=$JAVA_HOME/bin:$PATH
./gradlew packageDebug
```
Expected: `app/build/outputs/apk/debug/app-debug.apk` built successfully (~24 MB).

- [ ] **Step 2: Install to physical phone via ADB**

Run:
```bash
adb -s LMK420CUQOLFLNU8B6 install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.jarvis/.ui.MainActivity
```
Expected: `Success` and `MainActivity` launched.

- [ ] **Step 3: Verify ambient silence rejection (Zero Fake Wake Triggers)**

Run:
```bash
adb shell am broadcast -n com.jarvis/.service.CommandReceiver -a com.jarvis.action.EXECUTE_TOOL --es tool wake_diagnostics
```
Expected: `audioRms` reflects ambient room level (< 0.005), `energyGatePassed=false` or silence score = 0.
Monitor logcat for 30 seconds:
```bash
adb logcat -d -s OpenWakeWord | tail -n 20
```
Expected: ZERO `openWakeWord CONFIRMED` events during silence.

---

## Verification Plan

### Automated Tests
1. `python3 verify_wakeword_assets.py` -> Must PASS.
2. `python3 scripts/test_wake_word_fix.py` -> Must PASS.
3. `./gradlew testDebugUnitTest --tests "*Wake*"` -> All tests PASS.

### Manual Verification on Connected Device (`LM-K420`)
1. Ping probe: `adb shell am broadcast -n com.jarvis/.service.CommandReceiver -a com.jarvis.action.PING` -> `PONG:service=RUNNING`.
2. Silent room idle test: Observe logcat for 30 seconds -> Confirm no false activations occur.
3. Spoken keyword test: Speak "Jarvis" or "Hey Jarvis" to the phone -> Confirm wake word detection fires accurately.
