#!/usr/bin/env python3
from pathlib import Path

def main():
    code = Path("app/src/main/java/com/jarvis/battery/DeepBatteryGovernor.kt").read_text()
    if "fun addThrottleListener" not in code or "fun notifyThrottleChanged" not in code:
        print("FAIL (expected RED): DeepBatteryGovernor lacks addThrottleListener or notifyThrottleChanged")
        return 1

    print("PASS: DeepBatteryGovernor has throttle listeners")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
