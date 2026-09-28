package com.jarvis.ui.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.jarvis.ui.components.Ui
import com.jarvis.voice.audio.AcousticFeedbackEngine
import com.jarvis.wakeword.OwnerVoiceProfile
import com.jarvis.wakeword.TfliteOwnerVoiceVerifier
import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.VoiceEnrollmentCalibrator
import com.jarvis.wakeword.VoiceSampleRecorder
import com.jarvis.wakeword.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Unified Voice Training & Multi-Sample Calibration Wizard.
 *
 * Guides the user through 3 to 5 clean audio recordings of "Jarvis", validates acoustic parameters,
 * visualizes real-time speech energy levels, extracts TFLite 256-dim speaker embeddings,
 * and calibrates both personalized acoustic thresholds and owner voice verification templates.
 */
class VoiceEnrollmentDialog(
    private val context: Context,
    private val detector: WakeWordDetector? = null,
    private val calibrator: VoiceEnrollmentCalibrator = VoiceEnrollmentCalibrator(),
    private val voiceEngine: com.jarvis.voice.VoiceEngine? = null,
    private val onOwnerEnrolled: ((OwnerVoiceProfile) -> Unit)? = null,
    private val onProfileEnrolled: (UserVoiceProfile) -> Unit
) {
    private val recorder = VoiceSampleRecorder()
    private val feedbackEngine = runCatching { AcousticFeedbackEngine(context) }.getOrNull()
    private val dialogScope = CoroutineScope(Dispatchers.Main + Job())
    private var recordJob: Job? = null

    private var targetSampleCount = 3
    private val maxSampleCount = 5
    private val recordedSamples = mutableListOf<VoiceEnrollmentCalibrator.ValidatedSample>()
    private val recordedEmbeddings = mutableListOf<FloatArray?>()

    private var calibratedUserProfile: UserVoiceProfile? = null
    private var calibratedOwnerProfile: OwnerVoiceProfile? = null
    private var verifier: TfliteOwnerVoiceVerifier? = null

    private var alertDialog: AlertDialog? = null

    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.SURFACE_CONTAINER)
            setPadding(Ui.dp(context, 20), Ui.dp(context, 20), Ui.dp(context, 20), Ui.dp(context, 20))
        }

        // Header: Icon + Title
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 8))
        }
        val iconView = TextView(context).apply {
            text = "🎙"
            textSize = 20f
            setPadding(0, 0, Ui.dp(context, 8), 0)
        }
        val titleView = TextView(context).apply {
            text = "UNIFIED VOICE TRAINING WIZARD"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        headerRow.addView(iconView)
        headerRow.addView(titleView)
        root.addView(headerRow)

        // Subtitle & Instructions
        val subtitleView = TextView(context).apply {
            text = "Record 3 to 5 clean samples saying 'Jarvis' to train your owner voice embedding and calibrate wake-word sensitivity to your room acoustics."
            textSize = 12f
            setTextColor(Ui.MUTED)
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, Ui.dp(context, 14))
        }
        root.addView(subtitleView)

        // Step Indicator Chips Container
        val stepChipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 12))
        }
        root.addView(stepChipsRow)

        fun renderStepChips() {
            stepChipsRow.removeAllViews()
            val currentIdx = recordedSamples.size
            for (i in 0 until targetSampleCount) {
                val chip = TextView(context).apply {
                    text = "${i + 1}. SAMPLE ${i + 1}"
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.04f
                    gravity = Gravity.CENTER
                    setPadding(Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginEnd = Ui.dp(context, 4)
                    }

                    when {
                        i < currentIdx -> {
                            setTextColor(Ui.SUCCESS)
                            background = Ui.rounded(context, Ui.SURFACE_2, Ui.SUCCESS, 6)
                        }
                        i == currentIdx && currentIdx < targetSampleCount -> {
                            setTextColor(Ui.CYAN)
                            background = Ui.rounded(context, Ui.SURFACE_2, Ui.CYAN, 6)
                        }
                        else -> {
                            setTextColor(Ui.MUTED)
                            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 6)
                        }
                    }
                }
                stepChipsRow.addView(chip)
            }
        }
        renderStepChips()

        // Real-time Audio Level Bar Container
        val levelLabel = TextView(context).apply {
            text = "SPEECH ENERGY LEVEL"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.MUTED)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        root.addView(levelLabel)

        val levelBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(Ui.CYAN)
            progressBackgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, 8)).apply {
                bottomMargin = Ui.dp(context, 14)
            }
        }
        root.addView(levelBar)

        // Status Card / Instruction Text
        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER_GLOW, 10)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = Ui.dp(context, 14)
            }
        }
        val statusTitle = TextView(context).apply {
            text = "READY TO ENROLL"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        val statusMessage = TextView(context).apply {
            text = "Tap 'RECORD SAMPLE 1' below and say 'Jarvis' clearly at your normal speaking distance."
            textSize = 12f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.15f)
        }
        statusCard.addView(statusTitle)
        statusCard.addView(statusMessage)
        root.addView(statusCard)

        // Samples Progress List Container (shows captured samples with per-sample re-record buttons)
        val samplesListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 10))
        }
        root.addView(samplesListContainer)

        // Summary Details Card (Hidden until all target samples are completed)
        val summaryCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.CYAN_DIM, 10)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = Ui.dp(context, 14)
            }
        }
        val summaryTitle = TextView(context).apply {
            text = "UNIFIED TRAINING COMPLETE"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.SUCCESS)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        val summaryDetails = TextView(context).apply {
            text = ""
            textSize = 11.5f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.2f)
        }
        summaryCard.addView(summaryTitle)
        summaryCard.addView(summaryDetails)
        root.addView(summaryCard)

        // Action Buttons Row
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val addSampleBtn = MaterialButton(context).apply {
            text = "+ ADD EXTRA SAMPLE"
            textSize = 10f
            setTextColor(Ui.CYAN)
            strokeColor = ColorStateList.valueOf(Ui.CYAN_DIM)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = Ui.dp(context, 8)
            }
        }

        val cancelButton = MaterialButton(context).apply {
            text = "CANCEL"
            textSize = 11f
            setTextColor(Ui.MUTED)
            strokeColor = ColorStateList.valueOf(Ui.BORDER)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8))
            setOnClickListener {
                recorder.stop()
                recordJob?.cancel()
                dialogScope.cancel()
                alertDialog?.dismiss()
            }
        }

        val actionButton = MaterialButton(context).apply {
            text = "RECORD SAMPLE 1"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.BG)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = Ui.dp(context, 8)
            }
        }

        buttonRow.addView(addSampleBtn)
        buttonRow.addView(cancelButton)
        buttonRow.addView(actionButton)
        root.addView(buttonRow)

        addSampleBtn.setOnClickListener {
            if (targetSampleCount < maxSampleCount) {
                targetSampleCount++
                if (targetSampleCount >= maxSampleCount) {
                    addSampleBtn.visibility = View.GONE
                }
                renderStepChips()
                if (recordedSamples.size < targetSampleCount) {
                    summaryCard.visibility = View.GONE
                    statusCard.visibility = View.VISIBLE
                    val nextIdx = recordedSamples.size + 1
                    actionButton.text = "RECORD SAMPLE $nextIdx"
                    actionButton.backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
                    actionButton.isEnabled = true
                }
            }
        }

        fun updateSamplesListUI() {
            samplesListContainer.removeAllViews()
            if (recordedSamples.isEmpty()) return

            for (idx in recordedSamples.indices) {
                val sample = recordedSamples[idx]
                val itemRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 6)
                    setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = Ui.dp(context, 4)
                    }
                }

                val matchPct = (sample.score * 100).toInt()
                val infoText = TextView(context).apply {
                    text = "Sample ${idx + 1}: $matchPct% Match • ${sample.rmsEnergy.toInt()} RMS"
                    textSize = 11f
                    setTextColor(Ui.TEXT)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                val retryBtn = MaterialButton(context).apply {
                    text = "↺ RE-RECORD"
                    textSize = 9.5f
                    setTextColor(Ui.WARNING)
                    strokeColor = ColorStateList.valueOf(Ui.BORDER)
                    strokeWidth = Ui.dp(context, 1)
                    cornerRadius = Ui.dp(context, 6)
                    backgroundTintList = ColorStateList.valueOf(Ui.SURFACE)
                    setPadding(Ui.dp(context, 8), Ui.dp(context, 2), Ui.dp(context, 8), Ui.dp(context, 2))
                    setOnClickListener {
                        // Re-record this specific sample index
                        actionButton.isEnabled = false
                        statusTitle.text = "RE-RECORDING SAMPLE ${idx + 1}"
                        statusTitle.setTextColor(Ui.WARNING)
                        statusMessage.text = "🎙 Listening... Say 'Jarvis' clearly."
                        feedbackEngine?.playListeningTone()

                        recordJob?.cancel()
                        recordJob = dialogScope.launch {
                            val pcm = recorder.recordSample(durationMs = 2000L) { rms ->
                                levelBar.progress = (rms * 100).toInt()
                            }
                            levelBar.progress = 0

                            if (pcm != null && pcm.isNotEmpty()) {
                                val validation = calibrator.validateSample(pcm, detector)
                                if (validation is VoiceEnrollmentCalibrator.ValidationResult.Valid) {
                                    feedbackEngine?.playSuccessTone()
                                    recordedSamples[idx] = VoiceEnrollmentCalibrator.ValidatedSample(
                                        pcm = pcm,
                                        rmsEnergy = validation.rmsEnergy,
                                        score = validation.score,
                                        speechDurationMs = validation.speechDurationMs
                                    )

                                    if (verifier == null) {
                                        verifier = withContext(Dispatchers.IO) { runCatching { TfliteOwnerVoiceVerifier(context) }.getOrNull() }
                                    }
                                    val emb = verifier?.let { v -> withContext(Dispatchers.IO) { v.embed(pcm) } }
                                    if (idx < recordedEmbeddings.size) {
                                        recordedEmbeddings[idx] = emb
                                    }

                                    updateSamplesListUI()
                                    renderStepChips()
                                    statusTitle.text = "SAMPLE ${idx + 1} REPLACED"
                                    statusTitle.setTextColor(Ui.SUCCESS)
                                    statusMessage.text = "Updated sample ${idx + 1} with new recording."
                                } else if (validation is VoiceEnrollmentCalibrator.ValidationResult.Rejected) {
                                    statusTitle.text = "RE-RECORD FAILED"
                                    statusTitle.setTextColor(Ui.ERROR)
                                    statusMessage.text = validation.message
                                }
                            }
                            actionButton.isEnabled = true
                        }
                    }
                }

                itemRow.addView(infoText)
                itemRow.addView(retryBtn)
                samplesListContainer.addView(itemRow)
            }
        }

        fun finalizeTrainingAndShowSummary() {
            // Acoustic Calibration
            val userProfile = calibrator.calibrate(recordedSamples)
            calibratedUserProfile = userProfile

            // Speaker Embedding Fusion
            val validEmbeddings = recordedEmbeddings.filterNotNull()
            if (validEmbeddings.isNotEmpty()) {
                calibratedOwnerProfile = runCatching { OwnerVoiceProfile.fromEmbeddings(validEmbeddings) }.getOrNull()
            }

            statusCard.visibility = View.GONE
            summaryCard.visibility = View.VISIBLE
            summaryDetails.text = buildString {
                append("• Keyword Recognition Match: ${(userProfile.averageScore * 100).toInt()}%\n")
                append("• Calibrated Threshold: ${String.format(Locale.US, "%.2f", userProfile.calibratedThreshold)}\n")
                append("• Min Energy Floor: ${userProfile.calibratedMinEnergyRms.toInt()} RMS\n")
                append("• Audio Samples Captured: ${userProfile.sampleCount}\n")
                append("• Owner Speaker Model: ${if (calibratedOwnerProfile != null) "TFLite 256-dim Vector Generated" else "Not Available"}\n")
                append("• Status: Ready to activate")
            }

            actionButton.text = "SAVE & ACTIVATE"
            actionButton.backgroundTintList = ColorStateList.valueOf(Ui.SUCCESS)
            actionButton.isEnabled = true
        }

        actionButton.setOnClickListener {
            val currentSampleIdx = recordedSamples.size
            if (currentSampleIdx >= targetSampleCount) {
                // Save and activate profiles
                calibratedUserProfile?.let { userProf ->
                    onProfileEnrolled(userProf)
                }
                calibratedOwnerProfile?.let { ownerProf ->
                    onOwnerEnrolled?.invoke(ownerProf)
                }
                feedbackEngine?.playSuccessTone()
                dialogScope.cancel()
                alertDialog?.dismiss()
                return@setOnClickListener
            }

            // Start recording next sample
            actionButton.isEnabled = false
            actionButton.text = "RECORDING (2s)..."
            statusTitle.text = "RECORDING SAMPLE ${currentSampleIdx + 1}/$targetSampleCount"
            statusTitle.setTextColor(Ui.WARNING)
            statusMessage.text = "🎙 Listening... Please say 'Jarvis' now."
            feedbackEngine?.playListeningTone()

            recordJob?.cancel()
            recordJob = dialogScope.launch {
                val pcm = recorder.recordSample(durationMs = 2000L) { normalizedRms ->
                    levelBar.progress = (normalizedRms * 100).toInt()
                }
                levelBar.progress = 0

                if (pcm == null || pcm.isEmpty()) {
                    statusTitle.text = "RECORDING FAILED"
                    statusTitle.setTextColor(Ui.ERROR)
                    statusMessage.text = "Microphone error or permission denied. Tap to retry."
                    actionButton.isEnabled = true
                    actionButton.text = "RE-TRY SAMPLE ${currentSampleIdx + 1}"
                    return@launch
                }

                statusTitle.text = "ANALYZING ACOUSTICS & EMBEDDING"
                statusTitle.setTextColor(Ui.CYAN)
                statusMessage.text = "Validating vocal envelope, speech energy, and neural speaker embedding..."

                // Run acoustic validation
                val validation = calibrator.validateSample(pcm, detector)
                when (validation) {
                    is VoiceEnrollmentCalibrator.ValidationResult.Valid -> {
                        feedbackEngine?.playSuccessTone()
                        val validSample = VoiceEnrollmentCalibrator.ValidatedSample(
                            pcm = pcm,
                            rmsEnergy = validation.rmsEnergy,
                            score = validation.score,
                            speechDurationMs = validation.speechDurationMs
                        )
                        recordedSamples.add(validSample)

                        // Run TFLite speaker encoder in background
                        if (verifier == null) {
                            verifier = withContext(Dispatchers.IO) { runCatching { TfliteOwnerVoiceVerifier(context) }.getOrNull() }
                        }
                        val emb = verifier?.let { v -> withContext(Dispatchers.IO) { v.embed(pcm) } }
                        recordedEmbeddings.add(emb)
                        pcm.fill(0) // Privacy clear PCM

                        renderStepChips()
                        updateSamplesListUI()

                        if (recordedSamples.size < targetSampleCount) {
                            val nextIdx = recordedSamples.size + 1
                            val matchPct = (validation.score * 100).toInt()
                            statusTitle.text = "SAMPLE ${recordedSamples.size} ACCEPTED ($matchPct% MATCH)"
                            statusTitle.setTextColor(Ui.SUCCESS)
                            statusMessage.text = "Great! Clean sample captured. Ready for Sample $nextIdx of $targetSampleCount."
                            actionButton.text = "RECORD SAMPLE $nextIdx"
                            actionButton.isEnabled = true
                        } else {
                            finalizeTrainingAndShowSummary()
                        }
                    }
                    is VoiceEnrollmentCalibrator.ValidationResult.Rejected -> {
                        statusTitle.text = "SAMPLE REJECTED"
                        statusTitle.setTextColor(Ui.ERROR)
                        statusMessage.text = validation.message
                        actionButton.text = "RE-TRY SAMPLE ${currentSampleIdx + 1}"
                        actionButton.isEnabled = true
                    }
                }
            }
        }

        voiceEngine?.suspendContinuousAudio()

        alertDialog = AlertDialog.Builder(context)
            .setView(root)
            .setCancelable(false)
            .setOnDismissListener {
                verifier?.close()
                verifier = null
                voiceEngine?.resumeContinuousAudioAndReArm(calibratedUserProfile)
            }
            .create().apply {
                window?.setBackgroundDrawable(
                    GradientDrawable().apply {
                        setColor(Ui.SURFACE_CONTAINER)
                        cornerRadius = Ui.dp(context, 16).toFloat()
                        setStroke(Ui.dp(context, 1), Ui.BORDER_GLOW)
                    }
                )
            }

        alertDialog?.show()
    }
}
