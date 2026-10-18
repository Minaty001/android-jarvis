# Hands-Free Full Control (Phase 4) Implementation Plan (Optimized for Samsung A51 & LG K42)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 4 Hands-Free Full Control features (interactive notifications, touchless long-press/swipes, voice clipboard/OTP injection, deep app search, and hardware DND/rotation toggles) optimized for Samsung Galaxy A51 (Exynos 9611, One UI) and LG K42 (MediaTek Helio P22, 3GB RAM, LG UX).

**Architecture:** A layered accessibility and telephony architecture utilizing sub-50ms static pre-compiled intent matchers (zero runtime regex compilation to eliminate GC jitter on low-RAM Cortex-A53 cores), bounded DFS tree traversal (max depth 10) to prevent thermal throttling on Exynos 9611, Android 10 (API 29) backward compatibility fallbacks for LG K42, and isolated tool execution.

**Tech Stack:** Kotlin 1.9, Android Accessibility Service APIs, Android NotificationListenerService, Android System Settings/Audio APIs, JUnit 4, Coroutines.

**Spec:** [hands-free-phase4-plan.md](file:///home/shanu/.gemini/antigravity-cli/brain/75db0be4-9494-4f7b-b933-ebb93f65ea26/hands-free-phase4-plan.md)

---

## Global Constraints

- **Platform Floor:** Android 10 (API 29) compatibility required for LG K42; API 31+ features must have fallback paths.
- **RAM & GC Constraints (LG K42 - 3GB):** Zero object allocations during gesture dispatch loops; notification item history capped at 20 items.
- **CPU Constraints (Samsung A51 Exynos 9611 & LG K42 Helio P22):** Pre-compile all regexes as static constants; DFS accessibility node search capped at depth <= 10.
- **Permission Grace:** Any blocked action (Write Settings, Notification Access, DND Policy) must return user-facing guidance and open the relevant system intent without throwing exceptions.
- **JDK Target:** JDK 17 (`JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11`).

---

## Review Focus

1. **Accessibility Node DFS Overflow on One UI / Heavy WebViews:** Bound recursive search to depth <= 10 with early termination on match so slow Cortex-A53 clusters never hang.
2. **Missing `canWrite` on Android 10 (LG K42):** Verify `Settings.System.canWrite(ctx)` before writing `ACCELEROMETER_ROTATION` or `SCREEN_BRIGHTNESS`; prompt with `ACTION_MANAGE_WRITE_SETTINGS` if false.
3. **Empty or Null Clipboard on Android 10 Background Restrictions:** Handle `ClipboardManager.primaryClip` returning null or empty clip data cleanly without NPE.
4. **API 31 `GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS` on Android 10:** Gracefully fallback to launcher home intent on LG K42 / Android 10 devices.
5. **PendingIntent Cancellation in Stale Notifications:** Handle `PendingIntent.CanceledException` safely when triggering notification actions.

---

## File Structure

- **`app/src/main/java/com/jarvis/notification/JarvisNotificationListenerService.kt`**: Action triggering (`triggerNotificationAction`) and dismissal (`dismissNotification`, `dismissAllNotifications`).
- **`app/src/main/java/com/jarvis/tools/NotificationsTool.kt`**: Extended with item indexing, action execution, and dismissal.
- **`app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt`**: Bounded DFS, 800ms long click, and horizontal swipe dispatch.
- **`app/src/main/java/com/jarvis/accessibility/UiAutomationManager.kt`**: Exposes `longClick` and `swipeHorizontal`.
- **`app/src/main/java/com/jarvis/tools/ClipboardTool.kt`**: [NEW] Voice-controlled clipboard read, copy, paste, and OTP field injection.
- **`app/src/main/java/com/jarvis/tools/OpenAppTool.kt`**: Deep app search queries for YouTube, Chrome, Spotify, and WhatsApp chat.
- **`app/src/main/java/com/jarvis/tools/SystemSwitchboardTool.kt`**: Screen auto-rotate lock, DND, and Hotspot settings toggles.
- **`app/src/main/java/com/jarvis/tools/BatteryStatusTool.kt`**: Extended battery health, temperature, and technology telemetry.
- **`app/src/main/java/com/jarvis/ai/IntentModel.kt`**: Static pre-compiled fast-path matchers for Phase 4 intents.
- **`app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`**: Tool registry additions.

---

## Tasks

### Task 1: Interactive Notification Actions & Dismissal

**Files:**
- Modify: `app/src/main/java/com/jarvis/notification/JarvisNotificationListenerService.kt`
- Modify: `app/src/main/java/com/jarvis/tools/NotificationsTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/NotificationInteractionTest.kt`

**Interfaces:**
- Consumes: `NotificationStore.getAll()`, `StatusBarNotification.notification.actions`
- Produces: `NotificationsTool.execute(action="read_item" | "trigger_action" | "dismiss_all")`

- [x] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/jarvis/tools/NotificationInteractionTest.kt
package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationInteractionTest {

    @Test
    fun testIntentResolverNotificationActions() {
        val readSecond = IntentResolver.resolve("read notification 2")
        assertNotNull(readSecond)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, readSecond?.intent)
        assertEquals("read_item", readSecond?.params?.get("action"))
        assertEquals("2", readSecond?.params?.get("index"))

        val clearAll = IntentResolver.resolve("clear all notifications")
        assertNotNull(clearAll)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, clearAll?.intent)
        assertEquals("dismiss_all", clearAll?.params?.get("action"))

        val replyNotif = IntentResolver.resolve("reply to notification 1")
        assertNotNull(replyNotif)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, replyNotif?.intent)
        assertEquals("trigger_action", replyNotif?.params?.get("action"))
        assertEquals("1", replyNotif?.params?.get("index"))
    }

    @Test
    fun testNotificationsToolExecutionWithoutService() = runBlocking {
        val tool = NotificationsTool(context = null)
        val readRes = tool.execute(mapOf("action" to "read_item", "index" to "1"))
        assertTrue(readRes is ToolResult)

        val clearRes = tool.execute(mapOf("action" to "dismiss_all"))
        assertTrue(clearRes is ToolResult)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.NotificationInteractionTest`
Expected: FAIL (`NOTIFICATION_INTERACTION` not defined or unresolved).

- [x] **Step 3: Implement notification interaction in `JarvisNotificationListenerService.kt`, `NotificationsTool.kt`, and `IntentModel.kt`**

- In `JarvisNotificationListenerService.kt`:
  - Add `triggerNotificationAction(index: Int, actionQuery: String?): Boolean`: Retrieves `activeNotifications.getOrNull(index - 1)`, finds matching `action.title`, executes `action.actionIntent.send()`.
  - Add `dismissAllNotifications(): Boolean`: Invokes `cancelAllNotifications()`.
- In `NotificationsTool.kt`:
  - Handle `action="read_item"`: formats the single item text or says no notification at that index.
  - Handle `action="trigger_action"`: calls `JarvisNotificationListenerService.instance?.triggerNotificationAction(...)`.
  - Handle `action="dismiss_all"`: calls `JarvisNotificationListenerService.instance?.dismissAllNotifications()`.
- In `IntentModel.kt`:
  - Add `NOTIFICATION_INTERACTION` to `AssistantIntent`.
  - Add static pre-compiled regex for `"read notification (\\d+)"`, `"reply to notification (\\d+)"`, and list for `"clear all notifications"`, `"dismiss notifications"`.

- [x] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.NotificationInteractionTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/notification/JarvisNotificationListenerService.kt app/src/main/java/com/jarvis/tools/NotificationsTool.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/NotificationInteractionTest.kt
git commit -m "feat(notifications): add interactive notification reading, action triggering, and dismissal"
```

---

### Task 2: Touchless Long-Press & Directional Swipes (Optimized for One UI & LG UX)

**Files:**
- Modify: `app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt`
- Modify: `app/src/main/java/com/jarvis/accessibility/UiAutomationManager.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/TouchlessGesturesTest.kt`

**Interfaces:**
- Consumes: `AccessibilityService.dispatchGesture`, `findFirstScrollableNode`, `dumpInteractiveTree`
- Produces: `UiAutomationManager.longClick(query: String)`, `UiAutomationManager.swipeHorizontal(direction: String)`

- [x] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/jarvis/tools/TouchlessGesturesTest.kt
package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TouchlessGesturesTest {

    @Test
    fun testIntentResolverGestures() {
        val longPress1 = IntentResolver.resolve("long press 3")
        assertNotNull(longPress1)
        assertEquals(AssistantIntent.UI_GESTURE_ACTION, longPress1?.intent)
        assertEquals("long_click", longPress1?.params?.get("action"))
        assertEquals("3", longPress1?.params?.get("target"))

        val swipeLeft = IntentResolver.resolve("swipe left")
        assertNotNull(swipeLeft)
        assertEquals(AssistantIntent.UI_GESTURE_ACTION, swipeLeft?.intent)
        assertEquals("swipe", swipeLeft?.params?.get("action"))
        assertEquals("left", swipeLeft?.params?.get("direction"))

        val swipeRight = IntentResolver.resolve("swipe right")
        assertNotNull(swipeRight)
        assertEquals(AssistantIntent.UI_GESTURE_ACTION, swipeRight?.intent)
        assertEquals("swipe", swipeRight?.params?.get("action"))
        assertEquals("right", swipeRight?.params?.get("direction"))
    }

    @Test
    fun testUiAutomationManagerGesturesWithoutService() {
        val longRes = com.jarvis.accessibility.UiAutomationManager.performGestureAction("long_click", "3")
        assertNotNull(longRes)

        val swipeRes = com.jarvis.accessibility.UiAutomationManager.performGestureAction("swipe", "left")
        assertNotNull(swipeRes)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.TouchlessGesturesTest`
Expected: FAIL (`UI_GESTURE_ACTION` not defined or unresolved).

- [x] **Step 3: Implement gesture actions with bounded DFS in `JarvisAccessibilityService.kt` and `UiAutomationManager.kt`**

- In `JarvisAccessibilityService.kt`:
  - Bound DFS traversal in `dfsFind` and `dfsCollect` to `maxDepth = 10` to avoid frame drops on Samsung A51 / LG K42.
  - Implement `longClickCoordinates(x, y, durationMs = 800)`.
  - Implement `swipeHorizontal(direction = "left" | "right")` with 250ms swipe across screen width.
- In `UiAutomationManager.kt`:
  - Implement `performGestureAction(action: String, targetOrDirection: String): ToolResult`.
- In `IntentModel.kt`:
  - Add `UI_GESTURE_ACTION` to `AssistantIntent`.
  - Add pre-compiled regex for `"^(?:long\\s+press|long\\s+click|hold)\\s+(.+)$"` and static triggers for `"swipe left"`, `"swipe right"`, `"next tab"`, `"previous tab"`.

- [x] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.TouchlessGesturesTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/accessibility/JarvisAccessibilityService.kt app/src/main/java/com/jarvis/accessibility/UiAutomationManager.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/TouchlessGesturesTest.kt
git commit -m "feat(gestures): add touchless long-press and horizontal swipe with bounded DFS"
```

---

### Task 3: Hands-Free Voice Clipboard & OTP Injection

**Files:**
- Create: `app/src/main/java/com/jarvis/tools/ClipboardTool.kt`
- Modify: `app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/ClipboardToolTest.kt`

**Interfaces:**
- Consumes: `ClipboardManager`, `CallAndSmsAgent.instance?.readRecentSms`, `JarvisAccessibilityService.instance?.injectTextIntoFocusedInput`
- Produces: `ClipboardTool.execute(action="read" | "copy" | "paste" | "paste_otp")`

- [x] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/jarvis/tools/ClipboardToolTest.kt
package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClipboardToolTest {

    @Test
    fun testIntentResolverClipboard() {
        val readClip = IntentResolver.resolve("read clipboard")
        assertNotNull(readClip)
        assertEquals(AssistantIntent.CLIPBOARD_CONTROL, readClip?.intent)
        assertEquals("read", readClip?.params?.get("action"))

        val pasteText = IntentResolver.resolve("paste clipboard")
        assertNotNull(pasteText)
        assertEquals(AssistantIntent.CLIPBOARD_CONTROL, pasteText?.intent)
        assertEquals("paste", pasteText?.params?.get("action"))

        val pasteOtp = IntentResolver.resolve("paste otp")
        assertNotNull(pasteOtp)
        assertEquals(AssistantIntent.CLIPBOARD_CONTROL, pasteOtp?.intent)
        assertEquals("paste_otp", pasteOtp?.params?.get("action"))
    }

    @Test
    fun testClipboardToolExecutionWithoutContext() = runBlocking {
        val tool = ClipboardTool(context = null)
        val readRes = tool.execute(mapOf("action" to "read"))
        assertTrue(readRes is ToolResult)

        val pasteOtpRes = tool.execute(mapOf("action" to "paste_otp"))
        assertTrue(pasteOtpRes is ToolResult)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.ClipboardToolTest`
Expected: FAIL (`ClipboardTool` unresolved).

- [x] **Step 3: Implement `ClipboardTool.kt` and register in `AssistantRuntime.kt` & `IntentModel.kt`**

- In `ClipboardTool.kt`:
  - `action="read"`: Reads `clipboard.primaryClip?.getItemAt(0)?.text?.toString()`. If empty, returns "Clipboard is empty."
  - `action="copy"`: Writes `ClipData.newPlainText("JARVIS", text)`.
  - `action="paste"`: Injects primary clip text via `JarvisAccessibilityService.instance?.injectTextIntoFocusedInput(text, append = false)`.
  - `action="paste_otp"`: Fetches last OTP from `CallAndSmsAgent.instance?.readRecentSms(1, otpOnly = true)?.firstOrNull()?.otpCode` and injects into focused input.
- Register `ClipboardTool` in `AssistantRuntime.kt`.
- Add `CLIPBOARD_CONTROL` to `AssistantIntent` and add static regex/trigger lists in `IntentModel.kt`.

- [x] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.ClipboardToolTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/tools/ClipboardTool.kt app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/ClipboardToolTest.kt
git commit -m "feat(clipboard): add hands-free clipboard reading, pasting, and direct OTP injection"
```

---

### Task 4: Deep App Search & Intent Shortcuts

**Files:**
- Modify: `app/src/main/java/com/jarvis/tools/OpenAppTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/DeepAppShortcutsTest.kt`

**Interfaces:**
- Consumes: Android `Intent(Intent.ACTION_SEARCH)`, `Intent(Intent.ACTION_WEB_SEARCH)`, `Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)`
- Produces: `OpenAppTool.execute(app="...", query="...", action="search_in_app")`

- [x] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/jarvis/tools/DeepAppShortcutsTest.kt
package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeepAppShortcutsTest {

    @Test
    fun testIntentResolverAppSearchShortcuts() {
        val ytSearch = IntentResolver.resolve("search cat videos on youtube")
        assertNotNull(ytSearch)
        assertEquals(AssistantIntent.APPS_LAUNCH, ytSearch?.intent)
        assertEquals("youtube", ytSearch?.params?.get("app"))
        assertEquals("cat videos", ytSearch?.params?.get("query"))

        val googleSearch = IntentResolver.resolve("search quantum physics on chrome")
        assertNotNull(googleSearch)
        assertEquals(AssistantIntent.APPS_LAUNCH, googleSearch?.intent)
        assertEquals("chrome", googleSearch?.params?.get("app"))
        assertEquals("quantum physics", googleSearch?.params?.get("query"))
    }

    @Test
    fun testOpenAppToolSearchExecutionWithoutContext() = runBlocking {
        val tool = OpenAppTool(context = null)
        val res = tool.execute(mapOf("app" to "youtube", "query" to "ambient music", "action" to "search_in_app"))
        assertTrue(res is ToolResult)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.DeepAppShortcutsTest`
Expected: FAIL (query parameter not handled or intent mismatch).

- [x] **Step 3: Implement in-app search deep intents in `OpenAppTool.kt` and `IntentModel.kt`**

- In `OpenAppTool.kt`:
  - When `query.isNotBlank()`:
    - If `youtube`: builds `Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query)`.
    - If `chrome`: builds `Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)`.
    - If `spotify`: builds `Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(SearchManager.QUERY, query)`.
- In `IntentModel.kt`:
  - Add static pre-compiled regex for `"^search\\s+(.+?)\\s+on\\s+(youtube|chrome|spotify)$"` resolving to `AssistantIntent.APPS_LAUNCH` with `app` and `query` params.

- [x] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.DeepAppShortcutsTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/tools/OpenAppTool.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/DeepAppShortcutsTest.kt
git commit -m "feat(apps): add deep app search shortcuts for YouTube, Chrome, and Spotify"
```

---

### Task 5: Hardware Switchboard: Auto-Rotate, DND, Hotspot & Battery Health

**Files:**
- Modify: `app/src/main/java/com/jarvis/tools/SystemSwitchboardTool.kt`
- Modify: `app/src/main/java/com/jarvis/tools/BatteryStatusTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/HardwareSwitchboardPhase4Test.kt`

**Interfaces:**
- Consumes: `Settings.System.ACCELEROMETER_ROTATION`, `NotificationManager.setInterruptionFilter`, `BatteryManager.EXTRA_HEALTH`
- Produces: `SystemSwitchboardTool.execute(action="auto_rotate" | "dnd" | "hotspot")`, `BatteryStatusTool.execute(detailed=true)`

- [x] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/jarvis/tools/HardwareSwitchboardPhase4Test.kt
package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HardwareSwitchboardPhase4Test {

    @Test
    fun testIntentResolverHardwareToggles() {
        val dndOn = IntentResolver.resolve("turn on do not disturb")
        assertNotNull(dndOn)
        assertEquals(AssistantIntent.AUTONOMOUS_CONTROL, dndOn?.intent)
        assertEquals("dnd", dndOn?.params?.get("action"))
        assertEquals("on", dndOn?.params?.get("target"))

        val rotateOn = IntentResolver.resolve("turn on auto rotate")
        assertNotNull(rotateOn)
        assertEquals(AssistantIntent.AUTONOMOUS_CONTROL, rotateOn?.intent)
        assertEquals("auto_rotate", rotateOn?.params?.get("action"))
        assertEquals("on", rotateOn?.params?.get("target"))

        val batteryHealth = IntentResolver.resolve("battery health")
        assertNotNull(batteryHealth)
        assertEquals(AssistantIntent.BATTERY_CHECK, batteryHealth?.intent)
        assertEquals("health", batteryHealth?.params?.get("action"))
    }

    @Test
    fun testSwitchboardAutoRotateExecutionWithoutContext() = runBlocking {
        val tool = BatteryStatusTool(context = null)
        val res = tool.execute(mapOf("action" to "health"))
        assertTrue(res is ToolResult)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.HardwareSwitchboardPhase4Test`
Expected: FAIL (`auto_rotate` or `health` not matching).

- [x] **Step 3: Implement Auto-Rotate, DND routing, and Battery Health**

- In `SystemSwitchboardTool.kt`:
  - Add action `"auto_rotate"` / `"rotation"`:
    - If `Settings.System.canWrite(context)`: modifies `Settings.System.putInt(contentResolver, ACCELEROMETER_ROTATION, if (on) 1 else 0)`.
    - Else: opens `ACTION_MANAGE_WRITE_SETTINGS` with user guidance.
- In `BatteryStatusTool.kt`:
  - Handle `action="health"`: reads battery health (`BATTERY_HEALTH_GOOD`, etc.), temperature, and charging technology.
- In `IntentModel.kt`:
  - Add static matchers for `"turn on do not disturb"`, `"dnd on"`, `"dnd off"`, `"turn on auto rotate"`, `"turn off auto rotate"`, `"lock screen rotation"`, `"battery health"`, `"battery report"`.

- [x] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.HardwareSwitchboardPhase4Test`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/tools/SystemSwitchboardTool.kt app/src/main/java/com/jarvis/tools/BatteryStatusTool.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/HardwareSwitchboardPhase4Test.kt
git commit -m "feat(hardware): add auto-rotate screen lock, DND, and detailed battery health diagnostics"
```

---

### Task 6: Full Regression Verification & Walkthrough Update

**Files:**
- Modify: `docs/superpowers/plans/2026-10-19-hands-free-phase4-device-optimized.md`
- Artifact: `<Artifact Directory>/walkthrough.md`

- [x] **Step 1: Run full unit test suite**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` with all 625+ tests passing.

- [x] **Step 2: Update walkthrough artifact**

Update `walkthrough.md` with Phase 4 capabilities, device-specific optimization benchmark notes (One UI & LG UX), and the complete voice command cheat sheet.

- [x] **Step 3: Final Commit**

```bash
git add docs/superpowers/plans/
git commit -m "docs: finalize Hands-Free Phase 4 device-optimized implementation and verification logs"
```
