# Exhaustive ADB Feature Testing Plan (Phase 2 Expansion)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Expand ADB feature testing from 36 to 52+ tests, covering 100% of all cataloged tools, 5-tab UI navigation, screenshot capture, and stress resilience on the connected device (`LM-K420`).

**Architecture:** Python ADB test runner interacting with `CommandReceiver`, `ToolExecutor`, system services, and UI screen automation.

**Tech Stack:** Python 3, Android Debug Bridge, Android UI Accessibility / Input Keyevents.

---

## Tasks

### Task 1: Expand Test Suite to 52+ Tests
**Files:**
- Modify: `scripts/test_adb_features.py`

- [ ] Add tests for remaining tools:
  - `H-07`: PowerOptimizerTool ("optimize battery")
  - `H-08`: AudioPresetTool ("set sound preset to Bass Boost")
  - `M-05`: YouTubePlayTool ("play interstellar trailer on youtube")
  - `M-06`: SpotifyControlTool / MusicPlayTool ("play lofi beats on spotify")
  - `M-07`: MediaPlaybackControlTool ("pause music")
  - `U-05`: TranslatorTool ("translate hello to spanish")
  - `U-06`: ReminderSchedulerTool ("remind me to call John in 30 minutes")
  - `U-07`: NavigationTool ("navigate to Central Park")
  - `U-08`: SmartVoiceMemoTool ("record voice memo team sync")
  - `X-01`: FileManagerTool ("check storage space")
  - `X-02`: SecurityAuditorTool ("run security audit")
  - `X-03`: MacroWorkflowTool ("run macro status")

### Task 2: Implement 5-Tab UI Autopilot Navigation & Screenshot Verification
**Files:**
- Modify: `scripts/test_adb_features.py`

- [ ] Implement UI tab switching tests:
  - `T-01`: Switch to HOME tab & screencap
  - `T-02`: Switch to SKILLS tab & screencap
  - `T-03`: Switch to MEMORY tab & screencap
  - `T-04`: Switch to BRAIN tab & screencap
  - `T-05`: Switch to CONFIG tab & screencap
- [ ] Save captured screenshots to `docs/stitch/device_test_screens/`

### Task 3: Implement Concurrency Stress Test
**Files:**
- Modify: `scripts/test_adb_features.py`

- [ ] `Z-01`: Rapid burst of 5 commands in quick succession to verify SupervisorJob resilience.

### Task 4: Execute Full 52+ Test Suite & Update Reports
**Files:**
- Output: `dist/adb_test_report.json`
- Modify: `docs/DEVICE_TEST_REPORT.md`

- [ ] Run `bash scripts/run_adb_test_suite.sh`
- [ ] Confirm 100% pass across all 52+ tests.
