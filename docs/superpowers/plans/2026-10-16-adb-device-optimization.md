# ADB Device Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform Jarvis ADB device automation from fragile logcat polling into a robust, sub-second synchronous bridge optimized specifically for physical Android devices (targeting 32-bit `armeabi-v7a` 8x Cortex-A53 devices such as the connected LG K42, as well as modern multi-core devices).

**Architecture:** 
1. Build `AdbBridgeEngine` to handle ordered broadcast actions (`PING`, `EXECUTE_TOOL`, `EXECUTE_COMMAND`) returning immediate `resultCode` and structured `resultData` payloads directly to ADB shell callers.
2. Automate full device permission provisioning and battery optimization bypass via `scripts/optimize_adb_device.sh`.
3. Tune `HardwareProfileManager` specifically for 32-bit Cortex-A53 architectures (MediaTek Helio P22 / MT6762) to prevent CPU thermal throttling and memory pressure.
4. Replace slow, flaky 6-second sleep logcat polling in `run_jarvis_tests.sh` with a fast, deterministic Python test harness (`scripts/run_adb_device_suite.py`).

**Tech Stack:** Kotlin, Android Ordered Broadcast APIs (`BroadcastReceiver.setResultCode`, `setResultData`), Android AppOps / Settings CLI, Python 3, ADB CLI, JUnit 4.

**Target Hardware Profile (Connected Device):**
- **Model:** LG K42 (`LM-K420`)
- **OS:** Android 12 (API level 31)
- **CPU:** MediaTek Helio P22 (8x ARM Cortex-A53 @ 2.0 GHz, `armeabi-v7a` 32-bit)
- **RAM:** 2.9 GB Total (~980 MB Available)

---

## Global Constraints

- Android API compatibility: `minSdk = 28`, `targetSdk = 35`.
- Backward compatibility: Existing broadcast actions (`com.jarvis.action.EXECUTE_COMMAND`) and legacy extras (`command`, `query`, `command_text`) must remain functional.
- Ordered broadcast timeout: Broadcast receiver handlers must complete within 2000ms or delegate long tasks asynchronously to avoid Android broadcast ANR (10s threshold).
- Shell compatibility: All ADB shell broadcast invocations must properly escape multi-word arguments (e.g. `--es command "'what is battery status'"`).
- Permission resiliency: Permission grant scripts must handle user-restricted ROMs gracefully without failing catastrophically if non-root ADB cannot grant private AppOps.

---

## Review Focus

1. **Unstarted Foreground Service on ADB broadcast:** If `JarvisForegroundService.instance` is `null` when an ADB broadcast arrives, `AdbBridgeEngine` must start the service and report `"STARTING_SERVICE"` instead of crashing with a `NullPointerException`.
2. **Cortex-A53 Thermal Flapping & Throttling:** 8 in-order A53 cores overheat rapidly when more than 2-3 threads perform floating-point TFLite inference. `HardwareProfileManager` must detect 32-bit A53 architectures and cap thread count to 2.
3. **Escaped Spaces in Broadcast Extra Strings:** In bash, `am broadcast -a ... -e command what time is it` truncates the command to `what`. The bridge and runner must support single-quoted string wrappers and sanitized input.
4. **AppOps Permission Key Difference on Android 12:** In API 31+, usage stats permission requires `appops set com.jarvis GET_USAGE_STATS allow` (not `PACKAGE_USAGE_STATS`). The setup script must use valid AppOps keys.
5. **Multi-device Serial Disambiguation:** When multiple ADB devices or emulators are connected, scripts must accept an explicit `-s <serial>` argument and default cleanly to the target device.

---

## Proposed Tasks

### Task 1: `AdbBridgeEngine` & Synchronous Ordered Broadcast Protocol

**Files:**
- Create: `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`
- Modify: `app/src/main/java/com/jarvis/service/CommandReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/jarvis/service/AdbBridgeEngineTest.kt`

**Interfaces:**
```kotlin
object AdbBridgeEngine {
    const val ACTION_PING = "com.jarvis.action.PING"
    const val ACTION_EXECUTE_TOOL = "com.jarvis.action.EXECUTE_TOOL"
    const val ACTION_EXECUTE_COMMAND = "com.jarvis.action.EXECUTE_COMMAND"
    const val EXTRA_TOOL = "tool"
    const val EXTRA_PARAMS = "params"

    fun handleBroadcast(receiver: BroadcastReceiver, context: Context, intent: Intent): Boolean
    fun parseParams(rawParams: String?): Map<String, String>
    fun formatPingResponse(context: Context?): String
    fun formatToolExecutionResult(success: Boolean, message: String, data: Map<String, Any?> = emptyMap()): String
}
```

- [ ] **Step 1: Write unit tests in `AdbBridgeEngineTest.kt`**
  - Verify parameter parser handles `key=value`, `key: value`, JSON-like pairs, and empty/null inputs.
  - Verify `formatPingResponse` outputs running service state, available processor count, and device memory tier.
  - Verify `formatToolExecutionResult` outputs machine-parsable `STATUS:SUCCESS|...` and `STATUS:FAILED|...` strings.
- [ ] **Step 2: Run unit tests to confirm failure**
- [ ] **Step 3: Implement `AdbBridgeEngine.kt`**
  - Provide structured ordered broadcast responses:
    - `ACTION_PING`: returns `resultCode = Activity.RESULT_OK`, `resultData = "PONG:service=${serviceRunning},cores=${cores},tier=${tier}"`.
    - `ACTION_EXECUTE_TOOL`: extracts tool name and params; executes tool synchronously (or via lightweight coroutine dispatch) and returns execution payload in `resultData`.
    - `ACTION_EXECUTE_COMMAND`: sanitizes command string, triggers voice engine assistant runtime, and sets `resultData = "ACCEPTED: $command"`.
- [ ] **Step 4: Update `CommandReceiver.kt` and `AndroidManifest.xml`**
  - Delegate incoming intents through `AdbBridgeEngine.handleBroadcast(this, context, intent)`.
  - Register `com.jarvis.action.PING` and `com.jarvis.action.EXECUTE_TOOL` in the receiver's `<intent-filter>`.
- [ ] **Step 5: Verify unit tests pass**
- [ ] **Step 6: Commit** (`feat(adb): implement AdbBridgeEngine with ordered broadcast support`)

---

### Task 2: Architecture Tuning for 32-bit Cortex-A53 / MediaTek Helio P22

**Files:**
- Modify: `app/src/main/java/com/jarvis/core/hardware/HardwareProfileManager.kt`
- Test: `app/src/test/java/com/jarvis/core/hardware/HardwareProfileManagerTest.kt`

**Interfaces:**
```kotlin
// In HardwareProfileManager:
fun isCortexA53Cluster(): Boolean
fun getRecommendedTFLiteThreads(context: Context?): Int
```

- [ ] **Step 1: Add unit tests for 32-bit A53 architecture detection in `HardwareProfileManagerTest.kt`**
  - Test thread calculation for 8-core in-order 32-bit CPU architectures (caps at 2-3 threads instead of 4).
  - Verify low-RAM memory store limits on 3GB physical RAM devices (~900MB free).
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement A53 architecture profiling in `HardwareProfileManager.kt`**
  - Inspect `Build.SUPPORTED_ABIS` (contains `armeabi-v7a`, lacks `arm64-v8a`) or `/proc/cpuinfo` implementer/part codes.
  - When running on 8x Cortex-A53 (Helio P22 / Snapdragon 625), restrict recommended TFLite thread pool size to 2 threads to prevent cache thrashing and severe thermal degradation.
- [ ] **Step 4: Run tests to verify pass**
- [ ] **Step 5: Commit** (`perf(hardware): optimize TFLite threading and memory budgets for Cortex-A53 devices`)

---

### Task 3: One-Shot ADB Permissions & Autopilot Provisioning Script

**Files:**
- Create: `scripts/optimize_adb_device.sh`
- Modify: `scripts/install_on_phone.sh`
- Test: `scripts/test_optimize_script.py`

**Interfaces:**
- CLI: `scripts/optimize_adb_device.sh [-s serial]`
- Output: Pre-grants all permissions required for complete autonomous operation without opening phone settings dialogs.

- [ ] **Step 1: Write test validating `optimize_adb_device.sh` CLI commands and arguments**
  - Verify presence of accessibility service grant: `settings put secure enabled_accessibility_services com.jarvis/.accessibility.JarvisAccessibilityService`.
  - Verify presence of notification listener grant: `cmd notification allow_listener com.jarvis/.notifications.JarvisNotificationListenerService`.
  - Verify overlay grant: `appops set com.jarvis SYSTEM_ALERT_WINDOW allow`.
  - Verify usage stats grant: `appops set com.jarvis GET_USAGE_STATS allow`.
  - Verify Doze bypass whitelist: `dumpsys deviceidle whitelist +com.jarvis`.
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement `scripts/optimize_adb_device.sh`**
  - Support `-s <serial>` argument for multi-device setups.
  - Check `adb get-state` and detect device name (`getprop ro.product.model`).
  - Grant permissions idempotently with clear status feedback.
  - Integrate with `scripts/install_on_phone.sh` so full provisioning happens automatically upon installation.
- [ ] **Step 4: Verify test passes and execute against physical device (`LM-K420`)**
- [ ] **Step 5: Commit** (`feat(adb): add one-shot optimize_adb_device.sh automation script`)

---

### Task 4: High-Performance Sub-Second ADB Test Runner

**Files:**
- Create: `scripts/run_adb_device_suite.py`
- Modify: `run_jarvis_tests.sh`
- Test: `scripts/test_runner_verification.py`

**Interfaces:**
- CLI: `python3 scripts/run_adb_device_suite.py [--serial <serial>] [--tool <name>] [--dry-run]`
- Contract: Sends synchronous ordered broadcasts via `am broadcast -a com.jarvis.action.PING` and `EXECUTE_TOOL`, parsing `resultCode` and `resultData` directly without 6-second sleep polling.

- [ ] **Step 1: Write contract verification test for `run_adb_device_suite.py`**
  - Tests command line flag parsing (`--serial`, `--timeout`, `--dry-run`).
  - Tests ordered broadcast response string parsing.
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement `scripts/run_adb_device_suite.py`**
  - Probe device responsiveness via `com.jarvis.action.PING` (< 500ms).
  - Run test cases:
    1. System Ping & Service Liveness.
    2. Battery & Thermal Governor Telemetry query.
    3. Memory Store Fact Insertion & Retrieval probe.
    4. Media / WhatsApp intent dispatch probe.
  - Generate a formatted markdown summary report (`adb_test_report.md`).
- [ ] **Step 4: Update `run_jarvis_tests.sh` to delegate to `scripts/run_adb_device_suite.py`**
- [ ] **Step 5: Run tests on connected physical device and verify sub-second turnaround**
- [ ] **Step 6: Commit** (`feat(test): introduce sub-second ADB device test runner with ordered broadcast probes`)
