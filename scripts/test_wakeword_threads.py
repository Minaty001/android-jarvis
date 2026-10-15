#!/usr/bin/env python3
import os
from pathlib import Path

def main():
    # Verify test file exists
    test_path = Path("app/src/test/java/com/jarvis/wakeword/OpenWakeWordDetectorThreadTest.kt")
    assert test_path.exists(), "Missing OpenWakeWordDetectorThreadTest.kt"

    # Verify detector implementation uses HardwareProfileManager
    detector_code = Path("app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt").read_text()
    if "HardwareProfileManager" not in detector_code:
        print("FAIL (expected RED): OpenWakeWordDetector still uses hardcoded thread options")
        return 1

    print("PASS: OpenWakeWordDetector uses dynamic thread count")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
