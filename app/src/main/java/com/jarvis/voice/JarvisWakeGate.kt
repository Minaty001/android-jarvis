package com.jarvis.voice

import com.jarvis.wakeword.OwnerVoiceProfile

/** Applies optional speaker verification after the KWS detector's sole temporal confirmation. */
class JarvisWakeGate(
    private val ownerVerificationAvailable: () -> Boolean,
    private val verifyOwner: (ShortArray) -> Boolean,
    private val updateOwnerProfile: (OwnerVoiceProfile?) -> Unit = {},
    private val requireOwnerVerification: Boolean = false,
    private val cooldownMs: Long = 1_200L,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private var lastActivation = Long.MIN_VALUE

    @Synchronized
    fun processWakeScore(wakeScore: Float, candidateAudio: ShortArray, kwsThreshold: Float): Boolean {
        val now = clockMs()
        if (lastActivation != Long.MIN_VALUE && now - lastActivation < cooldownMs) return false
        if (!wakeScore.isFinite() || !kwsThreshold.isFinite() || wakeScore < kwsThreshold) return false

        if (requireOwnerVerification) {
            if (!runCatching(ownerVerificationAvailable).getOrDefault(false)) return false
            if (!runCatching { verifyOwner(candidateAudio) }.getOrDefault(false)) return false
        }

        lastActivation = now
        return true
    }

    fun setOwnerProfile(profile: OwnerVoiceProfile?) = updateOwnerProfile(profile)
    fun isOwnerVerificationRequired(): Boolean = requireOwnerVerification
}
