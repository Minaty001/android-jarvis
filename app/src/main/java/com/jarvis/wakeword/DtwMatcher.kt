package com.jarvis.wakeword

import android.content.Context
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Audio Fingerprinting & DTW (Dynamic Time Warping) Comparison Engine.
 *
 * Extracts MFCC features from 1.5-second audio chunks and calculates DTW warping matrix
 * distances against saved templates from [WakeWordTemplateStore].
 */
class DtwMatcher(
    private val context: Context? = null,
    private val templateStore: WakeWordTemplateStore = WakeWordTemplateStore(context),
    var threshold: Float = DEFAULT_THRESHOLD
) {
    private val TAG = "DtwMatcher"
    private val mfccExtractor = MfccExtractor()

    data class DtwMatchResult(
        val isMatch: Boolean,
        val similarityScore: Float,
        val normalizedDistance: Float,
        val bestMatchedTemplateIndex: Int
    )

    /**
     * Calculates Euclidean distance between two MFCC feature vectors of length [numMfcc].
     */
    fun euclideanDistance(v1: FloatArray, v2: FloatArray): Float {
        var sum = 0.0f
        val len = minOf(v1.size, v2.size)
        for (i in 0 until len) {
            val diff = v1[i] - v2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Calculates 2D Dynamic Time Warping (DTW) distance between two MFCC feature matrices.
     */
    fun calculateDtwDistance(seq1: Array<FloatArray>, seq2: Array<FloatArray>): Float {
        val n = seq1.size
        val m = seq2.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0.0f

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = euclideanDistance(seq1[i - 1], seq2[j - 1])
                val minPrev = minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
                dtw[i][j] = cost + minPrev
            }
        }

        // Normalize distance by total path length (n + m)
        return dtw[n][m] / (n + m).toFloat()
    }

    /**
     * Converts a 16 kHz Mono PCM audio frame into a mean-normalized MFCC feature sequence matrix.
     */
    fun extractFeatures(pcm: ShortArray): Array<FloatArray> {
        if (pcm.isEmpty()) return emptyArray()
        val maxPossibleFrames = (pcm.size - mfccExtractor.frameLengthSamples) / mfccExtractor.frameStrideSamples + 1
        if (maxPossibleFrames <= 0) return emptyArray()

        val features = Array(maxPossibleFrames) { FloatArray(mfccExtractor.numMfcc) }
        val extractedFrames = mfccExtractor.extractMfcc(pcm, features)

        if (extractedFrames <= 0) return emptyArray()

        val result = Array(extractedFrames) { i -> features[i] }

        // Apply Cepstral Mean Normalization (CMVN) across frames to remove channel/ambient bias
        val numMfcc = mfccExtractor.numMfcc
        val meanVector = FloatArray(numMfcc)
        for (i in 0 until extractedFrames) {
            for (c in 0 until numMfcc) {
                meanVector[c] += result[i][c]
            }
        }
        for (c in 0 until numMfcc) {
            meanVector[c] /= extractedFrames.toFloat()
        }
        for (i in 0 until extractedFrames) {
            for (c in 0 until numMfcc) {
                result[i][c] -= meanVector[c]
            }
        }

        return result
    }

    /**
     * Evaluates a live audio chunk against all saved templates in [WakeWordTemplateStore].
     */
    fun evaluateAudioChunk(livePcm: ShortArray): DtwMatchResult {
        if (livePcm.isEmpty()) {
            return DtwMatchResult(isMatch = false, similarityScore = 0.0f, normalizedDistance = Float.MAX_VALUE, bestMatchedTemplateIndex = -1)
        }

        val liveFeatures = extractFeatures(livePcm)
        if (liveFeatures.isEmpty()) {
            return DtwMatchResult(isMatch = false, similarityScore = 0.0f, normalizedDistance = Float.MAX_VALUE, bestMatchedTemplateIndex = -1)
        }

        var minDistance = Float.MAX_VALUE
        var bestIndex = -1

        for (idx in 1..3) {
            val templatePcm = templateStore.loadTemplate(idx) ?: continue
            val templateFeatures = extractFeatures(templatePcm)
            if (templateFeatures.isEmpty()) continue

            val distance = calculateDtwDistance(liveFeatures, templateFeatures)
            if (distance < minDistance) {
                minDistance = distance
                bestIndex = idx
            }
        }

        if (bestIndex == -1 || minDistance == Float.MAX_VALUE) {
            return DtwMatchResult(isMatch = false, similarityScore = 0.0f, normalizedDistance = Float.MAX_VALUE, bestMatchedTemplateIndex = -1)
        }

        // Convert normalized DTW distance to similarity score range [0.0 .. 1.0]
        // Base scale factor: 25.0f (a distance of 0.0 gives 1.0 similarity; distance of 25.0 gives 0.0 similarity)
        val score = max(0.0f, 1.0f - (minDistance / SCALE_FACTOR)).coerceIn(0.0f, 1.0f)
        val isMatch = score >= threshold

        Log.d(TAG, "DTW Evaluation: distance=${String.format("%.2f", minDistance)}, score=${String.format("%.2f", score)}, threshold=${String.format("%.2f", threshold)}, isMatch=$isMatch, bestTemplate=$bestIndex")

        return DtwMatchResult(
            isMatch = isMatch,
            similarityScore = score,
            normalizedDistance = minDistance,
            bestMatchedTemplateIndex = bestIndex
        )
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.80f
        const val STRICT_THRESHOLD = 0.88f
        private const val SCALE_FACTOR = 25.0f
    }
}
