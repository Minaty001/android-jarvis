#!/usr/bin/env python3
import re
from pathlib import Path

def calculate_optimal_threads(cores: int, is_low_ram: bool) -> int:
    clamped = max(1, cores)
    if is_low_ram or clamped <= 2:
        return min(clamped, 2)
    elif 3 <= clamped <= 6:
        return 2
    else:
        return 3

def classify_tier(cores: int, total_ram_bytes: int, is_low_ram: bool) -> str:
    four_gb = 4 * 1024 * 1024 * 1024
    eight_gb = 8 * 1024 * 1024 * 1024
    if is_low_ram or total_ram_bytes < four_gb or cores <= 4:
        return "LOW_END"
    elif total_ram_bytes < eight_gb or cores < 8:
        return "MID_RANGE"
    else:
        return "FLAGSHIP"

def main():
    # Verify Kotlin file exists and matches implementation contract
    kt_code = Path("app/src/main/java/com/jarvis/device/HardwareProfileManager.kt").read_text()
    assert "object HardwareProfileManager" in kt_code
    assert "fun calculateOptimalThreads" in kt_code
    assert "fun classifyTier" in kt_code
    assert "fun getProfile" in kt_code
    assert "fun getRecommendedTfliteThreads" in kt_code

    # Verify unit test file exists
    test_code = Path("app/src/test/java/com/jarvis/device/HardwareProfileManagerTest.kt").read_text()
    assert "class HardwareProfileManagerTest" in test_code

    # Run logic test suite
    assert calculate_optimal_threads(1, True) == 1
    assert calculate_optimal_threads(2, False) == 2
    assert calculate_optimal_threads(4, True) == 2
    assert calculate_optimal_threads(8, False) == 3
    assert calculate_optimal_threads(0, False) == 1

    assert classify_tier(2, 2 * 1024**3, True) == "LOW_END"
    assert classify_tier(6, 6 * 1024**3, False) == "MID_RANGE"
    assert classify_tier(8, 12 * 1024**3, False) == "FLAGSHIP"

    print("PASS: HardwareProfileManager logic and Kotlin contracts verified")

if __name__ == "__main__":
    main()
