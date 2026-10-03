# AI Engineering Rules & Coding Constraints - Android J.A.R.V.I.S.

## 1. Absolute Integrity Rules

### Rule 1: No Superficial Symptom Patches
NEVER resolve errors by masking symptoms, swallowing exceptions, returning dummy fallbacks, commenting out broken assertions, or deleting failing unit tests. When a test or function fails, identify why the underlying contract was broken.

### Rule 2: Inspect Logs & Stack Traces Before Diagnosing
NEVER form a diagnostic hypothesis for a runtime failure or test breakage without reading the full, un-truncated error log. Base diagnosis strictly on empirical log evidence.

### Rule 3: Never Declare Success Without Verification
NEVER claim a task is resolved, a bug is fixed, or a feature is working until you have gathered concrete, empirical runtime verification (running `./gradlew testDebugUnitTest` and `./export_release.sh`). Editing a file does not equal completing the task.

---

## 2. Codebase & Architectural Constraints

### Rule 4: Preserving API Contracts & Signatures
If modifying a function signature or data model, search the codebase using `git grep` and update every invocation site so parameter passing remains intact.

### Rule 5: No Blocking Calls on Main Looper Threads
Never invoke blocking thread synchronizations (`Thread.sleep()`, `CountDownLatch.await()`, `Future.get()`) on the Android Main UI Thread or single-threaded event dispatchers. Use Kotlin coroutines (`withContext(Dispatchers.IO)`).

### Rule 6: Honesty Contract in Tool Execution
Tools must report honest execution state. Never report action success when system permissions, OS versions, or missing APIs block the change. Open matching system Settings panel when Android OS blocks direct toggle.

---

## 3. Tool & Skill Authoring Guidelines

### Rule 7: Tool Registry Consistency
Whenever creating or modifying a core tool, register it in `ToolRegistry.kt` and `AssistantRuntime.kt` with all appropriate command aliases.

### Rule 8: Skill Intent Pattern Matching
Skills must implement bilingual English + Hindi / Hinglish trigger matching and support clean fallback logic in `canHandle()` and `execute()`.

### Rule 9: Memory & Workspace Boundary Safety
Write project files strictly inside active workspace directories. Never modify outside paths or temporary files.
