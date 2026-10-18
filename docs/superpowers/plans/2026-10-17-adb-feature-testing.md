# ADB Feature Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an automated end-to-end ADB testing harness and execute tests across all J.A.R.V.I.S. Android features on the connected device (`LM-K420`), producing an updated `docs/DEVICE_TEST_REPORT.md` and machine-readable `dist/adb_test_report.json`.

**Architecture:** A Python-based modular ADB test suite (`scripts/test_adb_features.py`) interacts with `com.jarvis.service.CommandReceiver` and Android system utilities via ADB shell commands and ordered broadcasts, inspecting logcat telemetry and device state to verify 9 subsystem categories.

**Tech Stack:** Python 3 (subprocess, json, re, argparse), Android ADB Shell (`am broadcast`, `dumpsys`, `content`, `settings`), Kotlin / Android Foreground Service.

---

## Tasks

### Task 1: Create ADB Feature Test Runner Harness
**Files:**
- Create: `scripts/test_adb_features.py`
- Create: `scripts/run_adb_test_suite.sh`

- [ ] **Step 1: Implement `scripts/test_adb_features.py` core architecture**
  - Device discovery and property extraction (model, brand, Android version, SDK, battery).
  - Broadcast sender (`am broadcast -a com.jarvis.action.EXECUTE_COMMAND -n com.jarvis/.service.CommandReceiver`).
  - Ordered broadcast sender for `PING` and `EXECUTE_TOOL`.
  - Logcat watcher filtering by `com.jarvis` PID for real-time validation of `VoiceEngine`, `ToolRegistry`, and `AndroidTtsEngine`.

- [ ] **Step 2: Implement test cases across all 9 domains**
  - System health (PING, process status, notification channels, permissions).
  - Voice & acoustic engine (`wake_diagnostics`, `trigger_wake`).
  - Hardware controls (Flashlight toggle, Battery status query, Volume, Brightness).
  - Offline utilities (Calculator, Currency/Unit converter, Clock, Weather).
  - Memory & knowledge (Quick notes, Fact memory, Retrieval query).
  - App lifecycle (List installed apps, Open app, Close all apps).
  - Communication & messaging (Message reader, WhatsApp command broadcast, SMS/Contacts access).
  - Routines & briefing (Daily briefing, Bedtime routine).
  - Vision perception (Screen inspect, Camera vision).

- [ ] **Step 3: Implement reporter and markdown generator**
  - Formatted terminal output with live test table.
  - Export to `dist/adb_test_report.json`.
  - Export markdown summary to `docs/DEVICE_TEST_REPORT.md`.

- [ ] **Step 4: Create executable wrapper `scripts/run_adb_test_suite.sh`**
  - Make script executable (`chmod +x`).

- [ ] **Step 5: Verify script compilation and smoke test**
  - Run `python3 -m py_compile scripts/test_adb_features.py`.
  - Run `python3 scripts/test_adb_features.py --category health` to verify basic device communication.

---

### Task 2: (Optional) Enhance AdbBridgeEngine for Synchronous Tool Execution
**Files:**
- Modify: `app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt`

- [ ] **Step 1: Connect `ACTION_EXECUTE_TOOL` to `ToolRegistry`**
  - Dispatch generic tool names to `service.voiceEngine?.assistantRuntime?.toolRegistry?.get(toolName)`.
  - Execute tool with parsed params and return serialized result in ordered broadcast `resultData`.

- [ ] **Step 2: Run unit tests**
  - `./gradlew testDebugUnitTest`

---

### Task 3: Execute Full ADB Test Suite & Update Device Test Report
**Files:**
- Modify: `docs/DEVICE_TEST_REPORT.md`
- Output: `dist/adb_test_report.json`

- [ ] **Step 1: Execute complete test suite against connected LG LM-K420**
  - Run `bash scripts/run_adb_test_suite.sh`.

- [ ] **Step 2: Verify all results and generate final documentation**
  - Inspect `docs/DEVICE_TEST_REPORT.md`.
  - Confirm all 9 categories tested and recorded.
