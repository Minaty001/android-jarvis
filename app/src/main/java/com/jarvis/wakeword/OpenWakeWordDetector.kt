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
    private data class Head(
        val phrase: String,
        val asset: String,
        val threshold: Float,
        val interpreter: Interpreter,
        val confirmation: KwsTemporalConfirmation
    )

    private val rawPcm = ShortArray(RAW_HISTORY_SAMPLES)
    private var rawPcmIndex = 0
    private var rawPcmFilled = false

    private val audioChunk = ShortArray(FRAME_SAMPLES)
    private var audioChunkIndex = 0

    // Pre-allocated circular ring buffers to eliminate GC churn in the streaming audio thread
    private val melFramePool = Array(MEL_HISTORY_FRAMES) { FloatArray(MEL_BINS) }
    private var melFrameHead = 0
    private var melFrameCount = 0

    private val embeddingFramePool = Array(FEATURE_HISTORY_FRAMES) { FloatArray(EMBEDDING_SIZE) }
    private var embeddingFrameHead = 0

    private var melInterpreter: Interpreter? = null
    private var embeddingInterpreter: Interpreter? = null
    private var heads: List<Head> = emptyList()
    private var initialized = false
    private var available = false
    private var error: String? = null
    private var latestScore = 0f
    private var detectedPhrase: String? = null
    private var realEmbeddingCount = 0
    private var latestEnergyGatePassed: Boolean? = null
    private var consecutiveSilenceFrames = 0
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
                    val confirmation = KwsTemporalConfirmation(
                        windowCount = config.confirmationWindowCount,
                        requiredPositiveWindows = config.requiredPositiveWindows
                    )
                    loadedHeads += Head(model.getString("phrase"), asset, threshold, interpreter, confirmation)
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
            melFramePool.forEach { it.fill(0f) }
            melFrameHead = 0
            melFrameCount = 0
            embeddingFramePool.forEach { it.fill(0f) }
            embeddingFrameHead = 0
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
        val threadCount = com.jarvis.device.HardwareProfileManager.getRecommendedTfliteThreads(context)
        return Interpreter(mapped, Interpreter.Options().apply {
            setNumThreads(threadCount)
            setUseNNAPI(false)
            setUseXNNPACK(true)
        })
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
    override fun getLatestEnergyGatePassed(): Boolean? = latestEnergyGatePassed
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
            rawPcm[rawPcmIndex] = sample
            rawPcmIndex++
            if (rawPcmIndex == RAW_HISTORY_SAMPLES) {
                rawPcmIndex = 0
                rawPcmFilled = true
            }

            audioChunk[audioChunkIndex++] = sample
            if (audioChunkIndex == FRAME_SAMPLES) {
                val rms = calculateRms(audioChunk)
                val energyPassed = rms >= config.minEnergyRms
                latestEnergyGatePassed = energyPassed
                audioChunkIndex = 0
                processedFrame = true
                if (!energyPassed) {
                    consecutiveSilenceFrames++
                    if (consecutiveSilenceFrames >= config.silenceFramesBeforeReset) {
                        heads.forEach { it.confirmation.reset() }
                        latestScore = 0f
                        detectedPhrase = null
                    }
                    // When room silence has saturated the history buffer (>= 16 frames = 1.28s),
                    // skip redundant TFLite forward passes on pure silence to preserve CPU & battery
                    if (consecutiveSilenceFrames <= FEATURE_WINDOW) {
                        processChunk()
                    }
                } else {
                    consecutiveSilenceFrames = 0
                    processChunk()
                }
            }
        }
        if (!processedFrame || latestEnergyGatePassed == false) return 0f
        if (realEmbeddingCount < FEATURE_WINDOW) return 0f
        val featureInput = flattenLastFeatures() ?: return 0f
        var confirmedScore = 0f
        var confirmedPhrase: String? = null
        var maxRatio = 0f

        for (head in heads) {
            val score = runHead(head.interpreter, featureInput)
            val ratio = score / head.threshold
            if (ratio > maxRatio) {
                maxRatio = ratio
            }
            if (ratio >= 0.5f) {
                Log.d(TAG, "openWakeWord candidate: ${head.phrase} score=$score ratio=$ratio (thr=${head.threshold})")
            }
            val confirmed = head.confirmation.add(ratio, 1f)
            if (confirmed >= 1f) {
                // If multiple heads confirm simultaneously, prioritize multi-word phrase ("Hey Jarvis" > "Jarvis")
                val isBetter = confirmedPhrase == null ||
                    (head.phrase == "Hey Jarvis" && confirmedPhrase == "Jarvis") ||
                    (confirmed > confirmedScore && confirmedPhrase != "Hey Jarvis")
                if (isBetter) {
                    confirmedScore = confirmed
                    confirmedPhrase = head.phrase
                }
            }
        }
        latestScore = maxRatio
        detectedPhrase = confirmedPhrase
        if (detectedPhrase != null) {
            Log.i(TAG, "openWakeWord CONFIRMED: phrase='$detectedPhrase' score=$confirmedScore")
            heads.forEach { it.confirmation.reset() }
            return confirmedScore
        }
        return 0f
    }

    private fun processChunk() {
        val mel = melInterpreter ?: return
        val embedding = embeddingInterpreter ?: return
        val input = melInputBuffer ?: return
        input.clear()
        if (rawPcmFilled) {
            for (i in rawPcmIndex until RAW_HISTORY_SAMPLES) {
                input.put(rawPcm[i].toFloat())
            }
            for (i in 0 until rawPcmIndex) {
                input.put(rawPcm[i].toFloat())
            }
        } else {
            val pad = RAW_HISTORY_SAMPLES - rawPcmIndex
            for (i in 0 until pad) input.put(0f)
            for (i in 0 until rawPcmIndex) input.put(rawPcm[i].toFloat())
        }
        if (input.position() != RAW_HISTORY_SAMPLES) return
        input.rewind()
        val melTensor = mel.getOutputTensor(0)
        val melOutput = melOutputBuffer ?: return
        melOutput.clear()
        mel.run(input, melOutput)
        melOutput.rewind()
        val shape = melTensor.shape()
        val frameCount = shape[shape.size - 2]

        // Fast zero-allocation insertion into circular mel pool
        for (row in 0 until frameCount) {
            val target = melFramePool[melFrameHead]
            for (column in 0 until MEL_BINS) {
                target[column] = melOutput.float / 10f + 2f
            }
            melFrameHead = (melFrameHead + 1) % MEL_HISTORY_FRAMES
            melFrameCount = minOf(MEL_HISTORY_FRAMES, melFrameCount + 1)
        }

        if (melFrameCount < MEL_WINDOW) return
        val embeddingInput = embeddingInputBuffer ?: return
        embeddingInput.clear()

        // Vectorized bulk copy of 76 mel frames into direct embedding buffer (zero allocations)
        for (i in 0 until MEL_WINDOW) {
            val idx = (melFrameHead - MEL_WINDOW + i + MEL_HISTORY_FRAMES) % MEL_HISTORY_FRAMES
            embeddingInput.put(melFramePool[idx])
        }
        embeddingInput.rewind()
        val embedded = embeddingOutputBuffer ?: return
        embedded.clear()
        embedding.run(embeddingInput, embedded)
        embedded.rewind()

        // Fast zero-allocation insertion into circular feature embedding pool
        val targetFrame = embeddingFramePool[embeddingFrameHead]
        for (i in 0 until EMBEDDING_SIZE) {
            targetFrame[i] = embedded.float
        }
        embeddingFrameHead = (embeddingFrameHead + 1) % FEATURE_HISTORY_FRAMES
        realEmbeddingCount = minOf(FEATURE_HISTORY_FRAMES, realEmbeddingCount + 1)
    }

    private fun flattenLastFeatures(): FloatBuffer? {
        val buffer = headInputBuffer ?: return null
        buffer.clear()
        // Vectorized bulk copy of 16 embedding frames into direct head input buffer (zero allocations)
        for (i in 0 until FEATURE_WINDOW) {
            val idx = (embeddingFrameHead - FEATURE_WINDOW + i + FEATURE_HISTORY_FRAMES) % FEATURE_HISTORY_FRAMES
            buffer.put(embeddingFramePool[idx])
        }
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
        rawPcm.fill(0)
        rawPcmIndex = 0
        rawPcmFilled = false
        audioChunk.fill(0)
        audioChunkIndex = 0
        melFramePool.forEach { it.fill(0f) }
        melFrameHead = 0
        melFrameCount = 0
        embeddingFramePool.forEach { it.fill(0f) }
        embeddingFrameHead = 0
        latestScore = 0f; detectedPhrase = null; realEmbeddingCount = 0
        heads.forEach { it.confirmation.reset() }
        latestEnergyGatePassed = null
        consecutiveSilenceFrames = 0
    }

    private fun calculateRms(samples: ShortArray): Double {
        var sumSquares = 0L
        for (i in 0 until FRAME_SAMPLES) {
            val s = samples[i].toLong()
            sumSquares += s * s
        }
        return kotlin.math.sqrt(sumSquares.toDouble() / FRAME_SAMPLES) / 32768.0
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
