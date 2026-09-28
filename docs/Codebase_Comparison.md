# JARVIS Codebase Comparison Report

This document outlines the architectural and feature differences between two variations of the JARVIS project:
1. **Primary Project (Current):** `/home/shanu/Pictures/android-jarvis-main`
2. **Desktop Project (Alternative):** `/home/shanu/Desktop/android-jarvis`

---

## 1. Feature Modules & Parity

The `/home/shanu/Desktop/android-jarvis` repository contains several high-level experimental or standalone modules. Owner voice verification is now present in both codebases; Stitch, Emergency, and Meeting remain Desktop-specific in this comparison.

### 1.1 Owner Voice Verification (present in both)
Owner voice verification is now present in the current `main` project as well as the Desktop implementation. The current enrollment flow saves an `OwnerVoiceProfile`; the normal wake gate uses the TFLite speaker encoder when both that profile and the model are available. If either is unavailable, wake-word activation falls back to keyword detection without the owner check.

The three-template DTW setup is a separate screen-local matcher and is not connected to normal wake detection.

### 1.2 "Stitch" UI Module
*   **Added Files:** `com.jarvis.stitch.*`
*   **Impact:** Contains the Stitch dashboard and Skills Screen rebuild features. (HTML/PNG previews exist in the `main` docs but the actual Kotlin package `stitch` only exists in the Desktop version).

### 1.3 Emergency & Meeting Modules
*   **Added Files:** `com.jarvis.emergency.*`, `com.jarvis.meeting.*`
*   **Impact:** Suggests advanced integrations for handling emergency situations (e.g., auto-dialing, location sharing) and meeting management (e.g., auto-muting, transcriptions).

---

## 2. Refactoring & Cleanup in Primary (Main) Version

The `/home/shanu/Pictures/android-jarvis-main` repository shows evidence of clean architecture refactoring and technical debt resolution that isn't present in the Desktop branch.

### 2.1 Interface Segregation
The `main` branch abstracts concrete classes into interfaces to improve testability and Dependency Injection.
*   **Added Files:** _(removed: single-impl interfaces deleted per YAGNI audit)_
*   **Impact:** The Desktop version relies on concrete `MemoryStore.kt` and `RagIndexStore.kt` directly.

### 2.2 Skill Persistence (Recent Fixes)
*   **Changes in `LearnedSkillStore.kt`:** The `main` branch recently implemented `java.io.Serializable` and Object Streams to save learned procedural UI macros to disk across device restarts. The Desktop version likely still holds these only in RAM (technical debt).

### 2.3 Additional Abstractions
*   **Added Files:** `MemoryRepository.kt` (Abstracts raw DB access), _(removed: single-impl wrapper deleted per YAGNI audit)_

---

## 3. Summary of Core Divergence

| Feature Area | `android-jarvis-main` (Pictures) | `android-jarvis` (Desktop) |
| :--- | :--- | :--- |
| **Architecture** | Clean, direct implementation (YAGNI-audited) | Monolithic, concrete classes |
| **Procedural Memory** | **Persistent** (survives app reboots) | **Volatile** (in-memory only) |
| **Voice Security** | Optional owner verification with wake-word fallback (`TfliteOwnerVoiceVerifier`) | Owner verification (`TfliteOwnerVoiceVerifier`) |
| **Experimental Modes** | None | **Emergency**, **Meeting**, & **Stitch** modules |

### Conclusion & Recommendation
The `main` branch is cleaner, better abstracted, and more stable (e.g., fixes applied to memory persistence). The Desktop branch still holds experimental Emergency and Meeting modules.
**Recommendation:** The owner-verification path no longer needs to be ported from Desktop. Consider porting the `emergency` and `meeting` modules only if those features are needed.
