package com.jarvis.voice

import android.content.Context
import com.jarvis.wakeword.OwnerVoiceProfile
import com.jarvis.wakeword.TfliteOwnerVoiceVerifier as SpeakerEncoder

class OwnerVoiceVerifier(
    private val context: Context
) {
    @Volatile private var profile: OwnerVoiceProfile? = null
    @Volatile private var encoder: SpeakerEncoder? = null
    private var encoderLoadAttempted = false

    @Synchronized
    fun setOwnerProfile(profile: OwnerVoiceProfile?) {
        this.profile = profile
        if (profile == null) {
            encoder?.close()
            encoder = null
            encoderLoadAttempted = false
        }
    }

    @Synchronized
    fun isAvailableAndEnrolled(): Boolean = profile != null && getEncoder()?.isAvailable() == true

    @Synchronized
    fun verifyOwner(candidateAudio: ShortArray): Boolean {
        val enrolledProfile = profile ?: return true
        val speakerEncoder = getEncoder()?.takeIf { it.isAvailable() } ?: return true
        val embedding = speakerEncoder.embed(candidateAudio) ?: return true
        return enrolledProfile.matches(embedding)
    }

    private fun getEncoder(): SpeakerEncoder? {
        if (!encoderLoadAttempted) {
            encoderLoadAttempted = true
            encoder = runCatching { SpeakerEncoder(context) }.getOrNull()
        }
        return encoder
    }
}
