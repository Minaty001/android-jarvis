package com.jarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Uncomment the import below once you have added the sherpa-onnx.aar to your libs folder
// import fun.k2fsa.sherpa.onnx.Tts
// import fun.k2fsa.sherpa.onnx.TtsConfig

class SherpaTtsEngine(
    private val context: Context,
    private val fallback: TtsEngine? = null
) : TtsEngine {
    private val TAG = "SherpaTtsEngine"
    
    private var tts: Any? = null
    private val isReady = AtomicBoolean(false)
    private var isBluetoothScoActive = false
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    @Volatile private var isSpeakingActive = false

    override fun start(ttsDir: File) {
        if (isReady.get()) return
        try {
            /* TODO: Uncomment this block once you add the AAR
            val config = fun.k2fsa.sherpa.onnx.TtsConfig().apply {
                model.vits.model = "hi_IN-pratham-medium.onnx"
                model.vits.lexicon = "lexicon.txt"
                model.vits.tokens = "tokens.txt"
                model.vits.dataDir = ""
                model.vits.noiseScale = 0.667f
                model.vits.noiseScaleW = 0.8f
                model.vits.lengthScale = 1.0f
                model.numThreads = 2
            }
            
            tts = fun.k2fsa.sherpa.onnx.Tts(context.assets, config)
            */
            // isReady.set(true)
            // Log.i(TAG, "Sherpa-ONNX TTS initialized successfully")
            
            // Temporary fallback because AAR is missing
            Log.e(TAG, "Sherpa-ONNX library missing. Using Native Android TTS fallback.")
            isReady.set(false)
            fallback?.start(ttsDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX TTS: \${e.message}")
            isReady.set(false)
            fallback?.start(ttsDir)
        } catch (e: Error) {
            Log.e(TAG, "Sherpa-ONNX library or model missing: \${e.message}")
            isReady.set(false)
            fallback?.start(ttsDir)
        }
    }

    override fun synthesize(text: String, onAudio: (ShortArray) -> Unit) {
        if (!isReady.get() || tts == null) {
            fallback?.synthesize(text, onAudio)
            return
        }
        try {
            /* TODO: Uncomment once AAR is added
            val sherpaTts = tts as fun.k2fsa.sherpa.onnx.Tts
            val audio = sherpaTts.generate(text)
            playAudioData(audio.samples, audio.sampleRate)
            onAudio(audio.samples.toShortArray()) // Conversion logic omitted for brevity
            */
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa synthesize failed: \${e.message}")
            fallback?.synthesize(text, onAudio)
        }
    }

    override suspend fun speak(text: String): Boolean {
        if (!isReady.get() || tts == null) {
            return fallback?.speak(text) ?: false
        }
        isSpeakingActive = true
        return try {
            /* TODO: Uncomment once AAR is added
            val sherpaTts = tts as fun.k2fsa.sherpa.onnx.Tts
            val audio = sherpaTts.generate(text)
            
            val samples = audio.samples
            playAudioData(samples, audio.sampleRate)
            */
            isSpeakingActive = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa speak failed: \${e.message}")
            isSpeakingActive = false
            fallback?.speak(text) ?: false
        }
    }

    private fun playAudioData(samples: FloatArray, sampleRate: Int) {
        val shortSamples = ShortArray(samples.size) { i ->
            (samples[i] * 32767).toInt().toShort()
        }
        
        val usage = if (isBluetoothScoActive) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(shortSamples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            
        audioTrack.write(shortSamples, 0, shortSamples.size)
        audioTrack.play()
        
        val durationMs = (samples.size.toFloat() / sampleRate * 1000).toLong()
        Thread.sleep(durationMs)
        
        audioTrack.stop()
        audioTrack.release()
    }

    override fun stop() {
        fallback?.stop()
    }

    override fun cancel() {
        fallback?.cancel()
    }

    override fun isSpeaking(): Boolean {
        return isSpeakingActive || (fallback?.isSpeaking() == true)
    }

    override fun updateBluetoothScoState(scoActive: Boolean) {
        isBluetoothScoActive = scoActive
        fallback?.updateBluetoothScoState(scoActive)
    }
}
