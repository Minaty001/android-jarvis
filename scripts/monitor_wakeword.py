#!/usr/bin/env python3
"""
Jarvis Real-Time Wake Word & Voice Pipeline Monitor
Streams live telemetry directly from connected ADB device(s) and monitors:
- Acoustic feature energy & silence bypass state
- Candidate neural activations (openWakeWord raw scores & threshold ratios)
- Dedicated confirmation head updates & confirmations
- State transitions (WAITING_FOR_WAKE <-> LISTENING <-> PROCESSING <-> SPEAKING)
- Same-breath commands, barge-ins, and speech recognition events
"""

import sys
import os
import re
import subprocess
import time
from datetime import datetime

# ANSI Color Codes
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
MAGENTA = "\033[35m"
CYAN = "\033[36m"
WHITE = "\033[37m"
BG_GREEN = "\033[42m"
BG_BLUE = "\033[44m"
BG_RED = "\033[41m"
BG_YELLOW = "\033[43m"

WORKSPACE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_FILE = os.path.join(WORKSPACE_DIR, "wakeword_live.log")

BANNER = f"""
{CYAN}{BOLD}======================================================================
  🎙️  JARVIS WAKE WORD & ACOUSTIC STREAM MONITOR
  Device: Connected ADB Device
  Target Tags: OpenWakeWord | WakeWordEngine | VoiceEngine | AndroidSpeechEngine
  Log File: {LOG_FILE}
======================================================================{RESET}
"""

def append_log(line):
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

def render_meter(score, thr, length=18):
    ratio = score / thr if thr > 0 else 0
    filled = int(min(ratio, 1.0) * length)
    empty = length - filled
    bar = "█" * filled + "░" * empty
    if ratio >= 1.0:
        return f"{GREEN}[{bar}] {score:.3f}/{thr:.2f} ({ratio*100:.0f}%){RESET}"
    elif ratio >= 0.85:
        return f"{YELLOW}[{bar}] {score:.3f}/{thr:.2f} ({ratio*100:.0f}%){RESET}"
    else:
        return f"{DIM}[{bar}] {score:.3f}/{thr:.2f} ({ratio*100:.0f}%){RESET}"

def process_log_line(tag, msg, timestamp):
    now_str = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    raw_entry = f"[{now_str}] [{tag}] {msg}"
    append_log(raw_entry)

    # 1. Wake Word Confirmed
    if "openWakeWord CONFIRMED:" in msg or "Wake event accepted for" in msg:
        print(f"\n{BG_GREEN}{WHITE}{BOLD} 🎯 [WAKE WORD DETECTED & CONFIRMED] {RESET} {CYAN}{now_str}{RESET}")
        print(f"   {GREEN}{BOLD}{msg}{RESET}\n")
        return

    # 2. Wake Word Candidates
    candidate_match = re.search(r"openWakeWord candidate:\s*(.+?)\s+score=([\d\.]+)\s+ratio=([\d\.]+)\s*\(thr=([\d\.]+)\)", msg)
    if candidate_match:
        phrase = candidate_match.group(1)
        score = float(candidate_match.group(2))
        ratio = float(candidate_match.group(3))
        thr = float(candidate_match.group(4))
        meter = render_meter(score, thr)
        status = f"{YELLOW}SUB-THRESHOLD{RESET}" if ratio < 1.0 else f"{GREEN}{BOLD}MATCH{RESET}"
        print(f"  {DIM}[{now_str}]{RESET} {BOLD}{phrase:<12}{RESET} {meter} -> {status}")
        return

    # 3. Model Loaded & Configurations
    if "openWakeWord loaded heads=" in msg:
        print(f"\n{CYAN}{BOLD}🧠 [NEURAL HEADS LOADED]{RESET} {msg}")
        return

    if "WakeWordEngine sensitivity set to" in msg or "WakeWordEngine started" in msg:
        print(f"{BLUE}{BOLD}⚙️  [WAKE ENGINE CONFIG]{RESET} {msg}")
        return

    # 4. State Transitions
    if "VOICE session=" in msg or "State transition:" in msg or "Passive wake session active" in msg:
        print(f"  {CYAN}🔄 [VOICE STATE]{RESET} {BOLD}{msg}{RESET}")
        return

    # 5. Same-breath command or barge-in
    if "Same-breath command detected:" in msg:
        print(f"\n{BG_BLUE}{WHITE}{BOLD} ⚡ [SAME-BREATH COMMAND] {RESET} {YELLOW}{msg}{RESET}\n")
        return

    if "Barge-in triggered" in msg:
        print(f"\n{BG_YELLOW}{WHITE}{BOLD} ⚡ [BARGE-IN TRIGGERED] {RESET} {msg}\n")
        return

    # 6. Listening timeouts or empty returns
    if "Listening timeout" in msg or "returning to wake" in msg or "Returning to wake" in msg:
        print(f"  {MAGENTA}⏱️  [LISTENING CYCLE]{RESET} {msg}")
        return

    # 7. Speech Recognizer events
    if "Android native SpeechRecognizer" in msg or "onSpeechStarted" in msg or "onBeginningOfSpeech" in msg:
        print(f"  {GREEN}🎙️  [MIC / ASR]{RESET} {msg}")
        return

    # 8. Suppressions (e.g. proximity sensor / pocket)
    if "suppressed" in msg.lower() or "pocket" in msg.lower():
        print(f"  {RED}🛡️  [WAKE SUPPRESSED]{RESET} {msg}")
        return

    # Default fallback
    print(f"  {DIM}[{now_str}] {tag}: {msg}{RESET}")

def main():
    print(BANNER)

    # Check for connected adb device
    try:
        devices_out = subprocess.check_output(["adb", "devices"], text=True)
        devices = [line.split()[0] for line in devices_out.strip().splitlines()[1:] if line.strip() and not line.startswith("*")]
    except Exception as e:
        print(f"{RED}Error invoking adb: {e}{RESET}")
        sys.exit(1)

    if not devices:
        print(f"{RED}No ADB device detected. Please connect phone via USB or Wi-Fi with debugging enabled.{RESET}")
        sys.exit(1)

    device_id = devices[0]
    print(f"{GREEN}✓ Connected to device: {BOLD}{device_id}{RESET}\n")
    print(f"{DIM}Say 'Hey Jarvis' or 'Jarvis' near the phone to observe neural activations...{RESET}\n")

    # Command to stream logcat with tags
    adb_cmd = [
        "adb", "-s", device_id, "logcat", "-v", "time",
        "OpenWakeWord:V",
        "WakeWordEngine:V",
        "VoiceEngine:V",
        "AndroidSpeechEngine:V",
        "ContinuousAudioStream:V",
        "*:S"
    ]

    try:
        proc = subprocess.Popen(
            adb_cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )

        for line in iter(proc.stdout.readline, ''):
            if not line:
                break
            line_str = line.strip()
            if not line_str:
                continue

            match = re.search(r"([VDIWEF])\/([A-Za-z0-9_\-]+)\s*\(\s*\d+\s*\):\s*(.*)", line_str)
            if match:
                level, tag, message = match.groups()
                process_log_line(tag, message, line_str)
            else:
                pass

    except KeyboardInterrupt:
        print(f"\n{YELLOW}Wake word monitoring stopped.{RESET}")
    except Exception as e:
        print(f"\n{RED}Error streaming logcat: {e}{RESET}")

if __name__ == "__main__":
    main()
