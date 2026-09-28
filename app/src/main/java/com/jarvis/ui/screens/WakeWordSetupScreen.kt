package com.jarvis.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.jarvis.ui.MainActivity
import com.jarvis.ui.components.Ui
import com.jarvis.voice.audio.AcousticFeedbackEngine
import com.jarvis.wakeword.DtwBackgroundListeningService
import com.jarvis.wakeword.DtwMatcher
import com.jarvis.wakeword.VoiceEnrollmentCalibrator
import com.jarvis.wakeword.VoiceSampleRecorder
import com.jarvis.wakeword.WakeWordTemplateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Custom Wake Word Setup & Continuous DTW Background Listening UI Screen.
 *
 * Allows the user to record 2 seconds of audio 3 times to generate
 * 3 separate local audio template files, and test continuous background
 * listening with configurable DTW matching threshold strictness.
 */
class WakeWordSetupScreen(
    private val context: Context,
    private val onNavigateBack: () -> Unit
) : ScrollView(context) {

    private val templateStore = WakeWordTemplateStore(context)
    private val dtwMatcher = DtwMatcher(context, templateStore)
    private val backgroundService = DtwBackgroundListeningService(context, dtwMatcher)
    private val recorder = VoiceSampleRecorder()
    private val calibrator = VoiceEnrollmentCalibrator()
    private val feedbackEngine = runCatching { AcousticFeedbackEngine(context) }.getOrNull()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var recordJob: Job? = null

    init {
        setBackgroundColor(Ui.BG)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 24))
        }

        // Top Navigation Bar
        val navBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 12))
        }

        val backBtn = MaterialButton(context).apply {
            text = "← DASHBOARD"
            textSize = 10.5f
            setTextColor(Ui.CYAN)
            strokeColor = ColorStateList.valueOf(Ui.BORDER_GLOW)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
            setOnClickListener {
                onNavigateBack()
            }
        }

        val screenTitle = TextView(context).apply {
            text = "CUSTOM WAKE WORD SETUP"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.TEXT)
            letterSpacing = 0.05f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        navBar.addView(backBtn)
        navBar.addView(screenTitle)
        root.addView(navBar)

        // Main Glass Card
        val card = Ui.glassCard(context, Ui.BORDER_GLOW)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16))
        }

        inner.addView(TextView(context).apply {
            text = "1. RECORD 3 AUDIO TEMPLATES"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.06f
            setPadding(0, 0, 0, Ui.dp(context, 6))
        })

        inner.addView(TextView(context).apply {
            text = "Press 'Record Wake Word' below to capture 2 seconds of audio. Complete 3 separate recordings to build your local custom wake word templates."
            textSize = 11.5f
            setTextColor(Ui.MUTED)
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, Ui.dp(context, 14))
        })

        // Step Chips Row
        val stepChipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 12))
        }
        inner.addView(stepChipsRow)

        fun updateStepChips() {
            stepChipsRow.removeAllViews()
            val savedCount = templateStore.getSavedTemplateCount()
            for (i in 1..3) {
                val chip = TextView(context).apply {
                    text = "$i. SAMPLE $i"
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.04f
                    gravity = Gravity.CENTER
                    setPadding(Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginEnd = Ui.dp(context, 4)
                    }

                    when {
                        i <= savedCount -> {
                            setTextColor(Ui.SUCCESS)
                            background = Ui.rounded(context, Ui.SURFACE_2, Ui.SUCCESS, 6)
                        }
                        i == savedCount + 1 -> {
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
        updateStepChips()

        // Speech Level Meter
        val levelLabel = TextView(context).apply {
            text = "SPEECH ENERGY LEVEL"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.MUTED)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        inner.addView(levelLabel)

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
        inner.addView(levelBar)

        // Status Card
        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER_GLOW, 10)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = Ui.dp(context, 14)
            }
        }
        val statusTitle = TextView(context).apply {
            text = "READY TO RECORD"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        val statusMessage = TextView(context).apply {
            text = "Tap 'Record Wake Word' to capture 2 seconds of your voice."
            textSize = 12f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.15f)
        }
        statusCard.addView(statusTitle)
        statusCard.addView(statusMessage)
        inner.addView(statusCard)

        // Templates File List Container
        val fileListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 12))
        }
        inner.addView(fileListContainer)

        fun updateFileListUI() {
            fileListContainer.removeAllViews()
            val files = templateStore.getSavedTemplateFiles()
            if (files.isEmpty()) return

            fileListContainer.addView(TextView(context).apply {
                text = "SAVED AUDIO TEMPLATES"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.CYAN)
                letterSpacing = 0.08f
                setPadding(0, 0, 0, Ui.dp(context, 4))
            })

            for (idx in 0 until files.size) {
                val f = files[idx]
                val item = TextView(context).apply {
                    text = "• Template ${idx + 1}: ${f.name} (${f.length() / 1024} KB)"
                    textSize = 11f
                    setTextColor(Ui.TEXT)
                    setPadding(Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4))
                    background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 6)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = Ui.dp(context, 4)
                    }
                }
                fileListContainer.addView(item)
            }
        }
        updateFileListUI()

        // Recording Action Buttons Row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 14))
        }

        val resetBtn = MaterialButton(context).apply {
            text = "↺ RESET TEMPLATES"
            textSize = 10.5f
            setTextColor(Ui.MUTED)
            strokeColor = ColorStateList.valueOf(Ui.BORDER)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6))
            visibility = if (templateStore.getSavedTemplateCount() > 0) View.VISIBLE else View.GONE
        }

        val recordBtn = MaterialButton(context).apply {
            text = "Record Wake Word"
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

        btnRow.addView(resetBtn)
        btnRow.addView(recordBtn)
        inner.addView(btnRow)

        // Section 2: Continuous Background Listening (DTW Engine)
        val divider = View(context).apply {
            setBackgroundColor(Ui.BORDER)
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, 1)).apply {
                topMargin = Ui.dp(context, 8)
                bottomMargin = Ui.dp(context, 14)
            }
        }
        inner.addView(divider)

        inner.addView(TextView(context).apply {
            text = "2. CONTINUOUS BACKGROUND LISTENING (DTW)"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.06f
            setPadding(0, 0, 0, Ui.dp(context, 6))
        })

        inner.addView(TextView(context).apply {
            text = "Runs 1.5-second overlapping background audio chunks against DTW templates. Adjust matching strictness threshold to control noise tolerance."
            textSize = 11.5f
            setTextColor(Ui.MUTED)
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, Ui.dp(context, 12))
        })

        // Threshold Strictness Selector Buttons Row
        fun thresholdDescription(value: Float, mode: String) =
            "DTW MATCHING THRESHOLD STRICTNESS: ${String.format(Locale.US, "%.2f", value)} ($mode)"

        val thresholdLabel = TextView(context).apply {
            text = thresholdDescription(dtwMatcher.threshold, "NORMAL")
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 6))
        }
        inner.addView(thresholdLabel)

        val thresholdRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 12))
        }

        val normalThreshBtn = MaterialButton(context).apply {
            text = "NORMAL (${String.format(Locale.US, "%.2f", DtwMatcher.DEFAULT_THRESHOLD)})"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.BG)
            cornerRadius = Ui.dp(context, 6)
            backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
            setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = Ui.dp(context, 4)
            }
        }

        val strictThreshBtn = MaterialButton(context).apply {
            text = "STRICT (${String.format(Locale.US, "%.2f", DtwMatcher.STRICT_THRESHOLD)})"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.MUTED)
            strokeColor = ColorStateList.valueOf(Ui.BORDER)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 6)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
            setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        normalThreshBtn.setOnClickListener {
            dtwMatcher.threshold = DtwMatcher.DEFAULT_THRESHOLD
            thresholdLabel.text = thresholdDescription(dtwMatcher.threshold, "NORMAL")
            normalThreshBtn.setTextColor(Ui.BG)
            normalThreshBtn.backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
            strictThreshBtn.setTextColor(Ui.MUTED)
            strictThreshBtn.backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
        }

        strictThreshBtn.setOnClickListener {
            dtwMatcher.threshold = DtwMatcher.STRICT_THRESHOLD
            thresholdLabel.text = thresholdDescription(dtwMatcher.threshold, "STRICT")
            strictThreshBtn.setTextColor(Ui.BG)
            strictThreshBtn.backgroundTintList = ColorStateList.valueOf(Ui.WARNING)
            normalThreshBtn.setTextColor(Ui.MUTED)
            normalThreshBtn.backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
        }

        thresholdRow.addView(normalThreshBtn)
        thresholdRow.addView(strictThreshBtn)
        inner.addView(thresholdRow)

        // Detection Logs View Container
        val logCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 8)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = Ui.dp(context, 14)
            }
        }
        val logTitle = TextView(context).apply {
            text = "LIVE DETECTION LOGS"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        val logText = TextView(context).apply {
            text = "Background listening stopped. Save 3 templates and tap 'START BACKGROUND LISTENING'."
            textSize = 11f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.15f)
        }
        logCard.addView(logTitle)
        logCard.addView(logText)
        inner.addView(logCard)

        // Background Service Start/Stop Button
        val listenToggleBtn = MaterialButton(context).apply {
            text = "🎙 START BACKGROUND LISTENING (DTW)"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.BG)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        inner.addView(listenToggleBtn)

        fun updateUIState() {
            val saved = templateStore.getSavedTemplateCount()
            updateStepChips()
            updateFileListUI()
            resetBtn.visibility = if (saved > 0) View.VISIBLE else View.GONE
            listenToggleBtn.isEnabled = saved >= 3

            if (saved >= 3) {
                statusTitle.text = "3 TEMPLATES SAVED"
                statusTitle.setTextColor(Ui.SUCCESS)
                statusMessage.text = "All 3 custom wake word audio templates saved! You can start continuous background listening below."
                recordBtn.text = "RECORDING COMPLETE"
                recordBtn.isEnabled = false
                recordBtn.backgroundTintList = ColorStateList.valueOf(Ui.SUCCESS)
            } else {
                statusTitle.text = "READY TO RECORD SAMPLE ${saved + 1}/3"
                statusTitle.setTextColor(Ui.CYAN)
                statusMessage.text = "Press 'Record Wake Word' below and speak your custom wake word clearly."
                recordBtn.text = "Record Wake Word (${saved + 1}/3)"
                recordBtn.isEnabled = true
                recordBtn.backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
            }
        }
        updateUIState()

        backgroundService.onRmsLevelListener = { normalizedRms ->
            post { if (isAttachedToWindow) levelBar.progress = (normalizedRms * 100).toInt() }
        }

        backgroundService.onWakeWordDetectedListener = { matchResult ->
            post {
                if (isAttachedToWindow) {
                    val msg = "Wake Word Detected!\nScore: ${String.format(Locale.US, "%.2f", matchResult.similarityScore)} • Template: #${matchResult.bestMatchedTemplateIndex} • Dist: ${String.format(Locale.US, "%.2f", matchResult.normalizedDistance)}"
                    logText.text = msg
                    statusTitle.text = "WAKE WORD DETECTED!"
                    statusTitle.setTextColor(Ui.SUCCESS)
                    statusMessage.text = "Match score ${String.format(Locale.US, "%.2f", matchResult.similarityScore)} passed threshold ${String.format(Locale.US, "%.2f", dtwMatcher.threshold)}."
                }
            }
        }

        listenToggleBtn.setOnClickListener {
            if (backgroundService.isListening()) {
                backgroundService.stopListening()
                listenToggleBtn.text = "🎙 START BACKGROUND LISTENING (DTW)"
                listenToggleBtn.backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
                logText.text = "Background listening stopped."
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    (context as? MainActivity)?.let { activity ->
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                    }
                    return@setOnClickListener
                }

                backgroundService.startListening()
                listenToggleBtn.text = "⏹ STOP BACKGROUND LISTENING"
                listenToggleBtn.backgroundTintList = ColorStateList.valueOf(Ui.WARNING)
                logText.text = "Listening continuously in background (1.5s overlapping chunks)... Speak your custom wake word!"
            }
        }

        resetBtn.setOnClickListener {
            backgroundService.stopListening()
            templateStore.clearTemplates()
            updateUIState()
        }

        recordBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                (context as? MainActivity)?.let { activity ->
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                }
                statusTitle.text = "PERMISSION REQUIRED"
                statusTitle.setTextColor(Ui.WARNING)
                statusMessage.text = "Microphone permission is required to record audio. Please grant permission."
                return@setOnClickListener
            }

            val savedCount = templateStore.getSavedTemplateCount()
            val targetIdx = savedCount + 1
            if (targetIdx > 3) return@setOnClickListener

            recordBtn.isEnabled = false
            recordBtn.text = "RECORDING (2s)..."
            statusTitle.text = "RECORDING SAMPLE $targetIdx/3"
            statusTitle.setTextColor(Ui.WARNING)
            statusMessage.text = "🎙 Listening... Speak your custom wake word now (2 seconds)."
            feedbackEngine?.playListeningTone()

            recordJob?.cancel()
            recordJob = scope.launch {
                val pcm = recorder.recordSample(durationMs = 2000L) { normalizedRms ->
                    post { if (isAttachedToWindow) levelBar.progress = (normalizedRms * 100).toInt() }
                }
                levelBar.progress = 0

                if (pcm == null || pcm.isEmpty()) {
                    statusTitle.text = "RECORDING FAILED"
                    statusTitle.setTextColor(Ui.ERROR)
                    statusMessage.text = "Microphone error or capture interrupted. Tap to retry."
                    recordBtn.isEnabled = true
                    recordBtn.text = "RE-TRY SAMPLE $targetIdx/3"
                    return@launch
                }

                val validation = calibrator.validateSample(pcm)
                val success = templateStore.saveValidatedTemplate(targetIdx, pcm, validation)

                when {
                    validation is VoiceEnrollmentCalibrator.ValidationResult.Rejected -> {
                        statusTitle.text = "SAMPLE REJECTED"
                        statusTitle.setTextColor(Ui.ERROR)
                        statusMessage.text = validation.message
                        recordBtn.isEnabled = true
                        recordBtn.text = "RE-TRY SAMPLE $targetIdx/3"
                    }
                    success -> {
                        feedbackEngine?.playSuccessTone()
                        statusTitle.text = "SAMPLE $targetIdx SAVED"
                        statusTitle.setTextColor(Ui.SUCCESS)
                        statusMessage.text = "Recorded 2 seconds of audio successfully for Template $targetIdx."
                        updateUIState()
                    }
                    else -> {
                        statusTitle.text = "SAVE ERROR"
                        statusTitle.setTextColor(Ui.ERROR)
                        statusMessage.text = "Failed to save audio template to disk. Tap to retry."
                        recordBtn.isEnabled = true
                        recordBtn.text = "RE-TRY SAMPLE $targetIdx/3"
                    }
                }
            }
        }

        card.addView(inner)
        root.addView(card)
        addView(root)
    }

    fun dispose() {
        backgroundService.onWakeWordDetectedListener = null
        backgroundService.onRmsLevelListener = null
        backgroundService.stopListening()
        recorder.stop()
        recordJob?.cancel()
        scope.cancel()
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }
}
