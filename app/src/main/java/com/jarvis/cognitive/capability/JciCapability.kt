package com.jarvis.cognitive.capability

enum class JciToolClassification {
    READ_ONLY,
    STATE_CHANGE,
    DESTRUCTIVE,
    EXTERNAL_COMMUNICATION
}

enum class JciRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class JciEvidenceLevel(val rank: Int) {
    NONE(0),               // Level 0: best-effort, no check possible
    TOOL_RETURN(1),        // Level 1: tool returned success boolean / 0 exit code
    STATE_OBSERVED(2),     // Level 2: state read-back confirms real-world change
    USER_CONFIRMED(3),     // Level 3: user explicitly confirmed outcome
    SUBMISSION_ACK(4),     // Level 4: dispatched / queued, outcome pending
    UNVERIFIABLE(5);       // Level 5: could not verify

    val canSupportDone: Boolean
        get() = this == TOOL_RETURN || this == STATE_OBSERVED || this == USER_CONFIRMED
}

enum class JciParamType {
    STRING,
    INT,
    FLOAT,
    BOOLEAN,
    PHONE_NUMBER,
    FILE_PATH,
    APP_NAME
}

data class JciParamDefinition(
    val name: String,
    val type: JciParamType,
    val required: Boolean = false,
    val minVal: Double? = null,
    val maxVal: Double? = null,
    val allowedValues: Set<String>? = null,
    val regexPattern: Regex? = null,
    val description: String = ""
)

data class JciCapability(
    val id: String,
    val aliases: Set<String> = emptySet(),
    val description: String,
    val classification: JciToolClassification,
    val riskLevel: JciRiskLevel,
    val expectedEvidenceLevel: JciEvidenceLevel,
    val rollbackAvailable: Boolean = false,
    val parameters: List<JciParamDefinition> = emptyList(),
    val rateLimitMaxPerMinute: Int = 30
)
