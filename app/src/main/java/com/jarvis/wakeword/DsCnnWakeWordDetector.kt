package com.jarvis.wakeword

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.sqrt

/** INT8 KWS inference. The checked manifest supplies the only production score threshold. */
class DsCnnWakeWordDetector(
    private val context: Context,
    val modelAssetPath: String = WakeWordModelMetadata.MODEL_NAME,
    val config: WakeWordConfig = WakeWordConfig()
) : WakeWordDetector {
    private val spec = KwsFeatureSpec.DEFAULT
    private val mfccExtractor = MfccExtractor()
    private var interpreter: Interpreter? = null
    private var metadata: WakeWordModelMetadata? = null
    private var modelAvailable = false
    private var modelLoadError: String? = null
    private var initialized = false
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var inputScale = 0f
    private var inputZeroPoint = 0
    private var outputScale = 0f
    private var outputZeroPoint = 0
    private var inputShape = "unavailable"
    private var outputShape = "unavailable"
    private val rollingAudio = ShortArray(spec.windowSamples)
    private var writePosition = 0
    private var bufferFull = false
    private var samplesSinceInference = 0
    private val confirmation = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)
    private val features = Array(spec.numFrames) { FloatArray(spec.numMfcc) }
    private val quantized = ByteArray(spec.numFrames * spec.numMfcc)
    private var noiseFloorRms = 0.003
    private var lastEnergyLogAt = 0L
    @Volatile private var latestRawScore = 0f
    @Volatile private var latestEnergyGatePassed: Boolean? = null

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        initialized = true
        try {
            val parsedMetadata = context.assets.open(WakeWordModelMetadata.ASSET_NAME).bufferedReader().use { it.readText() }
                .let(WakeWordModelMetadata::fromJson)
            require(parsedMetadata.modelFile == modelAssetPath) {
                "Model manifest names '${parsedMetadata.modelFile}', but runtime requested '$modelAssetPath'"
            }
            val actualSha = sha256Asset(modelAssetPath)
            require(actualSha.equals(parsedMetadata.modelSha256, ignoreCase = true)) {
                "KWS model SHA-256 mismatch: manifest=${parsedMetadata.modelSha256}, actual=$actualSha"
            }

            val descriptor = context.assets.openFd(modelAssetPath)
            val mapped = FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
            descriptor.close()

            val model = Interpreter(mapped, Interpreter.Options().apply { setNumThreads(2); setUseNNAPI(false) })
            try {
                validateAndConfigure(model)
                interpreter = model
            } catch (error: Throwable) {
                model.close()
                throw error
            }
            metadata = parsedMetadata
            modelAvailable = true
            modelLoadError = null
            Log.i(TAG, "KWS model loaded version=${parsedMetadata.version} sha256=$actualSha input=$inputShape output=$outputShape threshold=${parsedMetadata.threshold}")
        } catch (error: Throwable) {
            interpreter?.close()
            interpreter = null
            metadata = null
            modelAvailable = false
            val detail = error.message ?: error.javaClass.simpleName
            modelLoadError = if (error is FileNotFoundException) {
                "MODEL MISSING — TRAIN MODEL FIRST. Required KWS asset is unavailable: $detail"
            } else detail
            Log.e(TAG, "MODEL MISSING/INVALID — train and install a validated KWS model: $modelLoadError")
        }
    }

    private fun validateAndConfigure(model: Interpreter) {
        require(model.inputTensorCount == 1 && model.outputTensorCount == 1) {
            "Expected one input and one output tensor; actual ${model.inputTensorCount} inputs, ${model.outputTensorCount} outputs"
        }
        val input = model.getInputTensor(0)
        val output = model.getOutputTensor(0)
        inputShape = input.shape().contentToString()
        outputShape = output.shape().contentToString()
        WakeWordModelContract.validate(input.shape(), input.dataType().name, output.shape(), output.dataType().name)

        val inputQuantization = input.quantizationParams()
        val outputQuantization = output.quantizationParams()
        require(inputQuantization.scale.isFinite() && inputQuantization.scale > 0f) { "KWS input tensor has invalid INT8 quantization scale" }
        require(outputQuantization.scale.isFinite() && outputQuantization.scale > 0f) { "KWS output tensor has invalid INT8 quantization scale" }
        inputScale = inputQuantization.scale
        inputZeroPoint = inputQuantization.zeroPoint
        outputScale = outputQuantization.scale
        outputZeroPoint = outputQuantization.zeroPoint
        inputBuffer = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
    }

    override fun isModelAvailable(): Boolean {
        ensureInitialized()
        return modelAvailable
    }

    override fun getModelLoadError(): String? {
        ensureInitialized()
        return modelLoadError
    }

    override fun getModelThreshold(): Float? {
        ensureInitialized()
        return metadata?.threshold
    }

    override fun getModelMetadata(): WakeWordModelMetadata? {
        ensureInitialized()
        return metadata
    }

    override fun getInputShape(): String? = inputShape
    override fun getOutputShape(): String? = outputShape
    override fun getLatestRawScore(): Float? = latestRawScore
    override fun getLatestEnergyGatePassed(): Boolean? = latestEnergyGatePassed

    @Synchronized
    override fun pushAudio(samples: ShortArray): Float {
        ensureInitialized()
        if (!modelAvailable || samples.isEmpty()) return 0f
        for (sample in samples) {
            rollingAudio[writePosition] = sample
            writePosition = (writePosition + 1) % rollingAudio.size
            if (writePosition == 0) bufferFull = true
        }
        samplesSinceInference += samples.size
        if (!bufferFull || samplesSinceInference < config.inferenceIntervalSamples) return 0f
        samplesSinceInference = 0

        val window = windowSnapshot()
        latestEnergyGatePassed = hasSufficientEnergy(window)
        if (latestEnergyGatePassed != true) {
            latestRawScore = 0f
            confirmation.add(0f, metadata?.threshold ?: 1f)
            return 0f
        }

        val score = inferWindow(window) ?: return 0f
        val confirmed = confirmation.add(score, metadata?.threshold ?: 1f)
        return if (confirmed >= (metadata?.threshold ?: 1f)) confirmed else 0f
    }

    @Synchronized
    override fun scoreEnrollmentSample(pcm: ShortArray): Float? {
        ensureInitialized()
        if (!modelAvailable || pcm.isEmpty()) return null
        val window = ShortArray(spec.windowSamples)
        System.arraycopy(pcm, 0, window, 0, minOf(pcm.size, window.size))
        return inferWindow(window)
    }

    private fun inferWindow(window: ShortArray): Float? {
        val frameCount = mfccExtractor.extractMfcc(window, features)
        if (frameCount != spec.numFrames) {
            markInferenceUnavailable("Expected ${spec.numFrames} MFCC frames, extracted $frameCount")
            return null
        }
        val model = interpreter ?: return null
        val input = inputBuffer ?: return null
        val output = outputBuffer ?: return null
        return try {
            input.clear()
            val count = mfccExtractor.quantizeToInt8(features, frameCount, quantized, inputScale, inputZeroPoint)
            input.put(quantized, 0, count)
            input.rewind()
            output.clear()
            model.run(input, output)
            output.rewind()
            val values = FloatArray(3) { (output.get().toInt() - outputZeroPoint) * outputScale }
            val probabilities = if (values.all { it in 0f..1.05f } && values.sum() in 0.90f..1.10f) values else softmax(values)
            val score = probabilities[metadata?.positiveClassIndex ?: return null]
            if (!score.isFinite()) {
                markInferenceUnavailable("KWS model returned a non-finite score")
                null
            } else {
                latestRawScore = score
                score
            }
        } catch (error: Throwable) {
            markInferenceUnavailable("KWS inference failed: ${error.message ?: error.javaClass.simpleName}", error)
            null
        }
    }

    private fun windowSnapshot(): ShortArray {
        val window = ShortArray(spec.windowSamples)
        val firstLength = spec.windowSamples - writePosition
        System.arraycopy(rollingAudio, writePosition, window, 0, firstLength)
        System.arraycopy(rollingAudio, 0, window, firstLength, writePosition)
        return window
    }

    private fun hasSufficientEnergy(frame: ShortArray): Boolean {
        if (frame.isEmpty()) return false
        var sum = 0.0
        frame.forEach { val normalized = it / 32768.0; sum += normalized * normalized }
        val rms = sqrt(sum / frame.size)
        val now = System.currentTimeMillis()
        if (now - lastEnergyLogAt >= 4000L) {
            lastEnergyLogAt = now
            Log.d(TAG, "KWS energy gate rms=${"%.4f".format(rms)} floor=${"%.4f".format(noiseFloorRms)}")
        }
        val required = maxOf(config.minEnergyRms, noiseFloorRms * 1.15)
        if (rms < required) {
            noiseFloorRms = noiseFloorRms * 0.96 + rms * 0.04
            return false
        }
        noiseFloorRms = noiseFloorRms * 0.995 + rms * 0.005
        return true
    }

    @Synchronized
    override fun resetDetectorState() {
        rollingAudio.fill(0)
        writePosition = 0
        bufferFull = false
        samplesSinceInference = 0
        confirmation.reset()
        latestRawScore = 0f
        latestEnergyGatePassed = null
    }

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
        metadata = null
        modelAvailable = false
        initialized = false
        inputBuffer = null
        outputBuffer = null
    }

    private fun markInferenceUnavailable(message: String, error: Throwable? = null) {
        modelAvailable = false
        modelLoadError = message
        interpreter?.close()
        interpreter = null
        Log.e(TAG, message, error)
    }

    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: 0f
        val expValues = FloatArray(values.size) { exp((values[it] - max).toDouble()).toFloat() }
        val sum = expValues.sum()
        return if (sum > 0f) FloatArray(values.size) { expValues[it] / sum } else FloatArray(values.size)
    }

    private fun sha256Asset(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object { private const val TAG = "DsCnnWakeWordDetector" }
}
