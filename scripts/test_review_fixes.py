#!/usr/bin/env python3
import re
from pathlib import Path

def test_memory_store_fixes():
    code = Path("app/src/main/java/com/jarvis/memory/MemoryStore.kt").read_text()
    assert "MemoryType.STRUCTURED_FACT" in code, "MemoryStore must use MemoryType.STRUCTURED_FACT"
    assert "MemoryType.FACT" not in code, "MemoryType.FACT does not exist"
    assert "types = emptySet()" in code, "searchMemories must pass types = emptySet() to queryRelevantMemories"
    assert ".map { it.item }" not in code, "searchMemories must not map .item over List<MemoryItem>"
    assert "SQLiteDatabase.releaseMemory()" in code, "trimCache must call SQLiteDatabase.releaseMemory()"

def test_governor_hysteresis():
    code = Path("app/src/main/java/com/jarvis/battery/DeepBatteryGovernor.kt").read_text()
    assert "40.0" in code, "DeepBatteryGovernor must include 40.0C hysteresis threshold for thermal release"
    assert "currentThrottleState == ThrottleState.THERMAL_THROTTLED" in code, "DeepBatteryGovernor must check currentThrottleState for hysteresis"

def test_hardware_profile_null_context():
    code = Path("app/src/main/java/com/jarvis/device/HardwareProfileManager.kt").read_text()
    assert "tier = DeviceTier.MID_RANGE" in code, "getProfile(null) must return DeviceTier.MID_RANGE baseline"
    assert "recommendedTfliteThreads = 2" in code, "getProfile(null) must return 2 threads baseline"

def main():
    test_memory_store_fixes()
    test_governor_hysteresis()
    test_hardware_profile_null_context()
    print("PASS: All review fix contracts satisfied")

if __name__ == "__main__":
    main()
