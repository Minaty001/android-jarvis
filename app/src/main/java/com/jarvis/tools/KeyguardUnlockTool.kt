package com.jarvis.tools

import android.content.Context
import com.jarvis.accessibility.UiAutomationManager
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

/**
 * KeyguardUnlockTool
 *
 * Hands-free keyguard dismissal and voice PIN entry.
 * Includes rate-limiting (max 3 attempts per 60 seconds),
 * input validation (4-8 digits), and masked PIN reporting for privacy.
 */
class KeyguardUnlockTool(private val context: Context? = null) : Tool {

    override val name: String = "KEYGUARD_UNLOCK"
    override val description: String =
        "Dismisses keyguard or unlocks device using voice PIN. Parameters: 'pin' (4-8 digits) or none for touchless swipe."

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("pin", "string", "Optional 4 to 8 digit numeric PIN", required = false),
            ParameterSchema("action", "string", "Action type (e.g. 'unlock' or 'dismiss')", required = false)
        ),
        riskLevel = RiskLevel.MEDIUM
    )

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val WINDOW_MS = 60_000L
        private val attemptTimestamps = mutableListOf<Long>()

        /**
         * Validates that the provided PIN contains between 4 and 8 digits only.
         */
        fun validatePin(pin: String?): String? {
            if (pin.isNullOrBlank()) return null
            val clean = pin.trim()
            if (clean.length in 4..8 && clean.all { it.isDigit() }) {
                return clean
            }
            return null
        }

        /**
         * Rate-limiting check: returns true if under limit, false if 3 attempts reached within 60s.
         */
        @Synchronized
        fun checkRateLimit(): Boolean {
            val now = System.currentTimeMillis()
            attemptTimestamps.removeAll { now - it > WINDOW_MS }
            if (attemptTimestamps.size >= MAX_ATTEMPTS) {
                return false
            }
            attemptTimestamps.add(now)
            return true
        }

        /**
         * Resets the rate limiter (used for tests and successful authentications).
         */
        @Synchronized
        fun resetRateLimit() {
            attemptTimestamps.clear()
        }
    }

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (!checkRateLimit()) {
            return ToolResult(
                success = false,
                message = "Too many unlock attempts. Please wait 60 seconds."
            )
        }

        val rawPin = params["pin"] ?: params["code"] ?: params["passcode"]
        if (rawPin != null) {
            val validPin = validatePin(rawPin)
            if (validPin == null) {
                return ToolResult(
                    success = false,
                    message = "Invalid PIN format. PIN must be 4 to 8 digits."
                )
            }
            val masked = "*".repeat(validPin.length)

            val ctx = context ?: return ToolResult(
                success = true,
                message = "Keyguard dismissed with PIN $masked [Simulation Mode].",
                data = mapOf("masked_pin" to masked, "pin_length" to validPin.length.toString())
            )

            val res = UiAutomationManager.unlockScreen(ctx, validPin)
            val cleanMessage = if (res.message.contains(validPin)) {
                res.message.replace(validPin, masked)
            } else {
                res.message
            }
            return ToolResult(res.success, cleanMessage, mapOf("masked_pin" to masked))
        }

        val ctx = context ?: return ToolResult(
            success = true,
            message = "Keyguard dismissed via swipe gesture [Simulation Mode].",
            data = mapOf("action" to "dismiss_keyguard")
        )

        return UiAutomationManager.unlockScreen(ctx, null)
    }
}
