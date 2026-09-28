package com.jarvis.ui.dialogs

import android.content.Context
import com.jarvis.wakeword.OwnerVoiceProfile

class OwnerVoiceEnrollmentDialog(
    private val context: Context,
    private val beforeRecording: (() -> Boolean)? = null,
    private val afterRecording: (() -> Unit)? = null,
    private val onEnrolled: (OwnerVoiceProfile) -> Unit
) {
    fun show() {
        beforeRecording?.invoke()
        VoiceEnrollmentDialog(
            context = context,
            onOwnerEnrolled = { ownerProfile ->
                afterRecording?.invoke()
                onEnrolled(ownerProfile)
            },
            onProfileEnrolled = {
                afterRecording?.invoke()
            }
        ).show()
    }
}
