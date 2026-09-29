package com.jarvis.ui.screens

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.jarvis.ui.components.Ui
import com.jarvis.ui.dialogs.VoiceEnrollmentDialog
import com.jarvis.voice.VoiceEngine
import com.jarvis.wakeword.WakeWordDatasetStore

/** Dataset status and recording entry point; it never starts a second microphone listener. */
class WakeWordSetupScreen(
    private val context: Context,
    private val voiceEngine: VoiceEngine?,
    private val onNavigateBack: () -> Unit
) : ScrollView(context) {
    private val datasetStore = WakeWordDatasetStore(context)

    init {
        setBackgroundColor(Ui.BG)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 24))
        }
        root.addView(MaterialButton(context).apply {
            text = "← BACK"
            setOnClickListener { onNavigateBack() }
        })
        root.addView(TextView(context).apply {
            text = "PERSONAL WAKE WORD DATASET"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 12))
        })
        val status = TextView(context).apply {
            textSize = 13f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.2f)
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER_GLOW, 10)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 14))
        }
        root.addView(status)
        root.addView(TextView(context).apply {
            text = "RUNTIME DIAGNOSTICS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 6))
        })
        val diagnosticsView = TextView(context).apply {
            textSize = 11f
            setTextColor(Ui.MUTED)
            setLineSpacing(0f, 1.15f)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10))
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 8)
        }
        root.addView(diagnosticsView)
        root.addView(TextView(context).apply {
            text = "Enrollment saves real PCM16 WAV recordings under the app's private files directory. It does not change model weights. Export samples, add unknown speech and background audio on your development machine, then run scripts/train_kws.py."
            textSize = 12f
            setTextColor(Ui.MUTED)
            setLineSpacing(0f, 1.15f)
            setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 14))
        })
        root.addView(MaterialButton(context).apply {
            text = "OPEN DATASET ENROLLMENT"
            isEnabled = voiceEngine != null
            setOnClickListener {
                val engine = voiceEngine ?: return@setOnClickListener
                VoiceEnrollmentDialog(context, engine) { profile ->
                    com.jarvis.ui.data.UiPreferencesStore(context).saveVoiceProfile(profile)
                    renderStatus(status, diagnosticsView)
                }.show()
            }
        })
        addView(root)
        renderStatus(status, diagnosticsView)
    }

    private fun renderStatus(view: TextView, diagnosticsView: TextView) {
        val count = datasetStore.getPositiveSampleCount()
        val diagnostics = voiceEngine?.wakeEngine?.getDiagnostics(count, datasetStore.getPositiveDatasetSha256())
        view.text = buildString {
            append("Phrase: Jarvis\nSamples: $count / ${WakeWordDatasetStore.TARGET_SAMPLES}\n")
            append("Dataset: ${if (count >= WakeWordDatasetStore.TARGET_SAMPLES) "READY" else "INCOMPLETE"}\n")
            append("Model: ${when {
                diagnostics == null -> "STATUS UNAVAILABLE"
                diagnostics.modelStatus == com.jarvis.voice.WakeModelStatus.MODEL_OUTDATED -> "OUTDATED"
                diagnostics.modelAvailable -> "ACTIVE"
                else -> "MISSING"
            }}\n")
            append("Dataset/model status: ${diagnostics?.modelStatus?.name?.replace('_', ' ') ?: "unavailable"}\n")
            append("Model version: ${diagnostics?.modelVersion ?: "unavailable"} • trained on ${diagnostics?.trainedSampleCount ?: "unavailable"} samples\n")
            append("Threshold: ${diagnostics?.threshold?.let { "%.3f".format(java.util.Locale.US, it) } ?: "unavailable"}\n")
            append("Model file: ${diagnostics?.modelFile ?: "unavailable"}\n")
            append("SHA-256: ${diagnostics?.modelSha256 ?: "unavailable"}")
            diagnostics?.modelError?.let { append("\n$it") }
        }
        diagnosticsView.text = if (diagnostics == null) "Wake engine diagnostics unavailable" else buildString {
            append("Available: ${diagnostics.modelAvailable} • Mode: ${diagnostics.detectorMode}\n")
            append("Input: ${diagnostics.inputShape} • Output: ${diagnostics.outputShape}\n")
            append("Last score: ${"%.4f".format(java.util.Locale.US, diagnostics.latestScore)}")
            append(" • Model output positive: ${diagnostics.outputPositive ?: "unavailable"}\n")
            append("Energy gate passed: ${diagnostics.energyGatePassed ?: "unavailable"}")
            append(" • Audio RMS (normalized): ${"%.4f".format(java.util.Locale.US, diagnostics.audioRms)}\n")
            append("Window: ${diagnostics.audioDurationMs} ms at ${diagnostics.sampleRate} Hz\n")
            append("Owner verification required: ${diagnostics.ownerVerificationRequired}")
            append(" • Model trained on ${diagnostics.trainedSampleCount ?: "unavailable"} samples")
        }
    }
}
