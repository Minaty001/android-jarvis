# Manage Jarvis Notifications and Isolate Third-Party Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralize and manage all of Jarvis's own notifications with custom actionable buttons and throttled state updates, add granular notification channel controls in Settings, and enforce strict read-only isolation so third-party notifications (WhatsApp, Telegram, SMS, system apps) are never altered, dismissed, or cancelled.

**Architecture:** Create a unified `JarvisNotificationManager` to register all system notification channels (Service, Reminders, Proactive Alerts, Daily Briefings, Overlay), attach interactive quick action buttons (🎙 Talk, 🛑 Sleep/Pause) to the ongoing service notification, debounce high-frequency state updates (500ms throttle), add channel preference toggles in Settings, and enforce strict package isolation.

**Tech Stack:** Kotlin, Android NotificationManagerCompat, NotificationChannel, PendingIntent, AndroidX Core, SharedPreferences.

**Spec:** The user requested: "manage jarvis notifications and dont change others", selecting all four capabilities:
1. Quick action buttons (🎙 Talk, 🛑 Sleep/Pause) on Jarvis ongoing notification.
2. Enforce strict read-only observation for other apps (zero interference).
3. Throttle/debounce status updates on ongoing notification.
4. Channel preference toggles in Settings for Reminders, Proactive Alerts, and Daily Briefing.

---

## Global Constraints
- Must not touch, dismiss, cancel, or alter notifications belonging to packages other than `com.jarvis`.
- Ongoing foreground service notification must remain low-priority/silent to avoid disturbing the user.
- Throttling must not delay user-initiated action button responses.
- All existing notification and wake-word tests must pass without regressions.

---

## Review Focus
1. Third-Party Isolation: Verify no code path ever calls `cancelNotification()` on non-Jarvis packages.
2. Throttling Integrity: Rapid voice state changes (e.g. 5 state transitions in 100ms) must not spam Android `NotificationManager.notify()`.
3. Notification Quick Actions: Tapping "🎙 Talk" must start manual listening; tapping "🛑 Sleep/Pause" must stop the engine cleanly.
4. Settings Preference Persistence: Toggles for Reminders, Proactive Alerts, and Daily Briefings must persist across app restarts.
5. Android API Compatibility: Use `PendingIntent.FLAG_IMMUTABLE` and check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` for notification channel calls.

---

### Task 1: Create `JarvisNotificationManager` with Channels, Action Builders, and State Throttling

**Files:**
- Create: `app/src/main/java/com/jarvis/notification/JarvisNotificationManager.kt`
- Create: `app/src/test/java/com/jarvis/notification/JarvisNotificationManagerTest.kt`

**Interfaces:**
- Consumes: `Context`, `statusText: String`, `isListening: Boolean`
- Produces: `Notification`, `shouldUpdate(newContent: String): Boolean`, `initializeChannels(context: Context)`

- [ ] **Step 1: Write failing unit test for `JarvisNotificationManager`**

Create `app/src/test/java/com/jarvis/notification/JarvisNotificationManagerTest.kt`:
```kotlin
package com.jarvis.notification

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JarvisNotificationManagerTest {

    @Before
    fun setup() {
        JarvisNotificationManager.resetThrottlerForTesting()
    }

    @Test
    fun testThrottlerSuppressesIdenticalContent() {
        assertTrue(JarvisNotificationManager.shouldUpdateNotification("Wake word active", 1000L))
        assertFalse("Same content must not trigger notification update", 
            JarvisNotificationManager.shouldUpdateNotification("Wake word active", 1100L))
    }

    @Test
    fun testThrottlerEnforcesMinimumIntervalForDifferentContent() {
        assertTrue(JarvisNotificationManager.shouldUpdateNotification("Wake word active", 1000L))
        assertFalse("Rapid change within 500ms must be throttled", 
            JarvisNotificationManager.shouldUpdateNotification("Listening...", 1200L))
        assertTrue("Change after 500ms must be allowed", 
            JarvisNotificationManager.shouldUpdateNotification("Listening...", 1501L))
    }

    @Test
    fun testStrictThirdPartyIsolation() {
        assertTrue(JarvisNotificationManager.isJarvisNotification("com.jarvis", 1001))
        assertFalse(JarvisNotificationManager.isJarvisNotification("com.whatsapp", 1001))
        assertFalse(JarvisNotificationManager.isJarvisNotification("com.google.android.gm", 1001))
    }

    @Test
    fun testChannelConstantsDefined() {
        assertEquals("jarvis_service_channel", JarvisNotificationManager.CHANNEL_SERVICE)
        assertEquals("jarvis_reminders", JarvisNotificationManager.CHANNEL_REMINDERS)
        assertEquals("jarvis_proactive_channel", JarvisNotificationManager.CHANNEL_ALERTS)
        assertEquals("jarvis_briefing_channel", JarvisNotificationManager.CHANNEL_BRIEFINGS)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "com.jarvis.notification.JarvisNotificationManagerTest"`
Expected: FAIL (unresolved symbol `JarvisNotificationManager`)

- [ ] **Step 3: Implement `JarvisNotificationManager.kt`**

Create `app/src/main/java/com/jarvis/notification/JarvisNotificationManager.kt`:
```kotlin
package com.jarvis.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jarvis.service.JarvisForegroundService

/**
 * Central coordinator for all of Jarvis's own notifications.
 * Manages notification channels, builds ongoing service notifications with actionable buttons,
 * throttles rapid state changes to prevent notification shade flicker,
 * and strictly isolates Jarvis notifications so third-party notifications are never touched.
 */
object JarvisNotificationManager {
    const val CHANNEL_SERVICE = "jarvis_service_channel"
    const val CHANNEL_REMINDERS = "jarvis_reminders"
    const val CHANNEL_ALERTS = "jarvis_proactive_channel"
    const val CHANNEL_BRIEFINGS = "jarvis_briefing_channel"
    const val CHANNEL_OVERLAY = "jarvis_overlay_channel"

    const val NOTIFICATION_ID_SERVICE = 1001

    private const val MIN_UPDATE_INTERVAL_MS = 500L
    @Volatile private var lastUpdatedContent: String = ""
    @Volatile private var lastUpdateTimeMs: Long = 0L

    @Synchronized
    fun shouldUpdateNotification(newContent: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (newContent == lastUpdatedContent) return false
        if (currentTimeMs - lastUpdateTimeMs < MIN_UPDATE_INTERVAL_MS) return false
        lastUpdatedContent = newContent
        lastUpdateTimeMs = currentTimeMs
        return true
    }

    @Synchronized
    fun resetThrottlerForTesting() {
        lastUpdatedContent = ""
        lastUpdateTimeMs = 0L
    }

    fun isJarvisNotification(packageName: String, notificationId: Int): Boolean {
        return packageName == "com.jarvis"
    }

    fun initializeChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Service & Background Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Jarvis wake word and background service active"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timed reminders, alarms, and scheduled tasks"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_ALERTS,
                "Proactive Alerts & Health",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Battery warnings, contextual alerts, and system health suggestions"
            },
            NotificationChannel(
                CHANNEL_BRIEFINGS,
                "Daily Briefings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Morning and evening executive digests"
            },
            NotificationChannel(
                CHANNEL_OVERLAY,
                "Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating assistant orb indicator"
                setShowBadge(false)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun buildServiceNotification(
        context: Context,
        content: String
    ): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Talk / Manual Listening Trigger
        val talkIntent = Intent(context, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_START_WITH_WAKE
        }
        val talkPendingIntent = PendingIntent.getService(
            context,
            1,
            talkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Sleep / Stop Engine
        val stopIntent = Intent(context, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_btn_speak_now, "🎙 Talk", talkPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "🛑 Sleep", stopPendingIntent)
            .build()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "com.jarvis.notification.JarvisNotificationManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/jarvis/notification/JarvisNotificationManager.kt app/src/test/java/com/jarvis/notification/JarvisNotificationManagerTest.kt
git commit -m "feat(notifications): add JarvisNotificationManager with channels, quick actions, and state throttling"
```

---

### Task 2: Integrate `JarvisNotificationManager` into `JarvisForegroundService`

**Files:**
- Modify: `app/src/main/java/com/jarvis/service/JarvisForegroundService.kt:85-110, 345-395, 530-565`

**Interfaces:**
- Consumes: `JarvisNotificationManager.initializeChannels()`, `JarvisNotificationManager.buildServiceNotification()`, `JarvisNotificationManager.shouldUpdateNotification()`
- Produces: Actionable ongoing foreground notification with throttled state updates

- [ ] **Step 1: Update `JarvisForegroundService.kt` to use `JarvisNotificationManager`**

In `app/src/main/java/com/jarvis/service/JarvisForegroundService.kt`:
1. In `onCreate()`:
   Replace `createNotificationChannel()` with `JarvisNotificationManager.initializeChannels(this)`.
2. In `createNotification(content: String)`:
   Delegate to `JarvisNotificationManager.buildServiceNotification(this, content)`.
3. In `updateNotification(content: String)`:
   Check `if (!JarvisNotificationManager.shouldUpdateNotification(content)) return` before calling `manager.notify()`.
4. In `notificationJob`:
   Retain clean status mapping, now cleanly debounced.

- [ ] **Step 2: Run service and notification tests**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "*Notification*"`
Expected: PASS

- [ ] **Step 3: Commit changes**

```bash
git add app/src/main/java/com/jarvis/service/JarvisForegroundService.kt
git commit -m "refactor(service): connect JarvisForegroundService to JarvisNotificationManager with action buttons and debounced updates"
```

---

### Task 3: Enforce Read-Only Third-Party Notification Isolation in Listener & Tools

**Files:**
- Modify: `app/src/main/java/com/jarvis/notification/JarvisNotificationListenerService.kt:15-55`
- Modify: `app/src/main/java/com/jarvis/tools/NotificationsTool.kt:45-60`
- Create: `app/src/test/java/com/jarvis/notification/NotificationIsolationTest.kt`

**Interfaces:**
- Consumes: `StatusBarNotification`
- Produces: Strict passive observation (never calls `cancelNotification()` for external apps)

- [ ] **Step 1: Write unit test verifying third-party notification isolation**

Create `app/src/test/java/com/jarvis/notification/NotificationIsolationTest.kt`:
```kotlin
package com.jarvis.notification

import org.junit.Assert.*
import org.junit.Test

class NotificationIsolationTest {

    @Test
    fun testOnlyJarvisPackageIsSubjectToInternalDismissal() {
        val externalPackages = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "android"
        )
        for (pkg in externalPackages) {
            assertFalse(
                "External package $pkg must never be marked as a Jarvis notification",
                JarvisNotificationManager.isJarvisNotification(pkg, 123)
            )
        }
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "com.jarvis.notification.NotificationIsolationTest"`
Expected: PASS

- [ ] **Step 3: Document and verify strict isolation in `JarvisNotificationListenerService.kt` and `NotificationsTool.kt`**

In `app/src/main/java/com/jarvis/notification/JarvisNotificationListenerService.kt`:
Add architectural guarantee documentation and verify that no `cancelNotification` calls exist.
In `app/src/main/java/com/jarvis/tools/NotificationsTool.kt`:
Clarify in descriptions that clearing promotions only affects the in-memory assistant buffer and leaves system notifications untouched on the user's phone.

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/jarvis/notification/JarvisNotificationListenerService.kt app/src/main/java/com/jarvis/tools/NotificationsTool.kt app/src/test/java/com/jarvis/notification/NotificationIsolationTest.kt
git commit -m "feat(notifications): enforce strict read-only isolation for third-party notifications"
```

---

### Task 4: Add Granular Notification Channel Toggles in Settings and Preferences

**Files:**
- Modify: `app/src/main/java/com/jarvis/ui/model/UiModels.kt:20-30`
- Modify: `app/src/main/java/com/jarvis/ui/data/UiPreferencesStore.kt:120-180`
- Modify: `app/src/main/java/com/jarvis/ui/screens/SettingsScreen.kt:300-340, 870-940`
- Create: `app/src/test/java/com/jarvis/ui/SettingsNotificationPreferencesTest.kt`

**Interfaces:**
- Consumes: `AppSettings`
- Produces: `reminderNotificationsEnabled`, `proactiveAlertsEnabled`, `dailyBriefingNotificationsEnabled`

- [ ] **Step 1: Write unit test for notification preferences persistence**

Create `app/src/test/java/com/jarvis/ui/SettingsNotificationPreferencesTest.kt`:
```kotlin
package com.jarvis.ui

import com.jarvis.ui.model.AppSettings
import org.junit.Assert.*
import org.junit.Test

class SettingsNotificationPreferencesTest {

    @Test
    fun testDefaultSettingsHaveChannelsEnabled() {
        val settings = AppSettings()
        assertTrue(settings.notificationsEnabled)
        assertTrue(settings.reminderNotificationsEnabled)
        assertTrue(settings.proactiveAlertsEnabled)
        assertTrue(settings.dailyBriefingNotificationsEnabled)
    }

    @Test
    fun testSettingsAllowCustomChannelToggles() {
        val settings = AppSettings(
            notificationsEnabled = true,
            reminderNotificationsEnabled = true,
            proactiveAlertsEnabled = false,
            dailyBriefingNotificationsEnabled = false
        )
        assertTrue(settings.reminderNotificationsEnabled)
        assertFalse(settings.proactiveAlertsEnabled)
        assertFalse(settings.dailyBriefingNotificationsEnabled)
    }
}
```

- [ ] **Step 2: Run test to verify it compiles and passes**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "com.jarvis.ui.SettingsNotificationPreferencesTest"`

- [ ] **Step 3: Update `UiModels.kt`, `UiPreferencesStore.kt`, and `SettingsScreen.kt`**

1. In `app/src/main/java/com/jarvis/ui/model/UiModels.kt`:
   Add fields to `AppSettings`:
   - `val reminderNotificationsEnabled: Boolean = true`
   - `val proactiveAlertsEnabled: Boolean = true`
   - `val dailyBriefingNotificationsEnabled: Boolean = true`
2. In `app/src/main/java/com/jarvis/ui/data/UiPreferencesStore.kt`:
   Serialize and deserialize the 3 new fields in JSON.
3. In `app/src/main/java/com/jarvis/ui/screens/SettingsScreen.kt`:
   Add UI switches for:
   - "Reminders & Alarms"
   - "Proactive Alerts & Health"
   - "Daily Briefing Cards"
   Add button: "SYSTEM NOTIFICATION CHANNELS" to open Android system channel settings directly via `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.

- [ ] **Step 4: Run full unit test suite**

Run: `export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew testDebugUnitTest --tests "*Notification*"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/jarvis/ui/model/UiModels.kt app/src/main/java/com/jarvis/ui/data/UiPreferencesStore.kt app/src/main/java/com/jarvis/ui/screens/SettingsScreen.kt app/src/test/java/com/jarvis/ui/SettingsNotificationPreferencesTest.kt
git commit -m "feat(settings): add granular notification channel toggles and system channel link"
```

---

### Task 5: Assemble, Deploy, and Verify on Physical Device (`LM-K420`)

**Files:** None (build & device verification)

- [ ] **Step 1: Package clean Debug APK**

Run:
```bash
rm -f app/build/outputs/apk/debug/app-debug.apk
export JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11
export PATH=$JAVA_HOME/bin:$PATH
./gradlew packageDebug
```
Expected: `app/build/outputs/apk/debug/app-debug.apk` built successfully.

- [ ] **Step 2: Install to physical phone via ADB**

Run:
```bash
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.jarvis/.ui.MainActivity
```
Expected: `Success` and `MainActivity` launched.

- [ ] **Step 3: Verify notification channels and quick actions via ADB**

Inspect active notification channels:
```bash
adb shell dumpsys notification --noredact | grep -A 5 "Channel{jarvis_"
```
Verify `jarvis_service_channel`, `jarvis_reminders`, `jarvis_proactive_channel`, `jarvis_briefing_channel` exist.

---

## Verification Plan

### Automated Tests
1. `./gradlew testDebugUnitTest --tests "com.jarvis.notification.*"` -> All tests PASS.
2. `./gradlew testDebugUnitTest --tests "com.jarvis.ui.SettingsNotificationPreferencesTest"` -> PASS.
3. `./gradlew testDebugUnitTest --tests "*Wake*"` -> All wake-word tests continue to PASS with zero regressions.

### Manual Verification on Connected Device
1. Pull down notification shade: Confirm "J.A.R.V.I.S." notification appears with "🎙 Talk" and "🛑 Sleep" buttons.
2. Tap "🎙 Talk": Confirm Jarvis begins listening immediately.
3. Open Settings -> Notifications: Confirm individual channel switches for Reminders, Proactive Alerts, and Daily Briefings are visible and functional.
4. Check other apps' notifications (e.g. WhatsApp / SMS): Confirm third-party notifications remain untouched and undisturbed in the notification shade.
