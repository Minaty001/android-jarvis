package com.jarvis.foundation

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class ParameterSchema(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolMetadata(
    val name: String,
    val description: String,
    val parameters: List<ParameterSchema>,
    val riskLevel: RiskLevel,
    val outputSchema: String = "text"
)

data class PolicyEvaluationResult(
    val allowed: Boolean,
    val requiresApproval: Boolean,
    val reason: String
)

class PolicyEngine(
    var autonomousFullAuto: Boolean = false,
    val autonomyEngine: AutonomyPolicyEngine = AutonomyPolicyEngine(autonomousFullAuto = autonomousFullAuto),
    var isDeviceLockedProvider: () -> Boolean = { false },
    var allowLockScreenSafeActions: Boolean = true
) {
    init {
        autonomyEngine.autonomousFullAuto = autonomousFullAuto
    }

    fun setAutonomousMode(enabled: Boolean) {
        autonomousFullAuto = enabled
        autonomyEngine.autonomousFullAuto = enabled
    }

    fun setDrivingMode(active: Boolean) {
        autonomyEngine.drivingModeActive = active
    }

    fun evaluate(toolMetadata: ToolMetadata, params: Map<String, String>): PolicyEvaluationResult {
        autonomyEngine.autonomousFullAuto = autonomousFullAuto

        // Emergency SOS and safety monitoring are life-critical and exempt from manual approval/confirmation
        val isEmergency = toolMetadata.name.uppercase() in setOf(
            "EMERGENCY_SOS", "SOS", "STOP_SOS",
            "SOUND_DETECTOR", "SOUND_MONITOR", "START_SOUND_DETECTION", "STOP_SOUND_DETECTION"
        )
        if (isEmergency) {
            return PolicyEvaluationResult(
                allowed = true,
                requiresApproval = false,
                reason = "Safety and emergency tools are exempt from approval"
            )
        }

        // Critical safety invariant: never permit destructive actions regardless of flags
        if (toolMetadata.riskLevel == RiskLevel.CRITICAL) {
            return PolicyEvaluationResult(
                allowed = false,
                requiresApproval = true,
                reason = "Critical destructive action blocked by security policy"
            )
        }

        // Lock-screen security gatekeeper: gate private/sensitive actions when device is locked
        if (isDeviceLockedProvider()) {
            val safe = allowLockScreenSafeActions &&
                    LockScreenSecurityPolicy.isActionAllowedOnLockScreen(toolMetadata.name, params, toolMetadata.riskLevel)
            if (!safe) {
                return PolicyEvaluationResult(
                    allowed = false,
                    requiresApproval = true,
                    reason = "Device is locked. Please unlock your device to perform this action."
                )
            }
        }

        // In manual mode (autonomousFullAuto = false), HIGH risk requires user approval
        val isSafeWhatsAppSubAction = (toolMetadata.name.equals("WHATSAPP", ignoreCase = true) || toolMetadata.name.equals("WHATSAPP_SEND", ignoreCase = true)) &&
            params["action"]?.lowercase() in listOf("read_messages", "read", "check_unread", "auto_reply", "set_auto_reply", "open", "reply", "reply_message", "respond")

        if (!autonomousFullAuto && toolMetadata.riskLevel == RiskLevel.HIGH && !isSafeWhatsAppSubAction) {
            return PolicyEvaluationResult(
                allowed = true,
                requiresApproval = true,
                reason = "High impact action requires user confirmation in manual mode"
            )
        }

        return autonomyEngine.evaluate(toolMetadata.name, params)
    }

    fun evaluateAction(actionType: String, params: Map<String, String>): PolicyEvaluationResult {
        autonomyEngine.autonomousFullAuto = autonomousFullAuto

        // Emergency SOS and safety monitoring are life-critical and exempt from manual approval/confirmation
        val isEmergency = actionType.uppercase() in setOf(
            "EMERGENCY_SOS", "SOS", "STOP_SOS",
            "SOUND_DETECTOR", "SOUND_MONITOR", "START_SOUND_DETECTION", "STOP_SOUND_DETECTION"
        )
        if (isEmergency) {
            return PolicyEvaluationResult(
                allowed = true,
                requiresApproval = false,
                reason = "Safety and emergency tools are exempt from approval"
            )
        }

        // Lock-screen security gatekeeper: gate private/sensitive actions when device is locked
        if (isDeviceLockedProvider()) {
            val safe = allowLockScreenSafeActions &&
                    LockScreenSecurityPolicy.isActionAllowedOnLockScreen(actionType, params)
            if (!safe) {
                return PolicyEvaluationResult(
                    allowed = false,
                    requiresApproval = true,
                    reason = "Device is locked. Please unlock your device to perform this action."
                )
            }
        }

        return autonomyEngine.evaluate(actionType, params)
    }

    companion object {
        private val defaultEngine = PolicyEngine()

        fun evaluateAction(actionType: String, params: Map<String, String> = emptyMap()): PolicyEvaluationResult {
            return defaultEngine.evaluateAction(actionType, params)
        }
    }
}
