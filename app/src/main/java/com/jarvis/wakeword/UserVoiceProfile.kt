package com.jarvis.wakeword

/** Enrollment and installed-model provenance. It never changes the KWS threshold. */
data class UserVoiceProfile(
    val isEnrolled: Boolean = false,
    val enrolledAt: Long = 0L,
    val wakeWordPhrase: String = "Jarvis",
    val sampleCount: Int = 0,
    val datasetVersion: Int = 1,
    val datasetSha256: String = "",
    val modelVersion: String = "",
    val modelSha256: String = "",
    @Deprecated("Enrollment scores do not calibrate the runtime model")
    val averageScore: Float = 0.0f,
    @Deprecated("The validated model manifest owns the KWS threshold")
    val calibratedThreshold: Float = 0.65f,
    @Deprecated("Runtime energy gating is adaptive and independent of enrollment")
    val calibratedMinEnergyRms: Double = 15.0,
    @Deprecated("Runtime energy gating is adaptive and independent of enrollment")
    val ambientNoiseRms: Double = 12.0
) {
    companion object {
        val DEFAULT = UserVoiceProfile()
    }
}
