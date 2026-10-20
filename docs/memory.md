# Project Snapshot

## Project and build

- Single Android application module: `:app`, application ID `com.jarvis`.
- `versionName` 1.1, `versionCode` 2, minimum API 28, compile SDK 35, Java 17.
- Build debug and release APKs with `./gradlew assembleDebug assembleRelease`.
- `./export_release.sh` auto-detects Java 17 (`/home/shanu/jdk/jdk-17.0.13+11`) and Android SDK paths, builds release with R8 shrinking, and exports release artifacts to both `dist/` and `release/`.
- Exported release artifacts:
  - `dist/jarvis-v1.1-release.apk` (~12.2 MB) and `dist/jarvis-v1.1-release.apk.sha256`
  - `release/jarvis-v1.1-release.apk` (~12.2 MB) and `release/jarvis-v1.1-release.apk.sha256`
  - Debug artifacts: `dist/jarvis-v1.1-debug.apk` and `dist/jarvis-v1.1-debug.apk.sha256`
- Complete local unit test suite: **855 tests passing across 160 test suites, 0 failures, 0 errors** (`./gradlew testDebugUnitTest`).

## Core source locations

- `JarvisForegroundService.kt`: service lifecycle and assistant startup.
- `VoiceEngine.kt`, `voice/router/VoiceRouter.kt`, and `voice/audio/ContinuousAudioStream.kt`: speech, audio capture, and routing.
- `AssistantRuntime.kt` and `AgentKernel.kt`: assistant coordination and legacy tool planning/execution fallback.
- `cognitive/CognitiveOrchestrator.kt`: Jarvis Cognitive Interface (JCI) choke-point orchestrator managing `DIRECT`, `PLAN`, `CLARIFY`, and `REFUSE` modes.
- `cognitive/capability/JciCapabilityRegistry.kt`: 71 canonical audited capabilities with typed schemas and risk tiers.
- `cognitive/policy/JciPolicyEngine.kt` and `cognitive/verifier/JciVerifier.kt`: safety policy gatekeeper and honesty verification (Levels 1–4).
- `cognitive/planner/JciPlanner.kt` and `cognitive/planner/JciPlanExecutor.kt`: task decomposition and verified execution.
- `cognitive/memory/JciMemoryManager.kt`: working, epistemic, and episodic memory with PII redaction.
- `cognitive/style/JciStyleFormatter.kt` and `cognitive/style/JciProactiveEngine.kt`: MCU-Jarvis voice formatting and quiet hours (22:00–08:00).
- `emotion/EmotionalLifecycleController.kt` and `emotion/EmotionalStateReducer.kt`: Emotional Intelligence Engine reducer and state tracker.
- `emotion/DeterministicSentimentAnalyzer.kt` and `emotion/VoiceProfileAdapter.kt`: lexical sentiment parser and voice/orb adaptation.
- `memory/MemoryStore.kt`: SQLite memory, FTS4 indexing, retrieval ranking, expiry, and conversation history.
- `memory/AugmentedMemoryPipeline.kt` and `memory/MemorySummarizerJob.kt`: context assembly and optional session summaries.
- `network/JarvisLanServer.kt`: local HTTP/API and web asset serving.
- `wear/WearableCompanionReceiver.kt`: wearable wake, command, and stop broadcasts.
- `sensors/PocketAndMotionManager.kt`: pocket and motion sensor lifecycle.
- `autonomous/PassiveMemoryEngine.kt`: Silently logs context (foreground apps, screen text) in the background to build proactive context for future interactions.

## Current implementation notes

- **Jarvis Cognitive Core (JCI)**: Choke-point at `AssistantRuntime.executeCommand`. Feature-flagged (`JCI_ENABLED`, default false; `JCI_SHADOW`, default true when enabled). Evaluates deterministic single-step commands in sub-millisecond time (<1.5ms, 0 LLM tokens).
- **Honesty Invariant**: Level 1 (local read), Level 2 (OS verified), Level 3 (synthetic observed), Level 4 (dispatched only). Prevents claiming "done" for operations that were only submitted.
- **Emotional Intelligence Engine**: Pure deterministic state reducer over 7 affective modes. Dynamic color and pulsing transformations for `JarvisOrbView` with reduced-motion accessibility support.
- The primary memory store uses SQLite FTS4 candidates and lexical overlap, importance, recency, and access count. The separate document RAG subsystem uses BM25, lexical similarity, and optional deterministic hashed character n-gram vectors; those vectors are not semantic embeddings.
- Session summaries are generated only when an LLM client is available and stored as conversation task artifacts. Empty and `NONE` responses are ignored.
- Memory reads purge TTL-expired rows, including their FTS entries.
- Audio capture retries recoverable `AudioRecord` failures, backs off on zero-length reads, and resets stream state after unrecoverable errors so capture can restart.
- Wearable wake and command requests can start a cold foreground service while preserving the requested action.
- LAN HTTP responses stream web assets as bytes; API request bodies honor UTF-8 byte lengths and are size-limited.

## Verification state for the current workspace

- Full unit test verification: **855 passed, 0 failures, 0 errors across 160 suites** on 2026-10-21.
- Release APK build and assembly: `assembleRelease` with R8 code and resource shrinking completed successfully on 2026-10-21.
- Export verification: `./export_release.sh` generated `dist/jarvis-v1.1-release.apk` (12,756,138 bytes) and `release/jarvis-v1.1-release.apk` with SHA-256 verification.
- Release APK badging verified: `com.jarvis` (versionCode 2, versionName "1.1", compileSdkVersion 35, minSdkVersion 28, targetSdkVersion 35).

## Useful references

1. [Architecture](ARCHITECTURE.md)
2. [Local features and commands](JARVIS_LOCAL_FEATURES_AND_COMMANDS.md)
3. [System overview](Jarvis_System_Overview.md)
4. [Device test report](DEVICE_TEST_REPORT.md)
5. [Emotional Intelligence Engine](EMOTIONAL_INTELLIGENCE_ENGINE.md)
6. [JCI Audit & Changelog](jci/CHANGELOG.md)
