# Voice Command Aliases Implementation Plan

> **For agentic workers:** Execute inline task by task; preserve existing work in the current checkout.

**Goal:** Let users configure exact spoken phrase aliases for existing JARVIS commands.

**Architecture:** Add a small pure phrase expander, store aliases in `UiPreferencesStore`, expose them in Settings and settings backups, and expand the runtime input before both resolver and agent routing. The original phrase remains in conversation history; only execution uses its one-step alias expansion.

**Tech Stack:** Kotlin, Android Views, SharedPreferences, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-17-voice-command-aliases-design.md`

## Global Constraints

- Match only a full normalized utterance (trim, lowercase with `Locale.ROOT`, collapse whitespace).
- Expand once and route through the existing resolver and runtime approval flow.
- Missing alias data remains backward compatible as an empty map.
- Add no new dependency.

---

### Task 1: Phrase matching

**Files:**
- Create: `app/src/main/java/com/jarvis/ai/VoiceCommandAliases.kt`
- Create: `app/src/test/java/com/jarvis/VoiceCommandAliasesTest.kt`

**Interface:** `VoiceCommandAliases.expand(utterance: String, aliases: Map<String, String>): String`

- [x] Test exact match after case/whitespace normalization, no partial match, and unmatched passthrough.
- [x] Run the new test and confirm it fails because the function is missing.
- [x] Implement one-step exact expansion and normalized map keys.
- [x] Rerun the test and confirm it passes.

### Task 2: Persistence and backup

**Files:**
- Modify: `app/src/main/java/com/jarvis/ui/data/UiPreferencesStore.kt`
- Create: `app/src/test/java/com/jarvis/ui/data/UiPreferencesStoreTest.kt` only if the project test harness can provide an in-memory `SharedPreferences` context without new dependencies.

**Interface:** `loadVoiceCommandAliases(): Map<String, String>`, `saveVoiceCommandAlias(alias: String, command: String)`, and `deleteVoiceCommandAlias(alias: String)`.

- [x] Add coverage for alias JSON round-trip and absent-data compatibility with pure serialization assertions.
- [x] Run relevant tests to confirm the new behavior fails before production changes.
- [x] Persist aliases under their own key; normalize and validate nonblank values, updating an existing normalized key.
- [x] Include aliases in `exportSettings()` and restore them from `importSettings()`.
- [x] Run relevant tests and confirm they pass.

### Task 3: Settings editor

**Files:**
- Modify: `app/src/main/java/com/jarvis/ui/screens/SettingsScreen.kt`

- [x] Add two phrase fields, an add button, saved alias rows, and delete action using the existing Settings UI conventions.
- [x] Show validation feedback for blank aliases/targets; reject target phrases that are blank.
- [x] Mark settings dirty when aliases change and refresh rows after add/delete.

### Task 4: Runtime wiring and end-to-end verification

**Files:**
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Modify: `app/src/test/java/com/jarvis/VoiceCommandAliasesTest.kt` if a runtime boundary helper is extracted for direct coverage.

- [x] Load aliases at command execution and expand into `commandUtterance` once.
- [x] Keep conversation logging/history on original user wording; use `commandUtterance` for resolver, contextual search, kernel goal, and offline fallback. Pending clarification replies retain their original wording.
- [x] Run the focused test, full unit suite, and debug build.
- [x] Install the debug APK over ADB and confirm the package launches on the connected device.
