# Code Reduction, Compression, and Performance Optimization Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Goal Description
Drastically reduce codebase bloat, shrink APK package size by **~82%** (from 83 MB down to ~15 MB), eliminate high-frequency memory allocations (16,000 boxed objects/sec) in the continuous audio/wake-word loop, and accelerate boot and inference speeds on physical Android devices (specifically ARM Cortex-A53 devices like the LG K42).

---

## User Review Required

> [!IMPORTANT]
> **Removal of Unused 86 MB Binary Assets**:
> 1. `app/libs/sherpa-onnx.aar` (**48 MB**): A native library containing `libonnxruntime.so` that is **not referenced or imported anywhere** in `app/src/main/java`. Jarvis uses TensorFlow Lite (`org.tensorflow:tensorflow-lite`) for all on-device neural models (openWakeWord, Conformer speaker verification, VAD).
> 2. `app/src/main/assets/models/speaker.onnx` (**38 MB**): An ONNX model that is also **never loaded anywhere** in the codebase.
> Removing these cuts **86 MB** of dead weight, reducing APK size from **83 MB to ~15 MB** and accelerating ADB install time from ~15s to ~2s.

> [!TIP]
> **Hot-Path Allocation Elimination**:
> In `OpenWakeWordDetector.kt`, `rawPcm` is currently an `ArrayDeque<Short>`. In Kotlin, every element added to a generic collection is boxed into a `java.lang.Short` heap object. At 16,000 samples per second, this creates **16,000 heap allocations every second**, triggering the Android garbage collector and the frequent `onTrimMemory: level=15` warnings observed on the LG K42.
> Replacing this with a flat circular primitive `ShortArray` completely eliminates these allocations.

---

## Open Questions

None. All proposed removals have been verified to have zero active references in the codebase.

---

## Proposed Changes

### Component 1: Bloat Removal & Asset Pruning (-86 MB)

#### [DELETE] `app/libs/sherpa-onnx.aar`
- 48 MB unused native library.

#### [DELETE] `app/src/main/assets/models/speaker.onnx`
- 38 MB unused ONNX model file.

#### [DELETE] `app/src/main/java/com/jarvis/wakeword/DsCnnWakeWordDetector.kt`
- ~300 lines of obsolete, unreferenced legacy detector code.

#### [DELETE] `app/src/main/java/com/jarvis/wakeword/MfccExtractor.kt`
- ~180 lines of legacy MFCC extraction only used by `DsCnnWakeWordDetector`.

#### [DELETE] `app/src/test/java/com/jarvis/wakeword/MfccParityTest.kt`
- Obsolete test for deleted MFCC extractor.

#### [MODIFY] `app/build.gradle.kts`
- Remove `implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))` which packaged the 48 MB sherpa-onnx library.

---

### Component 2: Memory & Audio Hot-Path Optimization (Zero GC Allocations)

#### [MODIFY] `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt`
- Replace `ArrayDeque<Short>(RAW_HISTORY_SAMPLES)` with a flat circular primitive buffer:
  ```kotlin
  private val rawPcm = ShortArray(RAW_HISTORY_SAMPLES)
  private var rawPcmHead = 0
  private var rawPcmCount = 0
  ```
- Replace `audioChunk: ArrayList<Short>` with a pre-allocated `ShortArray(FRAME_SAMPLES)` and an integer index `chunkIndex`.
- In `processChunk()`: Read directly from the primitive array into `melInputBuffer` without boxing or intermediate array copying.

#### [MODIFY] `app/src/main/java/com/jarvis/voice/audio/ContinuousAudioStream.kt`
- In `readAudioLoop()`: Instead of `readBuffer.copyOf(read)` 25 times per second, use a reusable dispatch buffer or pass `readBuffer, 0, read` directly to consumers, cutting 1,500 array allocations per minute.

---

### Component 3: Build & Compiler Optimization

#### [MODIFY] `gradle.properties`
- Enable R8 Full Mode: `android.enableR8.fullMode=true` (optimizes byte-code, strips unused classes, inlines single-use methods).
- Enable Gradle Build Cache: `org.gradle.caching=true`.

#### [MODIFY] `app/proguard-rules.pro`
- Remove obsolete ProGuard rules for `com.k2fsa.sherpa.onnx`.

---

## Verification Plan

### Automated Tests
1. **Full Unit Test Suite Execution:**
   - Command:
     ```bash
     export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11
     export PATH=$JAVA_HOME/bin:$PATH
     ./gradlew testDebugUnitTest
     ```
   - Expected: All unit tests PASS with zero regressions.
2. **Asset Integrity Check:**
   - Command: `python3 verify_wakeword_assets.py`
   - Expected: All openWakeWord feature models and classifier heads pass SHA-256 checks.
3. **APK Size Benchmark:**
   - Command: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
   - Expected: Output size drops from **83 MB to ~15 MB**.

### Manual Verification on Connected Device (`LM-K420`)
1. Run `./scripts/install_on_phone.sh app/build/outputs/apk/debug/app-debug.apk`.
   - Verify fast streamed install (< 3 seconds).
2. Ping probe:
   ```bash
   adb shell am broadcast -n com.jarvis/.service.CommandReceiver -a com.jarvis.action.PING
   ```
   - Verify `PONG:service=RUNNING,cores=8,tier=LOW_END` in < 200ms.
3. Wake word probe:
   ```bash
   adb shell am broadcast -n com.jarvis/.service.CommandReceiver -a com.jarvis.action.EXECUTE_TOOL --es tool wake_diagnostics
   ```
   - Verify `STATUS:SUCCESS`, zero memory trims in logcat, and responsive wake word detection.
