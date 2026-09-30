package com.jarvis.wakeword

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.ArrayDeque

/** Native streaming openWakeWord frontend shared by the Jarvis and Hey Jarvis classifiers. */
class OpenWakeWordDetector(
    private val context: Context,
    private val config: WakeWordConfig = WakeWordConfig()
) : WakeWordDetector {
    private data class Head(val phrase: String, val asset: String, val threshold: Float, val interpreter: Interpreter)

    private val rawPcm = ArrayDeque<Short>(RAW_HISTORY_SAMPLES)
    private val audioChunk = ArrayList<Short>(FRAME_SAMPLES)
    private val melFrames = ArrayDeque<FloatArray>(MEL_HISTORY_FRAMES)
    private val embeddingFrames = ArrayDeque<FloatArray>(FEATURE_HISTORY_FRAMES)
    private var melInterpreter: Interpreter? = null
    private var embeddingInterpreter: Interpreter? = null
    private var heads: List<Head> = emptyList()
    private var initialized = false
    private var available = false
    private var error: String? = null
    private var latestScore = 0f
    private var detectedPhrase: String? = null
    private var realEmbeddingCount = 0
    private val confirmation = KwsTemporalConfirmation(config.confirmationWindowCount, config.requiredPositiveWindows)
    private var melInputBuffer: FloatBuffer? = null
    private var melOutputBuffer: ByteBuffer? = null
    private var embeddingInputBuffer: FloatBuffer? = null
    private var embeddingOutputBuffer: ByteBuffer? = null
    private var headInputBuffer: FloatBuffer? = null
    private var headOutputBuffer: ByteBuffer? = null

    @Synchronized
    private fun initialize() {
        if (initialized) return
        initialized = true
        val loadedHeads = ArrayList<Head>()
        try {
            val manifest = JSONObject(context.assets.open(MANIFEST).bufferedReader().use { it.readText() })
            val featureModels = manifest.getJSONObject("featureModels")
            listOf("melspectrogram" to MELSPECTROGRAM, "embedding" to EMBEDDING).forEach { (name, expectedAsset) ->
                val item = featureModels.getJSONObject(name)
                require(item.getString("asset") == expectedAsset) { "Unexpected openWakeWord $name asset" }
                require(sha256Asset(expectedAsset).equals(item.getString("sha256"), ignoreCase = true)) {
                    "openWakeWord feature model SHA-256 mismatch for $expectedAsset"
                }
            }
            val models = manifest.getJSONArray("models")
            require(models.length() in 1..2) { "Expected at least one supported wake-word head" }
            for (index in 0 until models.length()) {
                val model = models.getJSONObject(index)
                val asset = model.getString("asset")
                val threshold = model.getDouble("threshold").toFloat()
                require(threshold.isFinite() && threshold > 0f && threshold <= 1f) { "Invalid openWakeWord threshold for $asset" }
                require(sha256Asset(asset).equals(model.getString("sha256"), ignoreCase = true)) {
                    "openWakeWord model SHA-256 mismatch for $asset"
                }
                val interpreter = loadInterpreter(asset)
                try {
                    validateHead(interpreter)
                    loadedHeads += Head(model.getString("phrase"), asset, threshold, interpreter)
                } catch (failure: Throwable) {
                    interpreter.close()
                    throw failure
                }
            }
            val phrases = loadedHeads.map { it.phrase }
            require(phrases.toSet().size == phrases.size && phrases.all { it == "Jarvis" || it == "Hey Jarvis" }) {
                "Manifest contains an unsupported or duplicate wake-word phrase"
            }
            melInterpreter = loadInterpreter(MELSPECTROGRAM)
            embeddingInterpreter = loadInterpreter(EMBEDDING)
            validateFrontend(melInterpreter!!, embeddingInterpreter!!)
            melInputBuffer = floatBufferElements(RAW_HISTORY_SAMPLES)
            melOutputBuffer = byteBuffer(melInterpreter!!.getOutputTensor(0).numBytes())
            embeddingInputBuffer = floatBufferElements(MEL_WINDOW * MEL_BINS)
            embeddingOutputBuffer = byteBuffer(embeddingInterpreter!!.getOutputTensor(0).numBytes())
            headInputBuffer = floatBufferElements(FEATURE_WINDOW * EMBEDDING_SIZE)
            headOutputBuffer = byteBuffer(loadedHeads.first().interpreter.getOutputTensor(0).numBytes())
            repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS) { 1f }) }
            repeat(FEATURE_HISTORY_FRAMES) { embeddingFrames.addLast(FloatArray(EMBEDDING_SIZE)) }
            heads = loadedHeads
            available = true
            Log.i(TAG, "openWakeWord loaded heads=${heads.map { it.phrase }}")
        } catch (failure: Throwable) {
            loadedHeads.forEach { runCatching { it.interpreter.close() } }
            closeInterpreters()
            available = false
            error = failure.message ?: failure.javaClass.simpleName
            Log.e(TAG, "openWakeWord unavailable: $error", failure)
        }
    }

    private fun loadInterpreter(asset: String): Interpreter {
        val descriptor = context.assets.openFd(asset)
        val mapped = FileInputStream(descriptor.fileDescriptor).use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
        descriptor.close()
        return Interpreter(mapped, Interpreter.Options().apply { setNumThreads(2); setUseNNAPI(false) })
    }

    private fun validateHead(model: Interpreter) {
        require(model.inputTensorCount == 1 && model.outputTensorCount == 1)
        val input = model.getInputTensor(0)
        val output = model.getOutputTensor(0)
        require(input.shape().contentEquals(intArrayOf(1, FEATURE_WINDOW, EMBEDDING_SIZE)) && input.dataType() == DataType.FLOAT32) {
            "Expected openWakeWord head input [1,16,96] FLOAT32, got ${input.shape().contentToString()} ${input.dataType()}"
        }
        require(output.shape().contentEquals(intArrayOf(1, 1)) && output.dataType() == DataType.FLOAT32) {
            "Expected openWakeWord head output [1,1] FLOAT32, got ${output.shape().contentToString()} ${output.dataType()}"
        }
    }

    private fun validateFrontend(melspec: Interpreter, embedding: Interpreter) {
        require(melspec.inputTensorCount == 1 && melspec.outputTensorCount == 1)
        val melInput = melspec.getInputTensor(0)
        require(melInput.dataType() == DataType.FLOAT32 && melInput.shape().contentEquals(intArrayOf(1, RAW_HISTORY_SAMPLES))) {
            "Expected fixed openWakeWord frontend input [1,$RAW_HISTORY_SAMPLES] FLOAT32, got ${melInput.shape().contentToString()}"
        }
        require(melspec.getOutputTensor(0).dataType() == DataType.FLOAT32 &&
            melspec.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 1, MEL_FRAME_COUNT, MEL_BINS))) {
            "Unexpected openWakeWord melspectrogram output ${melspec.getOutputTensor(0).shape().contentToString()}"
        }
        require(embedding.getInputTensor(0).shape().contentEquals(intArrayOf(1, MEL_WINDOW, MEL_BINS, 1)))
        require(embedding.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 1, 1, EMBEDDING_SIZE)))
        require(embedding.getInputTensor(0).dataType() == DataType.FLOAT32 && embedding.getOutputTensor(0).dataType() == DataType.FLOAT32)
    }

    override fun isModelAvailable(): Boolean { initialize(); return available }
    override fun getModelLoadError(): String? { initialize(); return error }
    override fun getModelThreshold(): Float? { initialize(); return if (available) 1f else null }
    override fun getInputShape(): String? = "[1,16,96] FLOAT32 (per classifier)"
    override fun getOutputShape(): String? = "[1,1] FLOAT32 (per classifier)"
    override fun getLatestRawScore(): Float? = latestScore
    override fun getModelMetadata(): WakeWordModelMetadata? = null
    override fun getDetectedPhrase(): String? = detectedPhrase
    @Synchronized override fun getSupportedPhrases(): List<String> {
        initialize()
        return if (available) heads.map { it.phrase } else emptyList()
    }

    @Synchronized
    override fun scoreEnrollmentSample(pcm: ShortArray): Float? {
        initialize()
        if (!available || pcm.isEmpty()) return null
        resetDetectorState()
        pushAudio(pcm)
        val score = latestScore.takeIf { it.isFinite() }
        resetDetectorState()
        return score
    }

    @Synchronized
    override fun pushAudio(samples: ShortArray): Float {
        initialize()
        if (!available || samples.isEmpty()) return 0f
        var processedFrame = false
        for (sample in samples) {
            if (rawPcm.size == RAW_HISTORY_SAMPLES) rawPcm.removeFirst()
            rawPcm.addLast(sample)
            audioChunk.add(sample)
            if (audioChunk.size == FRAME_SAMPLES) {
                processChunk()
                audioChunk.clear()
                processedFrame = true
            }
        }
        if (!processedFrame) return 0f
        if (realEmbeddingCount < FEATURE_WINDOW) return 0f
        val featureInput = flattenLastFeatures() ?: return 0f
        var bestRatio = 0f
        var bestPhrase: String? = null
        for (head in heads) {
            val score = runHead(head.interpreter, featureInput)
            val ratio = score / head.threshold
            if (ratio > bestRatio) { bestRatio = ratio; bestPhrase = head.phrase }
        }
        latestScore = bestRatio
        val confirmed = confirmation.add(bestRatio, 1f)
        detectedPhrase = if (confirmed >= 1f) bestPhrase else null
        return if (detectedPhrase != null) confirmed else 0f
    }

    private fun processChunk() {
        val mel = melInterpreter ?: return
        val embedding = embeddingInterpreter ?: return
        val input = melInputBuffer ?: return
        input.clear()
        val skip = (rawPcm.size - RAW_HISTORY_SAMPLES).coerceAtLeast(0)
        rawPcm.forEachIndexed { index, sample -> if (index >= skip) input.put(sample.toFloat()) }
        if (input.position() != RAW_HISTORY_SAMPLES) return
        input.rewind()
        val melTensor = mel.getOutputTensor(0)
        val melOutput = melOutputBuffer ?: return
        melOutput.clear()
        mel.run(input, melOutput)
        melOutput.rewind()
        val shape = melTensor.shape()
        val frameCount = shape[shape.size - 2]
        val melRows = Array(frameCount) { FloatArray(MEL_BINS) }
        for (row in 0 until frameCount) for (column in 0 until MEL_BINS) {
            melRows[row][column] = melOutput.float / 10f + 2f
        }
        melRows.forEach {
            if (melFrames.size == MEL_HISTORY_FRAMES) melFrames.removeFirst()
            melFrames.addLast(it)
        }
        if (melFrames.size < MEL_WINDOW) return
        val embeddingInput = embeddingInputBuffer ?: return
        embeddingInput.clear()
        val melSkip = melFrames.size - MEL_WINDOW
        melFrames.forEachIndexed { index, row -> if (index >= melSkip) row.forEach(embeddingInput::put) }
        embeddingInput.rewind()
        val embedded = embeddingOutputBuffer ?: return
        embedded.clear()
        embedding.run(embeddingInput, embedded)
        embedded.rewind()
        val frame = FloatArray(EMBEDDING_SIZE) { embedded.float }
        if (embeddingFrames.size == FEATURE_HISTORY_FRAMES) embeddingFrames.removeFirst()
        embeddingFrames.addLast(frame)
        realEmbeddingCount = minOf(FEATURE_HISTORY_FRAMES, realEmbeddingCount + 1)
    }

    private fun flattenLastFeatures(): FloatBuffer? {
        val buffer = headInputBuffer ?: return null
        buffer.clear()
        val skip = embeddingFrames.size - FEATURE_WINDOW
        embeddingFrames.forEachIndexed { index, frame -> if (index >= skip) frame.forEach(buffer::put) }
        buffer.rewind()
        return buffer
    }

    private fun runHead(model: Interpreter, features: FloatBuffer): Float {
        val output = headOutputBuffer ?: return 0f
        output.clear()
        features.rewind()
        model.run(features, output)
        output.rewind()
        return output.float.coerceIn(0f, 1f)
    }

    private fun floatBufferElements(elements: Int): FloatBuffer =
        byteBuffer(elements * Float.SIZE_BYTES).asFloatBuffer()

    private fun byteBuffer(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

    private fun sha256Asset(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun closeInterpreters() {
        heads.forEach { runCatching { it.interpreter.close() } }
        heads = emptyList()
        melInterpreter?.close(); melInterpreter = null
        embeddingInterpreter?.close(); embeddingInterpreter = null
        melInputBuffer = null; melOutputBuffer = null
        embeddingInputBuffer = null; embeddingOutputBuffer = null
        headInputBuffer = null; headOutputBuffer = null
    }

    @Synchronized override fun resetDetectorState() {
        rawPcm.clear(); audioChunk.clear(); melFrames.clear(); embeddingFrames.clear()
        if (available) {
            repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS) { 1f }) }
            repeat(FEATURE_HISTORY_FRAMES) { embeddingFrames.addLast(FloatArray(EMBEDDING_SIZE)) }
        }
        latestScore = 0f; detectedPhrase = null; realEmbeddingCount = 0; confirmation.reset()
    }

    @Synchronized override fun close() {
        closeInterpreters(); initialized = false; available = false; error = null; resetDetectorState()
    }

    companion object {
        const val MANIFEST = "openwakeword_models.json"
        const val MELSPECTROGRAM = "melspectrogram.tflite"
        const val EMBEDDING = "embedding_model.tflite"
        const val FRAME_SAMPLES = 1280
        private const val MEL_OVERLAP_SAMPLES = 480
        private const val RAW_HISTORY_SAMPLES = FRAME_SAMPLES + MEL_OVERLAP_SAMPLES
        private const val MEL_BINS = 32
        private const val MEL_FRAME_COUNT = 8
        private const val MEL_WINDOW = 76
        private const val MEL_HISTORY_FRAMES = MEL_WINDOW
        private const val EMBEDDING_SIZE = 96
        private const val FEATURE_WINDOW = 16
        private const val FEATURE_HISTORY_FRAMES = FEATURE_WINDOW
        private const val TAG = "OpenWakeWord"
    }
}
