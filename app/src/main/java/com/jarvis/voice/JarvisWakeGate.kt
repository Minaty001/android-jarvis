package com.jarvis.voice

import com.jarvis.wakeword.OwnerVoiceProfile

class JarvisWakeGate(
    private val ownerVerificationAvailable: () -> Boolean,
    private val verifyOwner: (ShortArray) -> Boolean,
    private val updateOwnerProfile: (OwnerVoiceProfile?) -> Unit = {}
) {
    companion object {
        private const val WAKE_THRESHOLD = 0.55f
        private const val REQUIRED_HITS = 2
        private const val COOLDOWN_MS = 2500L
    }

    private var hitCount = 0
    private var lastActivation = 0L

    fun processWakeScore(
        wakeScore: Float,
        candidateAudio: ShortArray
    ): Boolean {
        val now = System.currentTimeMillis()

        if (now - lastActivation < COOLDOWN_MS) {
            return false
        }

        if (wakeScore < WAKE_THRESHOLD) {
            hitCount = 0
            return false
        }

        if (!ownerVerificationAvailable()) {
            hitCount = 0
            lastActivation = now
            return true
        }

        hitCount++

        if (hitCount < REQUIRED_HITS) {
            return false
        }

        hitCount = 0

        if (!verifyOwner(candidateAudio)) {
            return false
        }

        lastActivation = now
        return true
    }

    fun setOwnerProfile(profile: OwnerVoiceProfile?) = updateOwnerProfile(profile)
}
