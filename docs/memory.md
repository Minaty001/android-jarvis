# Project Snapshot

## Project and build

- Single Android application module: `:app`, application ID `com.jarvis`.
- `versionName` 1.1, `versionCode` 2, minimum API 28, compile SDK 35, Java 17.
- Build debug and release APKs with `./gradlew assembleDebug assembleRelease`.
- `./export_release.sh` builds and exports only the release APK and its SHA-256 file under `dist/`.
- The current exported files are `dist/jarvis-v1.1-debug.apk` and `dist/jarvis-v1.1-release.apk`, each with a neighboring `.sha256` file.
- Run tests with `./gradlew testDebugUnitTest`; do not advertise a fixed passing test count without rerunning the suite.

## Core source locations

- `JarvisForegroundService.kt`: service lifecycle and assistant startup.
- `VoiceEngine.kt`, `voice/router/VoiceRouter.kt`, and `voice/audio/ContinuousAudioStream.kt`: speech, audio capture, and routing.
- `AssistantRuntime.kt` and `AgentKernel.kt`: assistant coordination and tool planning/execution.
- `memory/MemoryStore.kt`: SQLite memory, FTS4 indexing, retrieval ranking, expiry, and conversation history.
- `memory/AugmentedMemoryPipeline.kt` and `memory/MemorySummarizerJob.kt`: context assembly and optional session summaries.
- `network/JarvisLanServer.kt`: local HTTP/API and web asset serving.
- `wear/WearableCompanionReceiver.kt`: wearable wake, command, and stop broadcasts.
- `sensors/PocketAndMotionManager.kt`: pocket and motion sensor lifecycle.

## Current implementation notes

- The primary memory store uses SQLite FTS4 candidates and lexical overlap, importance, recency, and access count. The separate document RAG subsystem uses BM25, lexical similarity, and optional deterministic hashed character n-gram vectors; those vectors are not semantic embeddings.
- Session summaries are generated only when an LLM client is available and stored as conversation task artifacts. Empty and `NONE` responses are ignored.
- Memory reads purge TTL-expired rows, including their FTS entries.
- Audio capture retries recoverable `AudioRecord` failures, backs off on zero-length reads, and resets stream state after unrecoverable errors so capture can restart.
- Wearable wake and command requests can start a cold foreground service while preserving the requested action.
- LAN HTTP responses stream web assets as bytes; API request bodies honor UTF-8 byte lengths and are size-limited.

## Verification state for the current workspace

- `assembleDebug assembleRelease` completed successfully on 2026-10-07.
- Focused tests were run during the stabilization work; the complete Gradle unit-test suite was not run successfully due to resource limits in prior attempts.
- Release APK installation via `adb install -r` succeeded on the connected Samsung SM-A515F on 2026-10-07. This confirms installation, not end-to-end feature behavior.

## Useful references

1. [Architecture](ARCHITECTURE.md)
2. [Local features and commands](JARVIS_LOCAL_FEATURES_AND_COMMANDS.md)
3. [System overview](Jarvis_System_Overview.md)
4. [Device test report](DEVICE_TEST_REPORT.md)
- `autonomous/PassiveMemoryEngine.kt`: Silently logs context (foreground apps, screen text) in the background to build proactive context for future interactions.
