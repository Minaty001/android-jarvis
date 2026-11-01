#!/usr/bin/env bash
# =============================================================================
# Jarvis Production Release Build & Export Script
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ] && [ -d "/home/shanu/jdk/jdk-17.0.13+11" ]; then
    export JAVA_HOME="/home/shanu/jdk/jdk-17.0.13+11"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "/home/shanu/android-sdk" ]; then
    export ANDROID_HOME="/home/shanu/android-sdk"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi

GRADLE_CMD="./gradlew"

echo "=== Building Production Release APK ==="
"$GRADLE_CMD" assembleRelease --no-daemon

echo "=== Exporting Release Artifacts ==="
mkdir -p dist release
RELEASE_SRC="app/build/outputs/apk/release/jarvis-ai.apk"
if [ ! -f "$RELEASE_SRC" ]; then
    RELEASE_SRC="app/build/outputs/apk/release/app-release.apk"
fi

cp -f "$RELEASE_SRC" jarvis-ai.apk
sha256sum jarvis-ai.apk > jarvis-ai.apk.sha256

cp -f "$RELEASE_SRC" dist/jarvis-ai.apk
sha256sum dist/jarvis-ai.apk > dist/jarvis-ai.apk.sha256

cp -f "$RELEASE_SRC" dist/jarvis-v1.1-release.apk
sha256sum dist/jarvis-v1.1-release.apk > dist/jarvis-v1.1-release.apk.sha256

cp -f "$RELEASE_SRC" release/jarvis-v1.1-release.apk
sha256sum release/jarvis-v1.1-release.apk > release/jarvis-v1.1-release.apk.sha256

echo "================================================="
echo " Production APK successfully exported to:"
echo " -> jarvis-ai.apk ($(du -h jarvis-ai.apk | cut -f1))"
echo " -> dist/jarvis-ai.apk ($(du -h dist/jarvis-ai.apk | cut -f1))"
echo " -> dist/jarvis-v1.1-release.apk ($(du -h dist/jarvis-v1.1-release.apk | cut -f1))"
echo " -> Checksum: $(cat jarvis-ai.apk.sha256)"
echo "================================================="
