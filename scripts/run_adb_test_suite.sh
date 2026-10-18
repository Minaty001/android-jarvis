#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DIR/.." && pwd)"

echo "=== JARVIS ADB Test Suite Runner ==="

# Check ADB
if ! command -v adb &>/dev/null; then
    echo "❌ Error: 'adb' command not found in PATH."
    exit 1
fi

# Check connected device
DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ Error: No ADB devices found in 'device' state."
    echo "Please ensure USB debugging is active."
    exit 1
fi

DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
echo "Target Device: $DEVICE_SERIAL ($(adb devices -l | grep "$DEVICE_SERIAL" | awk '{for(i=2;i<=NF;++i)print $i}' | tr '\n' ' '))"

# Verify or launch JARVIS app
PID=$(adb -s "$DEVICE_SERIAL" shell pidof com.jarvis 2>/dev/null || true)
if [ -z "$PID" ]; then
    echo "Starting com.jarvis.ui.MainActivity..."
    adb -s "$DEVICE_SERIAL" shell am start -n com.jarvis/.ui.MainActivity >/dev/null
    sleep 3
    PID=$(adb -s "$DEVICE_SERIAL" shell pidof com.jarvis 2>/dev/null || true)
    echo "Jarvis started with PID: $PID"
else
    echo "Jarvis process running (PID: $PID)"
fi

# Run python test suite
echo "Launching test suite..."
python3 "$DIR/test_adb_features.py" --device "$DEVICE_SERIAL" "$@"
