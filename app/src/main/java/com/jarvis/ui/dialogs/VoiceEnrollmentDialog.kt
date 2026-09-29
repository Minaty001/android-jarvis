package com.jarvis.ui.dialogs

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.jarvis.ui.components.Ui
import com.jarvis.voice.VoiceEngine
import com.jarvis.wakeword.UserVoiceProfile
import com.jarvis.wakeword.VoiceEnrollmentCalibrator
import com.jarvis.wakeword.VoiceSampleRecorder
import com.jarvis.wakeword.WakeWordDatasetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Collects real positive WAVs; all neural training happens offline. */
class VoiceEnrollmentDialog(
    private val context: Context,
    private val voiceEngine: VoiceEngine,
    private val onDatasetUpdated: (UserVoiceProfile) -> Unit
) {
    private val store = WakeWordDatasetStore(context)
    private val recorder = VoiceSampleRecorder()
    private val calibrator = VoiceEnrollmentCalibrator()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var recordJob: Job? = null
    private var dialog: AlertDialog? = null
    private var targetCount = WakeWordDatasetStore.TARGET_SAMPLES

    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 18), Ui.dp(context, 14), Ui.dp(context, 18), Ui.dp(context, 10))
        }
        val title = TextView(context).apply {
            text = "PERSONAL WAKE WORD DATASET"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, Ui.dp(context, 8))
        }
        val explanation = TextView(context).apply {
            text = "Record real 16 kHz WAV samples saying only ‘Jarvis’. Recording adds data; it does not train model weights. Export the dataset and run offline training with positive, unknown, and background audio."
            textSize = 12f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, Ui.dp(context, 12))
        }
        val progressText = TextView(context).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Ui.TEXT)
            setPadding(0, 0, 0, Ui.dp(context, 4))
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = targetCount
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, 8)).apply { bottomMargin = Ui.dp(context, 10) }
            progressTintList = ColorStateList.valueOf(Ui.CYAN)
        }
        val modelInfo = TextView(context).apply {
            textSize = 11f
            setTextColor(Ui.WARNING)
            setPadding(0, 0, 0, Ui.dp(context, 10))
        }
        val prompt = TextView(context).apply {
            textSize = 12f
            setTextColor(Ui.MUTED)
            setPadding(0, 0, 0, Ui.dp(context, 8))
        }
        val meter = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, 7)).apply { bottomMargin = Ui.dp(context, 10) }
            progressTintList = ColorStateList.valueOf(Ui.CYAN)
        }
        val status = TextView(context).apply {
            textSize = 12f
            setTextColor(Ui.TEXT)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8))
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 8)
        }
        val targetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
        }
        val recordButton = MaterialButton(context).apply {
            text = "RECORD SAMPLE"
            textSize = 11f
            setTextColor(Ui.BG)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.CYAN)
        }
        val testButton = MaterialButton(context).apply {
            text = "TEST LAST RECORDING"
            textSize = 10f
            setTextColor(Ui.CYAN)
            strokeColor = ColorStateList.valueOf(Ui.CYAN_DIM)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
        }
        val exportButton = MaterialButton(context).apply {
            text = "EXPORT WAKE WORD DATASET"
            textSize = 10f
            setTextColor(Ui.CYAN)
            strokeColor = ColorStateList.valueOf(Ui.CYAN_DIM)
            strokeWidth = Ui.dp(context, 1)
            cornerRadius = Ui.dp(context, 8)
            backgroundTintList = ColorStateList.valueOf(Ui.SURFACE_2)
        }

        root.addView(title)
        root.addView(explanation)
        root.addView(progressText)
        root.addView(progress)
        root.addView(modelInfo)
        root.addView(prompt)
        root.addView(meter)
        root.addView(status)
        root.addView(targetRow)
        root.addView(recordButton)
        root.addView(testButton)
        root.addView(exportButton)

        fun updateDatasetUi() {
            val count = store.getPositiveSampleCount()
            progress.max = targetCount
            progress.progress = count.coerceAtMost(targetCount)
            progressText.text = "Phrase: Jarvis    Samples: $count / $targetCount"
            val metadata = voiceEngine.wakeEngine.getDiagnostics(count, store.getPositiveDatasetSha256())
            modelInfo.text = buildString {
                append("Model: ${when {
                    metadata.modelStatus == com.jarvis.voice.WakeModelStatus.MODEL_OUTDATED -> "OUTDATED"
                    metadata.modelAvailable -> "ACTIVE"
                    else -> "MISSING"
                }}")
                append(" • Status: ${metadata.modelStatus.name.replace('_', ' ')}")
                append("\nModel version: ${metadata.modelVersion ?: "unavailable"}")
                append(" • SHA-256: ${metadata.modelSha256 ?: "unavailable"}")
                append("\nModel threshold: ${metadata.threshold?.let { String.format(Locale.US, "%.3f", it) } ?: "unavailable"}")
                append("\nDataset: ${if (count >= WakeWordDatasetStore.TARGET_SAMPLES) "READY" else "INCOMPLETE"}")
            }
            prompt.text = promptFor(count)
            recordButton.text = "RECORD SAMPLE ${count + 1}"
            testButton.isEnabled = count > 0
            exportButton.isEnabled = count > 0
        }

        listOf(50, 100, 200).forEach { count ->
            targetRow.addView(MaterialButton(context).apply {
                text = "$count"
                textSize = 10f
                minWidth = 0
                setPadding(Ui.dp(context, 7), 0, Ui.dp(context, 7), 0)
                setOnClickListener { targetCount = count; updateDatasetUi() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        targetRow.addView(MaterialButton(context).apply {
            text = "+50"
            textSize = 10f
            minWidth = 0
            setOnClickListener { targetCount += 50; updateDatasetUi() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        updateDatasetUi()

        recordButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION) }
                status.text = "Microphone permission is required to record the dataset."
                return@setOnClickListener
            }
            if (recordJob?.isActive == true) return@setOnClickListener
            recordButton.isEnabled = false
            meter.progress = 0
            status.text = "Recording sample ${store.getPositiveSampleCount() + 1}. ${promptFor(store.getPositiveSampleCount())}"
            voiceEngine.suspendContinuousAudio()
            recordJob = scope.launch {
                try {
                    val captured = recorder.recordSample(durationMs = RECORDING_DURATION_MS) { rms ->
                        meter.post { meter.progress = (rms * 100).toInt() }
                    }
                    if (captured == null || captured.isEmpty()) {
                        status.text = "Recording failed. Check microphone permission and retry."
                        return@launch
                    }
                    when (val quality = calibrator.validateAudioQuality(captured)) {
                        is VoiceEnrollmentCalibrator.ValidationResult.Rejected -> status.text = "Recording rejected: ${quality.message}"
                        is VoiceEnrollmentCalibrator.ValidationResult.Valid -> {
                            val pcm = calibrator.trimForDataset(captured)
                            val file = withContext(Dispatchers.IO) { store.savePositive(pcm) }
                            val modelScore = voiceEngine.scoreEnrollmentSample(pcm)
                            val count = store.getPositiveSampleCount()
                            val metadata = voiceEngine.wakeEngine.getModelMetadata()
                            val profile = UserVoiceProfile(
                                isEnrolled = count > 0,
                                enrolledAt = System.currentTimeMillis(),
                                wakeWordPhrase = "Jarvis",
                                sampleCount = count,
                                datasetVersion = WakeWordDatasetStore.DATASET_VERSION,
                                datasetSha256 = store.getPositiveDatasetSha256(),
                                modelVersion = metadata?.version?.toString() ?: "",
                                modelSha256 = metadata?.modelSha256 ?: ""
                            )
                            onDatasetUpdated(profile)
                            status.text = if (modelScore == null) {
                                "Recording quality: PASS • Saved: ${file.name}\nMODEL UNAVAILABLE — this recording was not evaluated by the wake-word model. Offline training is still required."
                            } else {
                                "Recording quality: PASS • Saved: ${file.name}\nModel score: ${String.format(Locale.US, "%.3f", modelScore.score)} • Threshold: ${String.format(Locale.US, "%.3f", modelScore.threshold)} • ${if (modelScore.passed) "PASS" else "BELOW THRESHOLD; retained for offline training"}"
                            }
                            updateDatasetUi()
                            pcm.fill(0)
                            captured.fill(0)
                        }
                    }
                } catch (duplicate: IllegalArgumentException) {
                    status.text = "Recording was not added: ${duplicate.message}"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status.text = "Could not save recording: ${error.message ?: error.javaClass.simpleName}"
                } finally {
                    recordButton.isEnabled = true
                    meter.progress = 0
                }
            }
        }

        testButton.setOnClickListener {
            testButton.isEnabled = false
            status.text = "Scoring the exact latest saved WAV through the active KWS model…"
            scope.launch {
                try {
                    val latest = withContext(Dispatchers.IO) { store.loadLatestPositive() }
                    if (latest == null) {
                        status.text = "No saved positive recording is available."
                    } else {
                        val result = voiceEngine.scoreEnrollmentSample(latest.second)
                        status.text = if (result == null) {
                            "MODEL UNAVAILABLE — the saved recording was not evaluated."
                        } else {
                            "Saved: ${latest.first.name}\nModel score: ${String.format(Locale.US, "%.3f", result.score)} • Threshold: ${String.format(Locale.US, "%.3f", result.threshold)} • ${if (result.passed) "PASS" else "FAIL"}"
                        }
                        latest.second.fill(0)
                    }
                } catch (error: Exception) {
                    status.text = "Sample test failed: ${error.message ?: error.javaClass.simpleName}"
                } finally {
                    testButton.isEnabled = true
                }
            }
        }

        exportButton.setOnClickListener {
            exportButton.isEnabled = false
            scope.launch {
                try {
                    val output = File(context.filesDir, "wake_word_exports/jarvis_dataset_${System.currentTimeMillis()}.zip")
                    val zip = withContext(Dispatchers.IO) { store.exportZip(output) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newUri(context.contentResolver, "Wake word dataset", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(send, "Export wake-word dataset"))
                    status.text = "Dataset ZIP exported (${store.getPositiveSampleCount()} positive WAVs plus manifest)."
                } catch (error: Exception) {
                    status.text = "Dataset export failed: ${error.message ?: error.javaClass.simpleName}"
                } finally {
                    exportButton.isEnabled = true
                }
            }
        }

        dialog = AlertDialog.Builder(context)
            .setView(ScrollView(context).apply { addView(root) })
            .setNegativeButton("CLOSE", null)
            .create()
            .apply {
                setOnDismissListener {
                    recorder.stop()
                    recordJob?.cancel()
                    scope.cancel()
                    voiceEngine.resumeContinuousAudioAndReArm()
                }
                show()
            }
    }

    private fun promptFor(sampleIndex: Int): String = when (sampleIndex % 5) {
        0 -> "Say ‘Jarvis’ naturally."
        1 -> "Say ‘Jarvis’ slightly louder."
        2 -> "Say ‘Jarvis’ from a little farther away."
        3 -> "Say ‘Jarvis’ at your normal volume."
        else -> "Say ‘Jarvis’ in your usual voice, with a short pause first."
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 4107
        private const val RECORDING_DURATION_MS = 2_000L
    }
}
