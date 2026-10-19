# J.A.R.V.I.S. Emotional Intelligence Engine for Android

## 1. Executive Summary

The J.A.R.V.I.S. Emotional Intelligence Engine provides a production-grade, modular, event-driven affective state and persona coordination architecture for Android personal assistants. It reproduces the recognizable communication characteristics of MCU J.A.R.V.I.S.:
- **Calm professionalism**
- **Context-aware empathy**
- **Restrained, intelligent humor**
- **Clear and controlled urgency**
- **Real task-processing awareness**
- **Concise and precise responses**
- **Consistent voice and visual personality**
- **Zero continuous polling and low resource consumption**

The engine maintains a single authoritative, pure deterministic state reducer (`EmotionalStateReducer`) managed by a thread-safe coroutine lifecycle controller (`EmotionalLifecycleController`). The authoritative state drives dynamic LLM response style policies, synchronized Android TTS voice profiles, and reactive floating orb visual presentations.

---

## 2. Six Assistant Communication States

| State | Priority | Purpose & Triggers | Voice Profile (Rate / Pitch) | Visual Glow & Pulse | Expiry & Lifecycle |
|---|:---:|---|---|---|---|
| **`PANIC`** | **100** | Urgent operational safety mode. Validated critical system health violation (thermal overheat, SOS beacon, battery hazard). *Note: Task failures never trigger PANIC.* | Rate: 1.04<br>Pitch: 1.01<br>Non-interruptible | Primary: `0xFFFF334B` (Red)<br>Secondary: `0xFFD32F2F`<br>Pulse: 600ms (Urgent) | Persistent; only cleared by explicit validated recovery event. |
| **`THINKING`** | **80** | Active task processing or tool execution in flight. Triggered by genuine `TaskPhase.PROCESSING` or `WAITING_FOR_TOOL`. | Rate: 0.98<br>Pitch: 1.00<br>Interruptible | Primary: `0xFFFFCF5C` (Amber)<br>Secondary: `0xFFF57F17`<br>Pulse: 1200ms | Persistent while operation is active; clears upon task completion, cancellation, or failure. |
| **`SAD`** | **60** | Supportive communication for high-confidence emotional distress or explicit user request for support. | Rate: 0.94<br>Pitch: 0.97<br>Interruptible | Primary: `0xFF7098B8` (Muted Blue)<br>Secondary: `0xFF486581`<br>Pulse: 3200ms (Slow) | Transient (6000ms); automatically expires back to underlying baseline. |
| **`HAPPY`** | **50** | Restrained satisfaction when a useful task has genuinely and verifiably succeeded (`verified == true`). | Rate: 1.04<br>Pitch: 1.03<br>Interruptible | Primary: `0xFF4EFAAE` (Bright Green)<br>Secondary: `0xFF2EB872`<br>Pulse: 1400ms | Transient (4000ms); automatically expires back to underlying baseline. |
| **`KIDDING`** | **40** | Restrained dry MCU-style humor when user explicitly requests a joke or contextual banter is playful, provided no hazard or serious context exists. | Rate: 1.02<br>Pitch: 1.02<br>Interruptible | Primary: `0xFF9D7CFF` (Purple)<br>Secondary: `0xFF6549D5`<br>Pulse: 1800ms | Transient (4000ms); automatically expires back to underlying baseline. |
| **`FOCUSED`** | **10** | Default baseline state. Calm, concise, professional readiness. Destination for idle standby, task failures, cancellations, and expired transient states. | Rate: 1.00<br>Pitch: 1.00<br>Interruptible | Primary: `0xFF3FD0FF` (Cyan)<br>Secondary: `0xFF1FA670`<br>Pulse: 2400ms | Baseline default. |

---

## 3. Authoritative Architecture & Data Flow

```
User Voice / Text Input ──> DeterministicSentimentAnalyzer ──┐
Task Lifecycle (Kernel) ───> TaskLifecycle Hooks ────────────┼──> EmotionalEvent
SystemAlert / SOS Beacon ──> JarvisEventBus Subscriber ──────┘         │
                                                                       ▼
                                                          EmotionalLifecycleController
                                                                       │
                                                       (Atomic Mutex & Generation Gate)
                                                                       │
                                                                       ▼
                                                           EmotionalStateReducer (Pure)
                                                                       │
                                                                       ▼
                                                       StateFlow<EmotionalSnapshot> (v1)
                                                       ┌───────────────┼──────────────┐
                                                       ▼               ▼              ▼
                                                ResponseStyle     VoiceProfile   JarvisOrbView
                                                 PolicyEngine       Adapter     Floating Overlay
                                                       │               │              │
                                                       ▼               ▼              ▼
                                                  LLM Prompt       AndroidTTS      Mini HUD
                                                 Instructions      Rate/Pitch     Animations
```

### Deterministic State Precedence
Transitions are arbitrated deterministically by priority:
$$\text{PANIC (100)} > \text{THINKING (80)} > \text{SAD (60)} > \text{HAPPY (50)} > \text{KIDDING (40)} > \text{FOCUSED (10)}$$

### Key Safety Invariants
1. **Task Failure Safety:** A tool or API failure transitions to `FOCUSED` with diagnostic explanations, never `PANIC`.
2. **Strict Verification for `HAPPY`:** Speculative dispatch or unverified intent completion does not trigger `HAPPY`. `verified == true` from `VerificationEngine` is required.
3. **No LLM State Overwrites:** Generative LLM responses receive style instructions (`ResponseStyle`) but cannot directly mutate or overwrite authoritative emotional state.
4. **Stale Callback & Timer Protection:** Monotonic generation IDs reject out-of-order or stale timer callbacks.
5. **Panic Speech Preemption:** When `PANIC` is triggered, active lower-priority speech is immediately cancelled and replaced with the critical safety alert.

---

## 4. JSON State Contract (Schema Version 1)

All snapshots can be serialized and deserialized using `EmotionalJsonContract`:

```json
{
  "schemaVersion": 1,
  "sessionId": "session_1729382400000",
  "version": 4,
  "current": {
    "state": "HAPPY",
    "previousState": "THINKING",
    "reason": "task_verified_success",
    "confidence": 1.0,
    "priority": 50,
    "enteredAtMs": 1729382400120,
    "expiresAtMs": 1729382404120,
    "operationId": "task_104"
  },
  "context": {
    "taskPhase": "SUCCEEDED",
    "sentiment": "POSITIVE",
    "sentimentConfidence": 1.0,
    "source": "TASK_MANAGER"
  },
  "presentation": {
    "voiceProfile": "HAPPY",
    "uiProfile": "HAPPY",
    "humorAllowed": true,
    "speechInterruptible": true
  }
}
```

---

## 5. Component Inventory

| File Path | Description |
|---|---|
| `app/src/main/java/com/jarvis/emotion/EmotionalModels.kt` | Domain model hierarchy (`AssistantState`, `TaskPhase`, `Sentiment`, `SignalSource`, `EmotionalSnapshot`, `VoiceProfile`, `UiEmotionProfile`, `ResponseStyle`, `EmotionalEvent`, `EmotionalPreferences`). |
| `app/src/main/java/com/jarvis/emotion/EmotionalJsonContract.kt` | Schema v1 serializer and deserializer with fallback safety. |
| `app/src/main/java/com/jarvis/emotion/EmotionalStateReducer.kt` | Pure deterministic state reducer with 20 transition rules and priority arbitration. |
| `app/src/main/java/com/jarvis/emotion/DeterministicSentimentAnalyzer.kt` | Lightweight, zero-allocation rules-based sentiment and intent classifier. |
| `app/src/main/java/com/jarvis/emotion/EmotionalLifecycleController.kt` | StateFlow authority with atomic generation-guarded coroutine expiry timers. |
| `app/src/main/java/com/jarvis/emotion/ResponseStylePolicyEngine.kt` | Translates emotional snapshot into MCU J.A.R.V.I.S. prompt style directives. |
| `app/src/main/java/com/jarvis/emotion/VoiceProfileAdapter.kt` | Synchronizes speech rate and pitch with the Android TTS pipeline. |
| `app/src/main/java/com/jarvis/emotion/EmotionalIntegrationAdapter.kt` | Wires runtime task lifecycles, user sentiment, and system event bus. |
| `app/src/main/java/com/jarvis/voice/AndroidTtsEngine.kt` | Dynamic rate/pitch application with speech serialization. |
| `app/src/main/java/com/jarvis/ui/JarvisOrbView.kt` | Arc reactor visual styling, color transitions, and reduced-motion mode. |
| `app/src/main/java/com/jarvis/overlay/JarvisFloatingOverlayService.kt` | Observes emotional StateFlow and updates floating HUD and status badges. |

---

## 6. Performance Microbenchmarks

Measured on Java 17 / JVM with 5,000 warm iterations:

| Operation | Budget | Measured Latency | Factor Faster Than Budget |
|---|:---:|:---:|:---:|
| **`EmotionalStateReducer.reduce`** | < 50.0 ms | **0.0028 ms** | **17,800x** |
| **`EmotionalJsonContract.toJson`** | < 20.0 ms | **0.0490 ms** | **408x** |
| **`DeterministicSentimentAnalyzer.analyze`** | < 50.0 ms | **0.0290 ms** | **1,724x** |

---

## 7. Verification & Test Suite Summary

- **Total Unit & Integration Tests in Repository:** **790**
- **Test Pass Rate:** **100% (790 passed, 0 failures, 0 errors, 0 skipped)**
- **Dedicated Emotional Intelligence Engine Tests:** **65**
  - `AcceptanceScenariosTest.kt`: 10/10 PASS
  - `EmotionalStateReducerTest.kt`: 20/20 PASS
  - `EmotionalLifecycleControllerTest.kt`: 7/7 PASS
  - `DeterministicSentimentAnalyzerTest.kt`: 7/7 PASS
  - `ResponseStylePolicyEngineTest.kt`: 6/6 PASS
  - `EmotionalIntegrationAdapterTest.kt`: 4/4 PASS
  - `EmotionalJsonContractTest.kt`: 4/4 PASS
  - `VoiceProfileAdapterTest.kt`: 2/2 PASS
  - `OrbViewAdaptationTest.kt`: 2/2 PASS
  - `EmotionalEngineBenchmarkTest.kt`: 3/3 PASS
