package com.jarvis.cognitive.policy

import com.jarvis.cognitive.capability.JciCapability
import com.jarvis.cognitive.capability.JciCapabilityRegistry
import com.jarvis.cognitive.capability.JciParamType
import com.jarvis.cognitive.capability.JciRiskLevel
import java.util.concurrent.ConcurrentHashMap

data class JciPolicyDecision(
    val allowed: Boolean,
    val requiresConfirmation: Boolean = false,
    val reason: String
) {
    companion object {
        fun allow(reason: String = "Action approved by policy") =
            JciPolicyDecision(allowed = true, requiresConfirmation = false, reason = reason)

        fun requireConfirmation(reason: String = "Action requires user confirmation") =
            JciPolicyDecision(allowed = false, requiresConfirmation = true, reason = reason)

        fun refuse(reason: String) =
            JciPolicyDecision(allowed = false, requiresConfirmation = false, reason = reason)
    }
}

class JciPolicyEngine(
    var strictMediumRisk: Boolean = false,
    var isDeviceLockedProvider: () -> Boolean = { false }
) {
    // Sliding window timestamp history per capability
    private val invocationHistory = ConcurrentHashMap<String, MutableList<Long>>()

    // Deduplication tracking: key -> (lastTimestamp, argsHashCode)
    private val lastInvocationArgs = ConcurrentHashMap<String, Pair<Long, Int>>()

    fun resetRateLimits() {
        invocationHistory.clear()
        lastInvocationArgs.clear()
    }

    fun evaluate(
        toolName: String,
        params: Map<String, String> = emptyMap(),
        userConfirmed: Boolean = false,
        isExecutionDispatch: Boolean = true
    ): JciPolicyDecision {
        val capability = JciCapabilityRegistry.getCapability(toolName)
            ?: return JciPolicyDecision.refuse("Unregistered capability: '$toolName' is not in the system registry.")

        return evaluateCapability(capability, params, userConfirmed, isExecutionDispatch)
    }

    fun evaluateCapability(
        capability: JciCapability,
        params: Map<String, String> = emptyMap(),
        userConfirmed: Boolean = false,
        isExecutionDispatch: Boolean = true
    ): JciPolicyDecision {
        val capId = capability.id

        // 1. Emergency SOS and safety detectors are life-critical exemptions
        if (capId in setOf("EMERGENCY_SOS", "SOUND_DETECTOR")) {
            return JciPolicyDecision.allow("Safety and emergency capabilities are exempt from gating.")
        }

        // 2. Critical destructive invariant: Hard refuse by default regardless of model score
        if (capability.riskLevel == JciRiskLevel.CRITICAL) {
            return JciPolicyDecision.refuse("Critical destructive action is refused by security policy.")
        }

        // 3. Lock-screen security
        if (isDeviceLockedProvider()) {
            val isLockScreenSafe = capability.riskLevel == JciRiskLevel.LOW &&
                    capId in setOf("FLASHLIGHT", "CLOCK", "BATTERY_CHECK", "SCREEN_UNLOCK")
            if (!isLockScreenSafe) {
                return JciPolicyDecision.requireConfirmation("Device is locked. Unlock device to execute ${capability.id}.")
            }
        }

        // 4. Argument validation
        val argValidation = validateArguments(capability, params)
        if (!argValidation.allowed) {
            return argValidation
        }

        // 5. Rate limiting & Deduplication (enforced only during active execution dispatch)
        if (isExecutionDispatch) {
            val rateLimitDecision = checkRateLimits(capability, params)
            if (!rateLimitDecision.allowed) {
                return rateLimitDecision
            }
        }

        // 6. High risk gating (model CANNOT bypass)
        if (capability.riskLevel == JciRiskLevel.HIGH) {
            if (!userConfirmed) {
                return JciPolicyDecision.requireConfirmation("High-risk action '${capability.id}' requires explicit user confirmation.")
            }
        }

        // 7. Medium risk gating (optional strict mode)
        if (strictMediumRisk && capability.riskLevel == JciRiskLevel.MEDIUM && !userConfirmed) {
            return JciPolicyDecision.requireConfirmation("Medium-risk action '${capability.id}' requires confirmation in strict mode.")
        }

        // Record successful evaluation in rate limiter if dispatching
        if (isExecutionDispatch) {
            recordInvocation(capability, params)
        }

        return JciPolicyDecision.allow()
    }

    private fun validateArguments(
        capability: JciCapability,
        params: Map<String, String>
    ): JciPolicyDecision {
        for (def in capability.parameters) {
            val value = params[def.name]?.trim()

            // Required check
            if (def.required && (value == null || value.isEmpty())) {
                return JciPolicyDecision.refuse("Missing required parameter '${def.name}' for capability '${capability.id}'.")
            }

            if (value != null && value.isNotEmpty()) {
                // Allowed values check
                if (def.allowedValues != null && def.allowedValues.none { it.equals(value, ignoreCase = true) }) {
                    return JciPolicyDecision.refuse("Invalid value '$value' for parameter '${def.name}'. Allowed: ${def.allowedValues}")
                }

                // Type checks
                when (def.type) {
                    JciParamType.INT -> {
                        val intVal = value.toIntOrNull()
                            ?: return JciPolicyDecision.refuse("Parameter '${def.name}' must be an integer, got '$value'.")
                        if (def.minVal != null && intVal < def.minVal) {
                            return JciPolicyDecision.refuse("Parameter '${def.name}' must be >= ${def.minVal.toInt()}, got $intVal.")
                        }
                        if (def.maxVal != null && intVal > def.maxVal) {
                            return JciPolicyDecision.refuse("Parameter '${def.name}' must be <= ${def.maxVal.toInt()}, got $intVal.")
                        }
                    }
                    JciParamType.FLOAT -> {
                        val floatVal = value.toDoubleOrNull()
                            ?: return JciPolicyDecision.refuse("Parameter '${def.name}' must be a number, got '$value'.")
                        if (def.minVal != null && floatVal < def.minVal) {
                            return JciPolicyDecision.refuse("Parameter '${def.name}' must be >= ${def.minVal}, got $floatVal.")
                        }
                        if (def.maxVal != null && floatVal > def.maxVal) {
                            return JciPolicyDecision.refuse("Parameter '${def.name}' must be <= ${def.maxVal}, got $floatVal.")
                        }
                    }
                    JciParamType.BOOLEAN -> {
                        if (!value.equals("true", ignoreCase = true) && !value.equals("false", ignoreCase = true)) {
                            return JciPolicyDecision.refuse("Parameter '${def.name}' must be boolean (true/false), got '$value'.")
                        }
                    }
                    JciParamType.PHONE_NUMBER -> {
                        val normalized = value.replace(Regex("[\\s\\-\\(\\)]"), "")
                        if (!Regex("^\\+?[0-9]{3,15}\$").matches(normalized)) {
                            return JciPolicyDecision.refuse("Invalid phone number format for '${def.name}': '$value'.")
                        }
                    }
                    JciParamType.FILE_PATH -> {
                        // Check directory traversal
                        if (value.contains("..") || value.contains("/../")) {
                            return JciPolicyDecision.refuse("Path traversal is strictly prohibited: '$value'.")
                        }
                        // Check dangerous system paths
                        val dangerousPrefixes = listOf("/system", "/etc", "/proc", "/dev", "/sys", "/sbin", "/root")
                        for (prefix in dangerousPrefixes) {
                            if (value.startsWith(prefix)) {
                                return JciPolicyDecision.refuse("Access to protected system path '$value' is forbidden.")
                            }
                        }
                    }
                    else -> { /* String, AppName */ }
                }

                // Custom regex check
                if (def.regexPattern != null && !def.regexPattern.matches(value)) {
                    return JciPolicyDecision.refuse("Parameter '${def.name}' does not match required format.")
                }
            }
        }

        return JciPolicyDecision.allow()
    }

    private fun checkRateLimits(
        capability: JciCapability,
        params: Map<String, String>
    ): JciPolicyDecision {
        val now = System.currentTimeMillis()
        val capId = capability.id

        // 1. Rapid duplicate check (same capability and args within 1000ms)
        val last = lastInvocationArgs[capId]
        val argsHash = params.hashCode()
        if (last != null) {
            val (lastTime, lastHash) = last
            if (now - lastTime < 1000L && argsHash == lastHash) {
                return JciPolicyDecision.refuse("Duplicate invocation of '$capId' throttled (executed < 1s ago with identical parameters).")
            }
        }

        // 2. Sliding window check
        val history = invocationHistory.computeIfAbsent(capId) { mutableListOf() }
        synchronized(history) {
            // Retain only entries from last 60 seconds
            val cutoff = now - 60_000L
            history.removeAll { it < cutoff }

            if (history.size >= capability.rateLimitMaxPerMinute) {
                return JciPolicyDecision.refuse("Rate limit exceeded for capability '$capId' (max ${capability.rateLimitMaxPerMinute}/min).")
            }
        }

        return JciPolicyDecision.allow()
    }

    private fun recordInvocation(
        capability: JciCapability,
        params: Map<String, String>
    ) {
        val now = System.currentTimeMillis()
        val capId = capability.id

        lastInvocationArgs[capId] = Pair(now, params.hashCode())

        val history = invocationHistory.computeIfAbsent(capId) { mutableListOf() }
        synchronized(history) {
            history.add(now)
        }
    }
}
