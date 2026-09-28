package com.jarvis.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.jarvis.voice.audio.AcousticFeedbackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Continuous Background Listening Engine for Custom Wake-Word Detection using DTW.
 *
 * Records audio continuously in 1.5-second overlapping chunks (24,000 samples @ 16 kHz)
 * with a 500ms sliding window step. Evaluates active speech chunks via [DtwMatcher].
 * Optimized for low battery consumption via VAD RMS energy gating.
 */
class DtwBackgroundListeningService(
    private val context: Context,
    private val dtwMatcher: DtwMatcher = DtwMatcher(context),
    private val sampleRate: Int = 16000
) {
    private val TAG = "DtwBackgroundService"
    private val isListening = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val feedbackEngine = runCatching { AcousticFeedbackEngine(context) }.getOrNull()
    private var listeningJob: Job? = null

    var onWakeWordDetectedListener: ((DtwMatcher.DtwMatchResult) -> Unit)? = null
    var onRmsLevelListener: ((Float) -> Unit)? = null

    fun isListening(): Boolean = isListening.get()

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening.getAndSet(true)) return

        Log.d(TAG, "Starting continuous background listening service (DTW matching threshold = ${dtwMatcher.threshold})...")
        listeningJob?.cancel()
        listeningJob = scope.launch {
            runListeningLoop()
        }
    }

    fun stopListening() {
        if (!isListening.getAndSet(false)) return
        Log.d(TAG, "Stopping continuous background listening service...")
        listeningJob?.cancel()
        listeningJob = null
    }

    private suspend fun runListeningLoop() = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, 4800)
        var audioRecord: AudioRecord? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                isListening.set(false)
                return@withContext
            }

            audioRecord.startRecording()

            // 1.5 seconds @ 16 kHz = 24,000 samples
            val chunkSize = (sampleRate * 1.5).toInt()
            // 500ms sliding step = 8,000 samples
            val stepSize = (sampleRate * 0.5).toInt()

            val rollingBuffer = ShortArray(chunkSize)
            var bufferPos = 0
            val readChunk = ShortArray(640)

            var samplesSinceLastEval = 0
            val evalIntervalSamples = (sampleRate * 0.4).toInt() // Evaluate every 400ms

            while (isActive && isListening.get()) {
                val read = audioRecord.read(readChunk, 0, readChunk.size)
                if (read > 0) {
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val sample = readChunk[i]
                        sumSq += sample.toDouble() * sample.toDouble()
                    }

                    val rms = sqrt(sumSq / read)
                    val normalizedRms = (rms / 32768.0 * 8.0).toFloat().coerceIn(0.0f, 1.0f)
                    onRmsLevelListener?.invoke(normalizedRms)

                    // Batch copy into rolling buffer (O(1) block copy instead of per-sample copy)
                    if (bufferPos + read <= chunkSize) {
                        System.arraycopy(readChunk, 0, rollingBuffer, bufferPos, read)
                        bufferPos += read
                    } else {
                        val spaceLeft = chunkSize - bufferPos
                        if (spaceLeft > 0) {
                            System.arraycopy(readChunk, 0, rollingBuffer, bufferPos, spaceLeft)
                        }
                        val shift = read
                        System.arraycopy(rollingBuffer, shift, rollingBuffer, 0, chunkSize - shift)
                        val overflow = read - spaceLeft
                        if (overflow > 0) {
                            System.arraycopy(readChunk, spaceLeft, rollingBuffer, chunkSize - overflow, overflow)
                        }
                        bufferPos = chunkSize
                    }

                    samplesSinceLastEval += read

                    // Evaluate DTW periodically every 400ms when buffer is full and VAD energy gate passes
                    if (bufferPos >= chunkSize && samplesSinceLastEval >= evalIntervalSamples) {
                        samplesSinceLastEval = 0
                        val overallRms = calculateRms(rollingBuffer)
                        val now = System.currentTimeMillis()
                        if (overallRms >= MIN_VAD_ENERGY_RMS && (now - lastTriggerTimeMs) >= COOLDOWN_MS) {
                            val result = dtwMatcher.evaluateAudioChunk(rollingBuffer)
                            if (result.isMatch) {
                                lastTriggerTimeMs = now
                                println("Wake Word Detected!")
                                Log.i(TAG, "=== WAKE WORD DETECTED! (Score: ${String.format("%.2f", result.similarityScore)}, Template: ${result.bestMatchedTemplateIndex}) ===")

                                feedbackEngine?.playSuccessTone()
                                onWakeWordDetectedListener?.invoke(result)

                                val intent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
                                    putExtra("similarityScore", result.similarityScore)
                                    putExtra("matchedTemplateIndex", result.bestMatchedTemplateIndex)
                                }
                                context.sendBroadcast(intent)
                            }
                        }
                    }
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord error code: $read")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in background listening loop", e)
        } finally {
            isListening.set(false)
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioRecord", e)
            }
        }
    }

    private var lastTriggerTimeMs: Long = 0L

    private fun calculateRms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sumSq = 0.0
        for (sample in pcm) {
            val v = sample.toDouble()
            sumSq += v * v
        }
        return sqrt(sumSq / pcm.size)
    }

    companion object {
        const val ACTION_WAKE_WORD_DETECTED = "com.jarvis.action.WAKE_WORD_DETECTED"
        private const val MIN_VAD_ENERGY_RMS = 350.0
        private const val COOLDOWN_MS = 1500L
    }
}
