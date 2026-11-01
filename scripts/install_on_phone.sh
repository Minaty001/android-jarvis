#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== Jarvis Android ADB Auto-Installer ==="
echo "Checking ADB connection..."

DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo "Phone not recognized by ADB yet."
    echo "Please ensure USB Debugging is turned ON in Developer Options."
    echo "Waiting for device..."
    adb wait-for-device
    DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
fi

echo "Connected Device: $(adb devices -l | grep "$DEVICE")"

# Discover the newest APK in the repository
APK_PATH="${1:-}"

if [ -z "$APK_PATH" ]; then
    for candidate in \
        "$DIR/jarvis-ai.apk" \
        "$DIR/jarvis-app.apk" \
        "$DIR/app/build/outputs/apk/debug/jarvis-ai.apk" \
        "$DIR/app/build/outputs/apk/release/jarvis-ai.apk" \
        "$DIR/dist/jarvis-ai.apk" \
        "$DIR/dist/jarvis-v1.1-release.apk" \
        "$DIR/dist/jarvis-v1.1-debug.apk" \
        "$DIR/app/build/outputs/apk/release/app-release.apk" \
        "$DIR/app/build/outputs/apk/debug/app-debug.apk"; do
        if [ -f "$candidate" ]; then
            APK_PATH="$candidate"
            break
        fi
    done
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: Could not find any installable APK in $DIR."
    exit 1
fi

echo "Installing $APK_PATH with permissions granted (-g)..."
adb -s "$DEVICE" install -r -d -g "$APK_PATH"

echo "Whitelisting com.jarvis from battery optimization (Doze bypass)..."
adb -s "$DEVICE" shell "dumpsys deviceidle whitelist +com.jarvis" 2>/dev/null || true

echo "Launching Jarvis MainActivity..."
adb -s "$DEVICE" shell am start -n com.jarvis/.ui.MainActivity

echo "✅ Jarvis successfully installed, optimized, and launched on $DEVICE!"
