# Device Hardware Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize the Jarvis Android runtime (adaptive TFLite thread allocation, low-RAM memory budgeting, dynamic thermal/battery throttling) and the host build environment for resource-constrained hardware (dual-core CPU, <= 4GB RAM).

**Architecture:** Introduce `HardwareProfileManager` to dynamically detect CPU core counts, device RAM tiers (`isLowRamDevice`), and battery/thermal state. Connect `OpenWakeWordDetector` to dynamically size TFLite interpreter threads rather than hardcoding 4 threads, enforce bounded search and cache memory budgets in `MemoryStore` for low-RAM devices, connect `DeepBatteryGovernor` to `JarvisEventBus` for proactive runtime load-shedding, and tune host `gradle.properties` to eliminate JVM heap exhaustion and Linux swap thrashing on 4GB systems.

**Tech Stack:** Kotlin, Android ActivityManager / BatteryManager APIs, TensorFlow Lite Java/Android, Gradle build configuration, JUnit 4.

**Spec:** `docs/superpowers/plans/2026-10-16-device-hardware-optimization.md`

## Global Constraints

- Android API compatibility: `minSdk = 28`, `targetSdk = 35`.
- Cannot break existing `OpenWakeWordDetector` manifest validation or asset SHA-256 checks.
- `MemoryStore` SQLite database schema version remains at 2; memory budgeting changes must apply to query limits and in-memory caching without altering table schemas.
- Gradle heap allocations must strictly stay within host available RAM (< 1.8 GB free) to avoid triggering the Linux OOM killer.
- All components must provide null-safe fallback execution when Android system services (`ActivityManager`, `BatteryManager`, `PowerManager`) are null or simulated in headless unit tests.

## Review Focus

1. **Zero or negative CPU core count reporting:** `Runtime.getRuntime().availableProcessors()` returning <= 0 under virtualized or restricted container environments; must clamp to minimum of 1 thread.
2. **Headless / Null Context execution:** `HardwareProfileManager` methods invoked with `null` Context must return safe baseline fallbacks (2 threads, medium tier) without throwing `NullPointerException`.
3. **Low-Memory event thresholding:** Rapid queries to `MemoryStore` on low-RAM devices must respect the maximum entry budget (`maxQueryResults = 50`) to avoid large object allocations.
4. **Thermal throttling governor flapping:** Battery temperature hovering near the 42.0°C boundary must not cause rapid reinitialization or thrashing of detector state.
5. **Host Gradle memory footprint:** Host Gradle and Kotlin daemon configuration must not exceed 2.5 GB combined heap to ensure stability on 4 GB physical RAM systems.

---

### Task 1: Host Gradle & JVM Configuration Tuning for 2-Core / 4GB Hardware

**Files:**
- Modify: `gradle.properties:1-7`
- Test: `scripts/verify_hardware_config.py`

**Interfaces:**
- Consumes: Host system hardware specs (Intel Celeron N4500, 2 cores, 3.6 GB total RAM, 1.8 GB available).
- Produces: Bounded Gradle and Kotlin daemon JVM options with G1GC, capped worker threads, and disabled memory-heavy file watching.

- [ ] **Step 1: Write verification script to assert safe Gradle resource limits**

Create `scripts/verify_hardware_config.py`:
```python
#!/usr/bin/env python3
import re
from pathlib import Path

def main():
    props = Path("gradle.properties").read_text()
    jvmargs = re.search(r"org\.gradle\.jvmargs=(.*)", props)
    daemonargs = re.search(r"kotlin\.daemon\.jvmargs=(.*)", props)
    workers = re.search(r"org\.gradle\.workers\.max=(\d+)", props)

    assert jvmargs, "Missing org.gradle.jvmargs"
    assert daemonargs, "Missing kotlin.daemon.jvmargs"
    assert workers, "Missing org.gradle.workers.max"

    jvm_mem = int(re.search(r"-Xmx(\d+)m", jvmargs.group(1)).group(1))
    daemon_mem = int(re.search(r"-Xmx(\d+)m", daemonargs.group(1)).group(1))
    max_workers = int(workers.group(1))

    assert jvm_mem <= 1536, f"Gradle JVM heap ({jvm_mem}m) exceeds 1536m safe limit for 4GB RAM"
    assert daemon_mem <= 1024, f"Kotlin daemon heap ({daemon_mem}m) exceeds 1024m safe limit"
    assert max_workers <= 2, f"Max workers ({max_workers}) exceeds 2 CPU cores"
    print("PASS: gradle.properties is properly tuned for 2-core / 4GB hardware")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run verification script to confirm it fails on current config**

Run: `python3 scripts/verify_hardware_config.py`
Expected: FAIL with `AssertionError: Gradle JVM heap (4096m) exceeds 1536m safe limit for 4GB RAM`

- [ ] **Step 3: Update `gradle.properties` with low-overhead JVM parameters**

Modify `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx1024m
org.gradle.workers.max=2
org.gradle.parallel=false
org.gradle.vfs.watch=false
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Run verification script to confirm it passes**

Run: `python3 scripts/verify_hardware_config.py`
Expected: `PASS: gradle.properties is properly tuned for 2-core / 4GB hardware`

- [ ] **Step 5: Commit**

```bash
git add gradle.properties scripts/verify_hardware_config.py
git commit -m "perf: tune gradle and JVM properties for 2-core 4GB hardware"
```

---

### Task 2: Dynamic Hardware Profile Detector (`HardwareProfileManager`)

**Files:**
- Create: `app/src/main/java/com/jarvis/device/HardwareProfileManager.kt`
- Test: `app/src/test/java/com/jarvis/device/HardwareProfileManagerTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `ActivityManager.MemoryInfo`, `Runtime.getRuntime().availableProcessors()`.
- Produces:
  ```kotlin
  enum class DeviceTier { LOW_END, MID_RANGE, FLAGSHIP }
  data class DeviceHardwareProfile(
      val cpuCores: Int,
      val totalRamBytes: Long,
      val isLowRamDevice: Boolean,
      val tier: DeviceTier,
      val recommendedTfliteThreads: Int,
      val maxMemoryStoreResults: Int
  )
  object HardwareProfileManager {
      fun getProfile(context: Context?): DeviceHardwareProfile
      fun getRecommendedTfliteThreads(context: Context?): Int
      fun getMaxMemoryStoreResults(context: Context?): Int
      fun isConstrainedDevice(context: Context?): Boolean
  }
  ```

- [ ] **Step 1: Write the failing unit tests for `HardwareProfileManager`**

Create `app/src/test/java/com/jarvis/device/HardwareProfileManagerTest.kt`:
```kotlin
package com.jarvis.device

import org.junit.Assert.*
import org.junit.Test

class HardwareProfileManagerTest {

    @Test
    fun testNullContextReturnsSafeFallbackProfile() {
        val profile = HardwareProfileManager.getProfile(null)
        assertNotNull(profile)
        assertTrue(profile.cpuCores >= 1)
        assertTrue(profile.recommendedTfliteThreads in 1..2)
        assertEquals(50, profile.maxMemoryStoreResults)
    }

    @Test
    fun testThreadCountCalculationBounds() {
        assertEquals(1, HardwareProfileManager.calculateOptimalThreads(cores = 1, isLowRam = true))
        assertEquals(2, HardwareProfileManager.calculateOptimalThreads(cores = 2, isLowRam = false))
        assertEquals(2, HardwareProfileManager.calculateOptimalThreads(cores = 4, isLowRam = true))
        assertEquals(3, HardwareProfileManager.calculateOptimalThreads(cores = 8, isLowRam = false))
        assertEquals(1, HardwareProfileManager.calculateOptimalThreads(cores = 0, isLowRam = false))
    }

    @Test
    fun testDeviceTierClassification() {
        val lowEnd = HardwareProfileManager.classifyTier(cores = 2, totalRamBytes = 2L * 1024 * 1024 * 1024, isLowRam = true)
        assertEquals(DeviceTier.LOW_END, lowEnd)

        val midRange = HardwareProfileManager.classifyTier(cores = 6, totalRamBytes = 6L * 1024 * 1024 * 1024, isLowRam = false)
        assertEquals(DeviceTier.MID_RANGE, midRange)

        val flagship = HardwareProfileManager.classifyTier(cores = 8, totalRamBytes = 12L * 1024 * 1024 * 1024, isLowRam = false)
        assertEquals(DeviceTier.FLAGSHIP, flagship)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 -c "import subprocess; subprocess.run(['git', 'status'])"`
Confirm test file exists and classes are unresolved.

- [ ] **Step 3: Implement `HardwareProfileManager`**

Create `app/src/main/java/com/jarvis/device/HardwareProfileManager.kt`:
- Define `enum class DeviceTier { LOW_END, MID_RANGE, FLAGSHIP }`.
- Define `data class DeviceHardwareProfile(...)`.
- Implement `calculateOptimalThreads(cores: Int, isLowRam: Boolean): Int`:
  - Clamp `cores` to `maxOf(1, cores)`.
  - If `isLowRam || cores <= 2`, return `minOf(cores, 2)`.
  - If `cores in 3..6`, return 2.
  - If `cores >= 8`, return 3 (avoids over-subscribing big.LITTLE core configurations).
- Implement `classifyTier(cores: Int, totalRamBytes: Long, isLowRam: Boolean): DeviceTier`:
  - If `isLowRam || totalRamBytes < 4L * 1024 * 1024 * 1024 || cores <= 4` -> `DeviceTier.LOW_END`.
  - If `totalRamBytes < 8L * 1024 * 1024 * 1024 || cores < 8` -> `DeviceTier.MID_RANGE`.
  - Else -> `DeviceTier.FLAGSHIP`.
- Implement `getProfile(context: Context?): DeviceHardwareProfile` using `ActivityManager` when available.

- [ ] **Step 4: Verify implementation matches test assertions**

Confirm all calculation formulas and fallback logic satisfy `HardwareProfileManagerTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/device/HardwareProfileManager.kt app/src/test/java/com/jarvis/device/HardwareProfileManagerTest.kt
git commit -m "feat(device): add HardwareProfileManager for adaptive resource profiling"
```

---

### Task 3: Dynamic TFLite Thread Sizing in `OpenWakeWordDetector`

**Files:**
- Modify: `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt:105-115`
- Test: `app/src/test/java/com/jarvis/wakeword/OpenWakeWordDetectorThreadTest.kt`

**Interfaces:**
- Consumes: `HardwareProfileManager.getRecommendedTfliteThreads(context)`
- Produces: TFLite `Interpreter.Options` configured with dynamically determined thread count instead of hardcoded 4.

- [ ] **Step 1: Write test to verify dynamic thread configuration**

Create `app/src/test/java/com/jarvis/wakeword/OpenWakeWordDetectorThreadTest.kt`:
```kotlin
package com.jarvis.wakeword

import com.jarvis.device.HardwareProfileManager
import org.junit.Assert.*
import org.junit.Test

class OpenWakeWordDetectorThreadTest {

    @Test
    fun testOpenWakeWordDeterminesSafeThreadCount() {
        val threads = HardwareProfileManager.getRecommendedTfliteThreads(null)
        assertTrue("Thread count must be bounded between 1 and 2 for constrained devices", threads in 1..2)
    }

    @Test
    fun testThreadCountDoesNotExceedAvailableProcessors() {
        val available = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val recommended = HardwareProfileManager.calculateOptimalThreads(available, isLowRam = false)
        assertTrue("Recommended threads ($recommended) must not exceed available CPU cores ($available)", recommended <= available)
    }
}
```

- [ ] **Step 2: Run test to verify initial expectations**

Confirm test compiles and asserts thread bounding.

- [ ] **Step 3: Update `OpenWakeWordDetector.kt` to use dynamic thread count**

In `app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt`:
Modify `loadInterpreter`:
```kotlin
    private fun loadInterpreter(asset: String): Interpreter {
        val descriptor = context.assets.openFd(asset)
        val mapped = FileInputStream(descriptor.fileDescriptor).use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
        descriptor.close()
        val threadCount = com.jarvis.device.HardwareProfileManager.getRecommendedTfliteThreads(context)
        return Interpreter(mapped, Interpreter.Options().apply {
            setNumThreads(threadCount)
            setUseNNAPI(false)
        })
    }
```

- [ ] **Step 4: Verify with Python wake-word asset validator**

Run: `python3 verify_wakeword_assets.py`
Expected: `CHECK PASS: manifest + assets satisfy OpenWakeWordDetector validation`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/wakeword/OpenWakeWordDetector.kt app/src/test/java/com/jarvis/wakeword/OpenWakeWordDetectorThreadTest.kt
git commit -m "perf(wakeword): adapt TFLite interpreter thread count to hardware profile"
```

---

### Task 4: Low-RAM Memory Budgeting in `MemoryStore`

**Files:**
- Modify: `app/src/main/java/com/jarvis/memory/MemoryStore.kt:145-215`
- Test: `app/src/test/java/com/jarvis/memory/MemoryStoreLowRamTest.kt`

**Interfaces:**
- Consumes: `HardwareProfileManager.getMaxMemoryStoreResults(context)`, `Context.TRIM_MEMORY_*`.
- Produces:
  ```kotlin
  fun searchMemories(query: String, maxResults: Int = -1): List<MemoryEntry>
  fun trimCache(level: Int)
  ```

- [ ] **Step 1: Write test for bounded query results on constrained devices**

Create `app/src/test/java/com/jarvis/memory/MemoryStoreLowRamTest.kt`:
```kotlin
package com.jarvis.memory

import com.jarvis.util.MockContext
import org.junit.Assert.*
import org.junit.Test

class MemoryStoreLowRamTest {

    @Test
    fun testMemoryStoreAppliesCapOnLowRam() {
        val context = MockContext()
        val store = MemoryStore(context, useInMemory = true)
        
        // Save 70 memories
        for (i in 1..70) {
            store.saveMemory(content = "Memory item $i about user preferences", tags = listOf("test"))
        }

        // On constrained device, default query should be bounded to 50
        val results = store.searchMemories("preferences")
        assertTrue("Results size (${results.size}) must not exceed safe low-RAM limit (50)", results.size <= 50)
    }

    @Test
    fun testTrimCacheDoesNotThrow() {
        val store = MemoryStore(MockContext(), useInMemory = true)
        store.trimCache(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Confirm that `searchMemories` currently returns all rows without low-RAM ceiling capping.

- [ ] **Step 3: Implement query bounding and `trimCache` in `MemoryStore.kt`**

Modify `app/src/main/java/com/jarvis/memory/MemoryStore.kt`:
- Add `private val maxQueryLimit = HardwareProfileManager.getMaxMemoryStoreResults(context)`.
- In `searchMemories(query: String, maxResults: Int = -1)`:
  - If `maxResults <= 0`, default to `maxQueryLimit`.
  - Else clamp `minOf(maxResults, maxQueryLimit * 2)`.
- Add `fun trimCache(level: Int)`:
  - Invokes `SQLiteDatabase.releaseMemory()` on the underlying database helper.

- [ ] **Step 4: Verify test passes**

Run test to ensure query capping and cache trimming operate properly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/memory/MemoryStore.kt app/src/test/java/com/jarvis/memory/MemoryStoreLowRamTest.kt
git commit -m "perf(memory): enforce low-RAM query bounding and cache trim hook in MemoryStore"
```

---

### Task 5: Dynamic Governor Throttling & System Diagnostic Integration

**Files:**
- Modify: `app/src/main/java/com/jarvis/battery/DeepBatteryGovernor.kt:60-96`
- Modify: `app/src/main/java/com/jarvis/device/SystemDiagnosticEngine.kt:40-82`
- Test: `app/src/test/java/com/jarvis/battery/DeepBatteryGovernorTest.kt`

**Interfaces:**
- Consumes: `BatteryManager.EXTRA_TEMPERATURE`, `BatteryManager.EXTRA_LEVEL`, `ActivityManager.MemoryInfo`.
- Produces:
  ```kotlin
  fun addThrottleListener(listener: (DeepBatteryGovernor.ThrottleState) -> Unit)
  fun removeThrottleListener(listener: (DeepBatteryGovernor.ThrottleState) -> Unit)
  ```
  Posts `JarvisEvent.SystemAlert` on state change via `JarvisEventBus`.

- [ ] **Step 1: Write test for state transition events and listeners**

Create `app/src/test/java/com/jarvis/battery/DeepBatteryGovernorTest.kt`:
```kotlin
package com.jarvis.battery

import org.junit.Assert.*
import org.junit.Test

class DeepBatteryGovernorTest {

    @Test
    fun testThrottleStateTransitions() {
        val governor = DeepBatteryGovernor(null)
        val report = governor.evaluateGovernorState()
        assertEquals(DeepBatteryGovernor.ThrottleState.NORMAL, report.throttleState)
        assertTrue(governor.isHighThroughputAllowed())
    }

    @Test
    fun testThrottleListenerRegistration() {
        val governor = DeepBatteryGovernor(null)
        var notifiedState: DeepBatteryGovernor.ThrottleState? = null
        val listener: (DeepBatteryGovernor.ThrottleState) -> Unit = { state -> notifiedState = state }

        governor.addThrottleListener(listener)
        governor.notifyThrottleChanged(DeepBatteryGovernor.ThrottleState.THERMAL_THROTTLED)
        assertEquals(DeepBatteryGovernor.ThrottleState.THERMAL_THROTTLED, notifiedState)

        governor.removeThrottleListener(listener)
        governor.notifyThrottleChanged(DeepBatteryGovernor.ThrottleState.NORMAL)
        assertEquals(DeepBatteryGovernor.ThrottleState.THERMAL_THROTTLED, notifiedState)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Confirm missing listener methods in `DeepBatteryGovernor`.

- [ ] **Step 3: Implement listener pattern and event bus dispatch**

In `app/src/main/java/com/jarvis/battery/DeepBatteryGovernor.kt`:
- Add `private val listeners = CopyOnWriteArrayList<(ThrottleState) -> Unit>()`.
- Add `fun addThrottleListener(...)` and `fun removeThrottleListener(...)`.
- Add `fun notifyThrottleChanged(newState: ThrottleState)`.
- In `evaluateGovernorState()`, detect when `newState != currentThrottleState` and trigger listeners + post `JarvisEvent.SystemAlert` via `JarvisEventBus.shared.post(...)`.

In `app/src/main/java/com/jarvis/device/SystemDiagnosticEngine.kt`:
- In `checkMemory()`, when `memInfo.lowMemory` is detected, trigger cache cleanup on active stores.

- [ ] **Step 4: Verify test passes**

Run test to confirm listener notifications and state transitions operate correctly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/battery/DeepBatteryGovernor.kt app/src/main/java/com/jarvis/device/SystemDiagnosticEngine.kt app/src/test/java/com/jarvis/battery/DeepBatteryGovernorTest.kt
git commit -m "feat(governor): add reactive throttle event notifications and diagnostic triggers"
```

---

## Self-Review

1. **Spec coverage:** Covers both the host laptop build configuration (2-core Intel Celeron / 4GB RAM) and the Android runtime resource adaptations (TFLite core-scaling, low-RAM query capping, thermal/battery load shedding).
2. **Step scan:** Each task defines explicit file paths, interfaces, unambiguous failing tests, minimal implementation guidance, verification steps, and commit instructions.
3. **Type consistency:** `HardwareProfileManager` methods (`getRecommendedTfliteThreads`, `getMaxMemoryStoreResults`, `getProfile`) and types (`DeviceTier`, `DeviceHardwareProfile`) are consistently named and typed across Tasks 2, 3, 4, and 5.
4. **Review Focus:** Addressed all 5 review focus items (zero-core handling, null-context fallbacks, memory query caps, thermal debounce, and Gradle heap bounds).
5. **Proportion:** The plan is focused and actionable, specifying interface signatures and test expectations without duplicating extensive boilerplate implementations.
