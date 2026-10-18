# Hands-Free Phase 6: Lock Screen Wake/Unlock & Emergency SOS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement hands-free lock screen control (display wake, screen sleep, touchless keyguard dismissal, rate-limited voice PIN entry) and an Emergency SOS Voice Beacon (Morse code strobe, siren, GPS coordinates with Google Maps link, automated emergency SMS broadcast, and emergency contact dialer) optimized for Samsung Galaxy A51 and LG K42.

**Architecture:**
- `DisplayWakeEngine` & `UiTools` manage display wake via `PowerManager.WakeLock` (`ACQUIRE_CAUSES_WAKEUP` with strict 10s timeout) and screen sleep via AccessibilityService `GLOBAL_ACTION_LOCK_SCREEN` (or `DevicePolicyManager.lockNow()`).
- `KeyguardUnlockTool` handles touchless keyguard dismissal and voice PIN entry with numeric validation (4-8 digits), rate limiting (max 3 attempts per 60s), and keypad node traversal without leaking PINs in logs.
- `EmergencySosBeaconEngine` & `EmergencySosTool` orchestrate life-critical SOS triggers: running a Morse code flashlight strobe (`... --- ...`) via `CameraManager` torch, alternating siren alert tone, GPS extraction formatted into a Google Maps URL, emergency SMS broadcast via `TelephonyTool`, and auto-dialing 112 with hands-free speakerphone.
- `IntentModel` fast-paths and `LockScreenSecurityPolicy` provide zero-latency regex resolution (<5ms) and authorized execution on secure lock screens.

**Tech Stack:** Kotlin 1.9, Android Jetpack, Android AccessibilityService, PowerManager, CameraManager, Telephony/SmsManager, ToneGenerator/AudioManager, Coroutines, JUnit4.

**Spec:** Phase 6 Requirements: Lock Screen Control & Emergency SOS Voice Beacon for Samsung Galaxy A51 (Exynos 9611) & LG K42 (MediaTek Helio P22, 3GB RAM, Android 10 API 29).

## Global Constraints

- **Platform Floor:** Android 10 (API 29) compatibility for LG K42; Android 12 (API 31) support for Samsung A51.
- **WakeLock Timeout:** All wake locks must specify a strict 10,000ms timeout (`acquire(10000L)`) to prevent battery drain on Helio P22 and Exynos 9611.
- **Torch Lifecycle:** CameraManager torch must always be safely turned off in a `finally` block or upon job cancellation.
- **PIN Privacy:** Cleartext PINs must never be logged to Android Logcat or included in unmasked plain error strings.
- **Zero Regressions:** 100% test pass rate across all existing 657 unit tests.

## Review Focus

1. **Unformatted / Malformed PIN:** Voice input containing non-numeric words (e.g. "enter pin twelve thirty four" or "enter pin abc") must be rejected gracefully with helpful prompt rather than crashing or spamming keypad.
2. **Emergency SOS Cancellation:** Fast-path "stop sos" or "cancel emergency" must cancel strobe and siren coroutines immediately within 100ms without leaving camera torch stuck ON.
3. **No GPS Fix Fallback:** In indoor emergency situations where GPS is unavailable, SOS SMS broadcast must still dispatch with "Location unavailable (GPS offline)" notice rather than failing silently.
4. **Helio P22 Memory Constraints:** Coroutines in Emergency SOS loop must not perform heap allocations inside the rapid Morse strobe loop (pre-calculate timing table).
5. **Lock Screen Security Whitelist:** Ensure `EMERGENCY_SOS`, `STOP_SOS`, `KEYGUARD_UNLOCK`, `SCREEN_LOCK`, `SCREEN_UNLOCK` are permitted by `LockScreenSecurityPolicy` when keyguard is active.

---

### Task 1: Hands-Free Display Wake & Screen Lock Engine

**Files:**
- Create: `app/src/main/java/com/jarvis/tools/DisplayWakeEngine.kt`
- Modify: `app/src/main/java/com/jarvis/tools/UiTools.kt`
- Test: `app/src/test/java/com/jarvis/tools/DisplayWakeAndLockTest.kt`

**Interfaces:**
- Consumes: `Context`, `PowerManager`, `JarvisAccessibilityService`, `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN`
- Produces:
  ```kotlin
  object DisplayWakeEngine {
      fun wakeDisplay(context: Context?, timeoutMs: Long = 10000L): ToolResult
      suspend fun lockDisplay(context: Context?): ToolResult
  }
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/tools/DisplayWakeAndLockTest.kt` testing:
  - `DisplayWakeEngine.wakeDisplay` returns success and caps timeout at 10,000ms.
  - `DisplayWakeEngine.lockDisplay` invokes accessibility lock or falls back cleanly in test mode.
  - `ScreenLockTool` and `ScreenUnlockTool` delegate to `DisplayWakeEngine`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.DisplayWakeAndLockTest`
  Expected: FAIL (unresolved reference `DisplayWakeEngine`).

- [ ] **Step 3: Implement `DisplayWakeEngine.kt` and integrate into `UiTools.kt`**
  Implement `DisplayWakeEngine.kt` with:
  - `wakeDisplay(context, timeoutMs)`: clamps timeout to `minOf(timeoutMs, 10000L)`, acquires `PowerManager.WakeLock` with `PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE`.
  - `lockDisplay(context)`: calls `JarvisAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)` (API 28+) or fallback.
  Update `ScreenLockTool` and `ScreenUnlockTool` in `UiTools.kt` to use `DisplayWakeEngine`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.DisplayWakeAndLockTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/tools/DisplayWakeEngine.kt app/src/main/java/com/jarvis/tools/UiTools.kt app/src/test/java/com/jarvis/tools/DisplayWakeAndLockTest.kt
  git commit -m "feat(lockscreen): implement hands-free display wake and lock engine with 10s battery guard"
  ```

---

### Task 2: Touchless Keyguard Dismissal & Voice Passcode / PIN Entry

**Files:**
- Create: `app/src/main/java/com/jarvis/tools/KeyguardUnlockTool.kt`
- Modify: `app/src/main/java/com/jarvis/foundation/LockScreenSecurityPolicy.kt`
- Test: `app/src/test/java/com/jarvis/tools/KeyguardUnlockTest.kt`

**Interfaces:**
- Consumes: `UiAutomationManager`, `KeyguardManager`, `LockScreenSecurityPolicy`
- Produces:
  ```kotlin
  class KeyguardUnlockTool(private val context: Context? = null) : Tool {
      companion object {
          fun validatePin(pin: String?): String? // returns clean 4-8 numeric string or null
          fun checkRateLimit(): Boolean
          fun resetRateLimit()
      }
      override val name: String = "KEYGUARD_UNLOCK"
      override suspend fun execute(params: Map<String, String>): ToolResult
  }
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/tools/KeyguardUnlockTest.kt` testing:
  - `validatePin` accepts valid 4 to 8 digit numbers ("1234", "0000", "987654") and rejects invalid ("12", "abcd", "123456789").
  - Rate limiting allows up to 3 attempts per 60 seconds and blocks the 4th attempt.
  - Plaintext PIN is never returned in result messages (masked as `****`).
  - `LockScreenSecurityPolicy.isActionAllowedOnLockScreen` permits `KEYGUARD_UNLOCK`, `SCREEN_UNLOCK`, `SCREEN_LOCK`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.KeyguardUnlockTest`
  Expected: FAIL (unresolved reference `KeyguardUnlockTool`).

- [ ] **Step 3: Implement `KeyguardUnlockTool.kt` and update `LockScreenSecurityPolicy.kt`**
  - Implement `KeyguardUnlockTool.kt`:
    - Numeric PIN sanitization and validation (4-8 digits).
    - Rate limit tracking: timestamps of recent attempts, max 3 within 60,000ms.
    - If PIN provided, iterates over digits with `findNodeByText` and clicks Enter/Done.
    - If no PIN provided, triggers keyguard dismiss via swipe-up / `requestDismissKeyguard`.
    - Result messages display masked PIN: `pin.map { '*' }.joinToString("")`.
  - Update `LockScreenSecurityPolicy.kt`:
    - Add `"KEYGUARD_UNLOCK"` to `SAFE_ACTIONS`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.KeyguardUnlockTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/tools/KeyguardUnlockTool.kt app/src/main/java/com/jarvis/foundation/LockScreenSecurityPolicy.kt app/src/test/java/com/jarvis/tools/KeyguardUnlockTest.kt
  git commit -m "feat(security): implement keyguard unlock with voice PIN rate limiting and security mask"
  ```

---

### Task 3: Emergency SOS Voice Beacon Engine

**Files:**
- Create: `app/src/main/java/com/jarvis/emergency/EmergencySosBeaconEngine.kt`
- Create: `app/src/main/java/com/jarvis/tools/EmergencySosTool.kt`
- Modify: `app/src/main/java/com/jarvis/foundation/LockScreenSecurityPolicy.kt`
- Test: `app/src/test/java/com/jarvis/tools/EmergencySosBeaconTest.kt`

**Interfaces:**
- Consumes: `CameraManager`, `LocationTool`, `TelephonyTool`, `ToneGenerator`
- Produces:
  ```kotlin
  object EmergencySosBeaconEngine {
      data class SosState(val isActive: Boolean, val cyclesCompleted: Int, val lat: Double?, val lng: Double?)
      val MORSE_SOS_TIMINGS: List<Pair<Boolean, Long>> // Precomputed timing table for Helio P22 low-allocation loop
      fun formatSosMessage(lat: Double?, lng: Double?): String
      suspend fun startBeacon(context: Context?, contactNumber: String? = null, dialEmergency: Boolean = true): ToolResult
      fun stopBeacon(): Boolean
      fun isBeaconActive(): Boolean
  }
  class EmergencySosTool(private val context: Context? = null) : Tool
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/tools/EmergencySosBeaconTest.kt` testing:
  - `MORSE_SOS_TIMINGS` contains exact Morse SOS pattern: 3 dots (150ms ON/OFF), 3 dashes (450ms ON/OFF), 3 dots (150ms ON/OFF).
  - `formatSosMessage` generates Google Maps link `https://maps.google.com/?q=28.6139,77.2090` when coordinates provided, and clean fallback text when null.
  - `startBeacon` activates beacon state, and `stopBeacon` deactivates beacon state cleanly within <50ms.
  - `EmergencySosTool` executes `"start"` / `"stop"` actions.
  - `LockScreenSecurityPolicy` permits `"EMERGENCY_SOS"` on lock screen.

- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.EmergencySosBeaconTest`
  Expected: FAIL (unresolved reference `EmergencySosBeaconEngine`).

- [ ] **Step 3: Implement `EmergencySosBeaconEngine.kt` and `EmergencySosTool.kt`**
  - Implement `EmergencySosBeaconEngine.kt`:
    - Constant timing table: `MORSE_SOS_TIMINGS = listOf(Pair(true, 150L), Pair(false, 150L), ...)` avoiding object allocation in the loop.
    - Formats Google Maps URL: `https://maps.google.com/?q=$lat,$lng`.
    - Strobe loop runs on background `CoroutineScope` with cancellation check `isActive`.
    - `finally` block ensures torch is turned OFF (`setTorchMode(id, false)`).
    - Dispatches emergency SMS with location coordinates to emergency contacts.
    - Triggers emergency dialer (`tel:112` or contact) with speakerphone enabled.
    - `stopBeacon()` cancels running coroutine job and turns torch OFF immediately.
  - Implement `EmergencySosTool.kt`:
    - Registers metadata and exposes `action="start"|"stop"|"status"`.
  - Update `LockScreenSecurityPolicy.kt`:
    - Allow `"EMERGENCY_SOS"` and `"STOP_SOS"` in `SAFE_ACTIONS`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.EmergencySosBeaconTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/emergency/EmergencySosBeaconEngine.kt app/src/main/java/com/jarvis/tools/EmergencySosTool.kt app/src/main/java/com/jarvis/foundation/LockScreenSecurityPolicy.kt app/src/test/java/com/jarvis/tools/EmergencySosBeaconTest.kt
  git commit -m "feat(sos): implement Emergency SOS Voice Beacon with Morse strobe, siren, GPS maps link and auto dialer"
  ```

---

### Task 4: IntentModel Fast-Paths, Bilingual Triggers & LockScreen Routing

**Files:**
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Modify: `app/src/main/java/com/jarvis/tools/ToolRegistry.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Test: `app/src/test/java/com/jarvis/ai/Phase6IntentFastPathTest.kt`

**Interfaces:**
- Consumes: `AssistantIntent.EMERGENCY_SOS`, `AssistantIntent.SCREEN_LOCK`, `AssistantIntent.SCREEN_UNLOCK`
- Produces:
  ```kotlin
  // In IntentModel:
  // Pre-compiled regex patterns:
  val SOS_TRIGGER_REGEX: Regex
  val SOS_CANCEL_REGEX: Regex
  val PIN_UNLOCK_REGEX: Regex
  val WAKE_DISPLAY_REGEX: Regex
  val LOCK_DISPLAY_REGEX: Regex
  ```

- [ ] **Step 1: Write the failing test**
  Write `app/src/test/java/com/jarvis/ai/Phase6IntentFastPathTest.kt` testing:
  - English SOS triggers: "send sos", "emergency", "help me", "send emergency alert" -> `AssistantIntent.EMERGENCY_SOS` (action="start").
  - Hindi SOS triggers: "bachao", "madad karo", "bachao mujhe" -> `AssistantIntent.EMERGENCY_SOS` (action="start").
  - SOS cancel triggers: "stop sos", "cancel sos", "sos band karo" -> `AssistantIntent.EMERGENCY_SOS` (action="stop").
  - Wake display triggers: "wake screen", "wake up display", "turn on display", "screen on karo" -> `AssistantIntent.SCREEN_UNLOCK` (action="wake").
  - Lock display triggers: "sleep display", "turn off screen", "phone band karo", "lock screen" -> `AssistantIntent.SCREEN_LOCK` (action="lock").
  - Voice PIN triggers: "unlock phone with pin 1234", "enter pin 4321", "pin dalo 9999", "unlock with pin 5555" -> `AssistantIntent.SCREEN_UNLOCK` (pin="1234", etc.).
  - ToolRegistry aliases for `EMERGENCY_SOS`, `SOS`, `BEACON`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.ai.Phase6IntentFastPathTest`
  Expected: FAIL (unresolved regex or missing intent routing).

- [ ] **Step 3: Update `IntentModel.kt`, `ToolRegistry.kt`, and `AssistantRuntime.kt`**
  - Add `EMERGENCY_SOS` to `AssistantIntent` enum.
  - Add precompiled static regexes: `SOS_TRIGGER_REGEX`, `SOS_CANCEL_REGEX`, `PIN_UNLOCK_REGEX`, `WAKE_DISPLAY_REGEX`, `LOCK_DISPLAY_REGEX`.
  - Evaluate `SOS_TRIGGER_REGEX` and `SOS_CANCEL_REGEX` at the very top of `IntentModel.classify()` so emergency commands preempt everything else.
  - Add PIN extraction in `IntentModel.classify()`.
  - Register `EmergencySosTool` and aliases (`"EMERGENCY_SOS"`, `"SOS"`, `"BEACON"`, `"KEYGUARD_UNLOCK"`) in `ToolRegistry.kt`.
  - Register `EmergencySosTool` in `AssistantRuntime.kt`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.ai.Phase6IntentFastPathTest`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/main/java/com/jarvis/tools/ToolRegistry.kt app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt app/src/test/java/com/jarvis/ai/Phase6IntentFastPathTest.kt
  git commit -m "feat(intent): add emergency SOS, wake/sleep, and voice PIN fast-paths with bilingual support"
  ```

---

### Task 5: End-to-End Regression Verification & Artifact Documentation

**Files:**
- Create: `app/src/test/java/com/jarvis/Phase6RegressionTest.kt`
- Modify: `walkthrough.md`

- [ ] **Step 1: Write `Phase6RegressionTest.kt`**
  Implement end-to-end regression tests verifying:
  - WakeLock safety and 10s timeout clamping.
  - Touchless PIN entry with rate limiting (max 3/min) and masking.
  - Emergency SOS Beacon lifecycle: Morse timings, GPS link formatting, emergency contact dispatch, immediate cancellation.
  - LockScreenSecurityPolicy verification across all lock screen actions.
  - Intent resolution across English and Hindi triggers.

- [ ] **Step 2: Run all unit tests across the entire project**
  Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest`
  Expected: PASS (675+ passing tests, 0 failures).

- [ ] **Step 3: Update `walkthrough.md` with Phase 6 architecture and feature list**
  Add Phase 6 components, sequence diagrams, and test statistics to `walkthrough.md`.

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/test/java/com/jarvis/Phase6RegressionTest.kt
  git commit -m "test(phase6): add comprehensive Phase 6 regression suite covering lockscreen and SOS beacon"
  ```
