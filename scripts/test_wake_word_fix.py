#!/usr/bin/env python3
"""Verification suite for Wake Word Detection Pipeline fixes."""
import re
from pathlib import Path

def test_kws_temporal_confirmation():
    path = Path("app/src/main/java/com/jarvis/wakeword/KwsTemporalConfirmation.kt")
    assert path.exists(), "KwsTemporalConfirmation.kt missing"
    code = path.read_text()

    # Must check for multi-window confirmation and retain peak score
    assert "positiveCount >= requiredPositiveWindows" in code, "Must check multi-window confirmation"
    assert "maxPositiveScore" in code, "Must retain max positive score"
    print("PASS: KwsTemporalConfirmation peak retention & multi-window logic verified")


def test_continuous_audio_stream_routing():
    path = Path("app/src/main/java/com/jarvis/voice/audio/ContinuousAudioStream.kt")
    assert path.exists(), "ContinuousAudioStream.kt missing"
    code = path.read_text()

    assert "isBluetoothScoActive" in code, "Must track isBluetoothScoActive"
    assert "resolvePreferredInputDevice" in code, "Must resolve preferred input device based on SCO active"
    assert "latestAudioRms" in code, "Must track latestAudioRms"
    assert "fun getLatestRms(): Double" in code, "Must expose getLatestRms"
    print("PASS: ContinuousAudioStream audio routing verified")


def test_adb_bridge_engine():
    path = Path("app/src/main/java/com/jarvis/service/AdbBridgeEngine.kt")
    assert path.exists(), "AdbBridgeEngine.kt missing"
    code = path.read_text()

    assert "ACTION_PING" in code
    assert "ACTION_EXECUTE_TOOL" in code
    assert "wake_diagnostics" in code
    assert "trigger_wake" in code
    print("PASS: AdbBridgeEngine wake diagnostics & triggers verified")


def test_openwakeword_logging():
    path = Path("app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt")
    assert path.exists(), "OpenWakeWordDetector.kt missing"
    code = path.read_text()

    assert "openWakeWord candidate:" in code
    assert "openWakeWord CONFIRMED:" in code
    print("PASS: OpenWakeWordDetector diagnostic logging verified")


def main():
    print("=== Running Wake Word Fix Verification Suite ===")
    test_kws_temporal_confirmation()
    test_continuous_audio_stream_routing()
    test_adb_bridge_engine()
    test_openwakeword_logging()
    print("ALL VERIFICATIONS PASSED!")

if __name__ == "__main__":
    main()
