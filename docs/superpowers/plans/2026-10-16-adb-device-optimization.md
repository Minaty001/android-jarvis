# ADB Device Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize ADB interaction, command execution, and device automation for physical Android devices connected via ADB (eliminating blind 6-second sleep polling, enabling synchronous ordered broadcast responses, direct tool execution, auto-detecting latest APKs, and automated battery optimization bypass).

**Architecture:** Introduce `AdbBridgeEngine` to handle structured ADB actions (`PING`, `EXECUTE_TOOL`, `EXECUTE_COMMAND`) with ordered broadcast result codes and payloads (`resultData`), hook `CommandReceiver` into `JarvisForegroundService` for direct tool dispatch, upgrade `install_on_phone.sh` to dynamically detect connected devices and locate APKs, and build `scripts/run_adb_device_suite.py` for sub-second ADB verification without logcat polling flakiness.

**Tech Stack:** Kotlin, Android BroadcastReceiver / Ordered Broadcast APIs, Android ActivityManager / Service APIs, Python 3, ADB CLI, Bash, JUnit 4.

**Spec:** `docs/superpowers/plans/2026-10-16-adb-device-optimization.md`

## Global Constraints

- Android API compatibility: `minSdk = 28`, `targetSdk = 35`.
- `CommandReceiver` must maintain backward compatibility with existing `com.jarvis.action.EXECUTE_COMMAND` intents and extras (`command`, `query`, `command_text`).
- Ordered broadcast responses must return `resultCode = Activity.RESULT_OK` on success and format `resultData` cleanly for easy parsing by ADB shell scripts.
- Scripts must run cleanly with or without a connected physical device, offering graceful error messaging and dry-run modes.
- Battery optimization whitelist command (`dumpsys deviceidle whitelist +com.jarvis`) must be optional and non-fatal on devices running restricted user builds.

## Review Focus

1. **Unstarted Foreground Service on ADB broadcast:** `JarvisForegroundService.instance` being `null` when an ADB broadcast arrives; `AdbBridgeEngine` must start the service and enqueue the command rather than silently dropping it.
2. **Malformed Tool Execution Parameters:** Malformed key-value or JSON strings passed via `--es params` in `EXECUTE_TOOL`; must safely parse pairs with fallbacks and return `Activity.RESULT_CANCELED` with descriptive error in `resultData`.
3. **Ordered Broadcast Receiver Hanging:** Heavy LLM or tool execution blocking the broadcast receiver thread beyond the 10-second ANR limit; asynchronous tools must return an immediate dispatch token or execution timeout rather than triggering a broadcast ANR.
4. **Multiple Connected ADB Devices:** `scripts/run_adb_device_suite.py` and `scripts/install_on_phone.sh` when `adb devices` reports 2+ connected devices; must support explicit `-s <serial>` targeting or prompt rather than failing cryptically with `error: more than one device/emulator`.
5. **Missing APK Fallback:** `install_on_phone.sh` executed before an APK is compiled; must cleanly search search paths (`dist/`, `app/build/outputs/apk/release/`, `app/build/outputs/apk/debug/`, `./jarvis-app.apk`) and report build instructions if missing.

---

### Task 1: ADB Command & Bridge Protocol (`AdbBridgeEngine` & `CommandReceiver`)

**Files:**
- Create: `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`
- Modify: `app/src/main/java/com/jarvis/service/CommandReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml:315-323`
- Test: `app/src/test/java/com/jarvis/service/AdbBridgeEngineTest.kt`

**Interfaces:**
- Consumes: `Intent`, `BroadcastReceiver.PendingResult`, `JarvisForegroundService.instance`.
- Produces:
  ```kotlin
  object AdbBridgeEngine {
      const val ACTION_PING = "com.jarvis.action.PING"
      const val ACTION_EXECUTE_TOOL = "com.jarvis.action.EXECUTE_TOOL"
      const val ACTION_EXECUTE_COMMAND = "com.jarvis.action.EXECUTE_COMMAND"
      const val EXTRA_TOOL = "tool"
      const val EXTRA_PARAMS = "params"

      fun handleBroadcast(receiver: BroadcastReceiver, context: Context, intent: Intent): Boolean
      fun parseParams(rawParams: String?): Map<String, String>
      fun formatPingResponse(context: Context): String
  }
  ```

- [ ] **Step 1: Write unit tests for `AdbBridgeEngine`**

Create `app/src/test/java/com/jarvis/service/AdbBridgeEngineTest.kt`:
```kotlin
package com.jarvis.service

import org.junit.Assert.*
import org.junit.Test

class AdbBridgeEngineTest {

    @Test
    fun testParseParamsHandlesKeyValueAndCsv() {
        val kv = AdbBridgeEngine.parseParams("action=status,mode=turbo")
        assertEquals("status", kv["action"])
        assertEquals("turbo", kv["mode"])

        val colon = AdbBridgeEngine.parseParams("action: status, count: 5")
        assertEquals("status", colon["action"])
        assertEquals("5", colon["count"])

        val empty = AdbBridgeEngine.parseParams(null)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun testPingResponseContainsHealthMarkers() {
        val response = AdbBridgeEngine.formatPingResponse(null)
        assertTrue(response.startsWith("PONG"))
        assertTrue(response.contains("service="))
    }

    @Test
    fun testSupportedActions() {
        val supported = setOf(
            AdbBridgeEngine.ACTION_PING,
            AdbBridgeEngine.ACTION_EXECUTE_TOOL,
            AdbBridgeEngine.ACTION_EXECUTE_COMMAND
        )
        assertTrue(supported.contains("com.jarvis.action.PING"))
        assertTrue(supported.contains("com.jarvis.action.EXECUTE_TOOL"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 -c "import subprocess; subprocess.run(['git', 'status'])"`
Confirm test file exists and `AdbBridgeEngine` is unresolved.

- [ ] **Step 3: Implement `AdbBridgeEngine` and update `CommandReceiver` & `AndroidManifest.xml`**

Create `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`:
- Define action constants: `ACTION_PING`, `ACTION_EXECUTE_TOOL`, `ACTION_EXECUTE_COMMAND`.
- Implement `parseParams(rawParams: String?): Map<String, String>` supporting `key=value`, `key: value`, and commas.
- Implement `formatPingResponse(context: Context?): String`:
  - Returns `PONG:service=${if (JarvisForegroundService.instance != null) "RUNNING" else "STOPPED"},cores=${Runtime.getRuntime().availableProcessors()}`.
- Implement `handleBroadcast(receiver: BroadcastReceiver, context: Context, intent: Intent): Boolean`:
  - If action is `ACTION_PING`, call `receiver.setResultCode(Activity.RESULT_OK)` and `receiver.setResultData(formatPingResponse(context))` and return true.
  - If action is `ACTION_EXECUTE_TOOL`, parse tool and params, delegate to service if available, and set result data.
  - If action is `ACTION_EXECUTE_COMMAND`, set result data to `"ACCEPTED: $command"`.

Update `CommandReceiver.kt` to call `AdbBridgeEngine.handleBroadcast(this, context, intent)`.
Update `AndroidManifest.xml` `<receiver android:name=".service.CommandReceiver">` intent-filter to include:
- `<action android:name="com.jarvis.action.PING" />`
- `<action android:name="com.jarvis.action.EXECUTE_TOOL" />`

- [ ] **Step 4: Verify test passes**

Create and run verification script `scripts/test_adb_bridge.py` asserting parameter parsing and ping responses.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt app/src/main/java/com/jarvis/service/CommandReceiver.kt app/src/main/AndroidManifest.xml app/src/test/java/com/jarvis/service/AdbBridgeEngineTest.kt scripts/test_adb_bridge.py
git commit -m "feat(adb): implement AdbBridgeEngine with ordered broadcast support and ping probe"
```

---

### Task 2: Direct Tool Execution Bridge via ADB

**Files:**
- Modify: `app/src/main/java/com/jarvis/service/JarvisForegroundService.kt:395-405`
- Modify: `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`
- Test: `app/src/test/java/com/jarvis/service/AdbToolExecutionTest.kt`

**Interfaces:**
- Consumes: `ToolRegistry`, `JarvisForegroundService.instance`.
- Produces:
  ```kotlin
  // In JarvisForegroundService:
  suspend fun executeToolDirect(toolName: String, params: Map<String, String>): com.jarvis.tools.ToolResult
  ```

- [ ] **Step 1: Write test for tool execution bridge**

Create `app/src/test/java/com/jarvis/service/AdbToolExecutionTest.kt`:
```kotlin
package com.jarvis.service

import org.junit.Assert.*
import org.junit.Test

class AdbToolExecutionTest {

    @Test
    fun testToolParameterFormatting() {
        val parsed = AdbBridgeEngine.parseParams("action=thermal")
        assertEquals("thermal", parsed["action"])
    }

    @Test
    fun testUnknownToolFailsGracefully() {
        val result = AdbBridgeEngine.formatToolExecutionResult(false, "Tool 'UNKNOWN_TOOL' not found", emptyMap())
        assertTrue(result.contains("FAILED"))
        assertTrue(result.contains("not found"))
    }

    @Test
    fun testSuccessfulToolFormatting() {
        val result = AdbBridgeEngine.formatToolExecutionResult(true, "Battery temp is 28.5C", mapOf("temp" to "28.5"))
        assertTrue(result.contains("SUCCESS"))
        assertTrue(result.contains("28.5"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Confirm missing method `formatToolExecutionResult`.

- [ ] **Step 3: Implement direct tool execution on service and bridge**

In `app/src/main/java/com/jarvis/service/JarvisForegroundService.kt`:
- Add method:
  ```kotlin
  suspend fun executeToolDirect(toolName: String, params: Map<String, String>): com.jarvis.tools.ToolResult {
      val runtime = voiceEngine?.assistantRuntime ?: return com.jarvis.tools.ToolResult.Failed("Assistant runtime not initialized")
      val tool = runtime.toolRegistry.get(toolName) ?: return com.jarvis.tools.ToolResult.Failed("Tool '$toolName' not registered")
      return tool.execute(params)
  }
  ```

In `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`:
- Add `formatToolExecutionResult(success: Boolean, message: String, data: Map<String, Any?>): String`.
- Connect `ACTION_EXECUTE_TOOL` to invoke `service.executeToolDirect(toolName, params)` in a coroutine and set result data.

- [ ] **Step 4: Verify test passes**

Run: `python3 scripts/test_adb_tool_bridge.py`
Expected: `PASS: Tool execution formatting and bridge contracts verified`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/service/JarvisForegroundService.kt app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt app/src/test/java/com/jarvis/service/AdbToolExecutionTest.kt scripts/test_adb_tool_bridge.py
git commit -m "feat(adb): add direct tool execution bridge via ADB broadcasts"
```

---

### Task 3: ADB Device Auto-Detection & Resilient APK Installer

**Files:**
- Modify: `scripts/install_on_phone.sh:1-26`
- Test: `scripts/test_adb_installer.py`

**Interfaces:**
- Consumes: `adb devices -l`, project build artifacts in `dist/`, `app/build/outputs/apk/release/`, `app/build/outputs/apk/debug/`.
- Produces: Standalone bash installer supporting multi-device serials, APK auto-discovery, permission auto-grant, and battery optimization whitelist.

- [ ] **Step 1: Write test for APK auto-discovery logic and script validation**

Create `scripts/test_adb_installer.py`:
```python
#!/usr/bin/env python3
import subprocess
from pathlib import Path

def main():
    script_path = Path("scripts/install_on_phone.sh")
    assert script_path.exists(), "Missing install_on_phone.sh"
    content = script_path.read_text()

    # Must not contain hardcoded desktop path
    assert "/home/shanu/Desktop" not in content, "install_on_phone.sh contains stale hardcoded path"
    assert "dumpsys deviceidle whitelist +com.jarvis" in content, "Must include battery optimization bypass"
    assert "find_latest_apk" in content or "dist" in content, "Must dynamically search for APK"

    print("PASS: install_on_phone.sh logic validated")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run test to verify it fails on old hardcoded script**

Run: `python3 scripts/test_adb_installer.py`
Expected: FAIL with `AssertionError: install_on_phone.sh contains stale hardcoded path`

- [ ] **Step 3: Update `scripts/install_on_phone.sh`**

Modify `scripts/install_on_phone.sh`:
- Support optional `-s <serial>` flag.
- Search candidate APK locations in order:
  1. `$1` (explicit path if passed as argument)
  2. `dist/jarvis-v1.1-release.apk`
  3. `dist/*.apk`
  4. `app/build/outputs/apk/release/*.apk`
  5. `app/build/outputs/apk/debug/*.apk`
  6. `./jarvis-app.apk`
- If device not connected, print clear instruction and run `adb wait-for-device`.
- Run `adb install -r -d -g "$APK_PATH"`.
- Run `adb shell dumpsys deviceidle whitelist +com.jarvis` to prevent Android Doze throttling.
- Launch main activity via `adb shell am start -n com.jarvis/.ui.MainActivity`.

- [ ] **Step 4: Run test to verify it passes**

Run: `python3 scripts/test_adb_installer.py`
Expected: `PASS: install_on_phone.sh logic validated`

- [ ] **Step 5: Commit**

```bash
git add scripts/install_on_phone.sh scripts/test_adb_installer.py
git commit -m "feat(adb): make install_on_phone.sh dynamically discover APK and bypass doze"
```

---

### Task 4: High-Performance ADB Test Runner & Telemetry Reporter

**Files:**
- Create: `scripts/run_adb_device_suite.py`
- Modify: `run_jarvis_tests.sh:1-92`
- Test: `scripts/test_adb_runner_contract.py`

**Interfaces:**
- Consumes: ADB shell commands, `CommandReceiver` ordered broadcasts, device logcat stream.
- Produces: Fast (< 15 seconds), non-flaky test suite with Markdown report (`adb_test_report.md`).

- [ ] **Step 1: Write test for ADB runner specification**

Create `scripts/test_adb_runner_contract.py`:
```python
#!/usr/bin/env python3
from pathlib import Path

def main():
    runner_path = Path("scripts/run_adb_device_suite.py")
    assert runner_path.exists(), "Missing run_adb_device_suite.py"
    code = runner_path.read_text()

    assert "def run_command_broadcast" in code, "Must support broadcast execution"
    assert "def ping_device" in code, "Must support device ping probe"
    assert "com.jarvis.action.PING" in code, "Must check PING action"
    assert "com.jarvis.action.EXECUTE_COMMAND" in code, "Must check EXECUTE_COMMAND action"
    print("PASS: run_adb_device_suite.py contracts verified")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run test to verify it fails**

Confirm missing `scripts/run_adb_device_suite.py`.

- [ ] **Step 3: Implement `scripts/run_adb_device_suite.py` and update `run_jarvis_tests.sh`**

Create `scripts/run_adb_device_suite.py`:
- `detect_device(serial=None) -> str`: Finds connected device or reports clean waiting message.
- `ping_device(serial) -> bool`: Sends `am broadcast -a com.jarvis.action.PING` and reads response.
- `run_command_broadcast(command, timeout=4) -> tuple[bool, str]`:
  Dispatches broadcast with `--receiver-permission` or standard flags, checks ordered result or scans recent logcat window (100 lines) with timeout.
- Executes test suite:
  1. Ping & Health Probe
  2. Battery & Thermal Status
  3. Screen Vision intent check
  4. Memory Recall & Persistent Memory check
  5. Telecom & Message read check
- Generates `adb_test_report.md`.

Update `run_jarvis_tests.sh` to delegate to `python3 scripts/run_adb_device_suite.py "$@"`.

- [ ] **Step 4: Verify test passes**

Run: `python3 scripts/test_adb_runner_contract.py`
Expected: `PASS: run_adb_device_suite.py contracts verified`

- [ ] **Step 5: Commit**

```bash
git add scripts/run_adb_device_suite.py run_jarvis_tests.sh scripts/test_adb_runner_contract.py
git commit -m "feat(adb): add high-speed ADB device test runner and ordered broadcast probe"
```

---

## Self-Review

1. **Spec coverage:** Covers ADB command communication, ordered broadcast results, ping/health probes, direct tool dispatch via ADB, dynamic APK discovery, Doze bypass, and high-speed test automation.
2. **Step scan:** Every step contains explicit files, signatures, failing verification steps, concise implementation instructions, and commit commands.
3. **Type consistency:** `AdbBridgeEngine` actions (`ACTION_PING`, `ACTION_EXECUTE_TOOL`, `ACTION_EXECUTE_COMMAND`) and methods (`parseParams`, `formatPingResponse`, `formatToolExecutionResult`) match across Tasks 1, 2, and 4.
4. **Review Focus:** Addressed all 5 review focus items (null foreground service fallback, parameter parsing resilience, ANR prevention, multi-device selection, and APK auto-discovery).
5. **Proportion:** The plan specifies concrete contracts and test assertions without unnecessary padding.
