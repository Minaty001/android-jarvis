package com.jarvis.tools

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.jarvis.accessibility.JarvisAccessibilityService

/**
 * DisplayWakeEngine
 *
 * Dedicated controller for display wake and screen sleep.
 * Features strict 10s auto-release WakeLock to prevent battery drain on
 * low-end processors (Samsung Galaxy A51 Exynos 9611 & LG K42 MediaTek Helio P22).
 */
object DisplayWakeEngine {

    private const val TAG = "DisplayWakeEngine"
    const val MAX_WAKELOCK_TIMEOUT_MS = 10000L
    const val DEFAULT_WAKELOCK_TIMEOUT_MS = 10000L

    /**
     * Wakes the display up using PowerManager with strict timeout protection.
     */
    fun wakeDisplay(context: Context?, timeoutMs: Long = DEFAULT_WAKELOCK_TIMEOUT_MS): ToolResult {
        val effectiveTimeout = timeoutMs.coerceIn(500L, MAX_WAKELOCK_TIMEOUT_MS)

        if (context == null) {
            return ToolResult(
                success = true,
                message = "Display screen woken up [Simulation Mode].",
                data = mapOf("action" to "wake", "timeout_ms" to effectiveTimeout.toString())
            )
        }

        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                @Suppress("DEPRECATION")
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "Jarvis::DisplayWake"
                )
                wakeLock.acquire(effectiveTimeout)
                ToolResult(
                    success = true,
                    message = "Display screen woken up successfully for ${effectiveTimeout}ms.",
                    data = mapOf("action" to "wake", "timeout_ms" to effectiveTimeout.toString())
                )
            } else {
                ToolResult(false, "PowerManager unavailable to wake display.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
            ToolResult(false, "Failed to wake display: ${e.message}")
        }
    }

    /**
     * Locks the screen display immediately via AccessibilityService or DevicePolicyManager.
     */
    suspend fun lockDisplay(context: Context?): ToolResult {
        val service = JarvisAccessibilityService.instance
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            if (success) {
                return ToolResult(
                    success = true,
                    message = "Screen locked successfully.",
                    data = mapOf("action" to "lock")
                )
            }
        }

        if (context != null) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null) {
                try {
                    dpm.lockNow()
                    return ToolResult(
                        success = true,
                        message = "Screen locked via Device Policy Manager.",
                        data = mapOf("action" to "lock")
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "DevicePolicyManager.lockNow() failed: ${e.message}")
                }
            }
            return ToolResult(
                success = false,
                message = "Locking screen requires Jarvis Accessibility Service (Android 9+) or Device Administrator permission."
            )
        }

        // Test environment fallback
        return ToolResult(
            success = true,
            message = "Screen locked successfully [Simulation Mode].",
            data = mapOf("action" to "lock")
        )
    }
}
