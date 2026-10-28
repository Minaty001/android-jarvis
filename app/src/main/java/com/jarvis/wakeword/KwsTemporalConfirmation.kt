package com.jarvis.wakeword

/** The KWS layer owns temporal confirmation; requires multiple consecutive/windowed hits. Zero allocations on steady-state. */
class KwsTemporalConfirmation(
    private val windowCount: Int = 4,
    private val requiredPositiveWindows: Int = 2
) {
    private val scores = FloatArray(windowCount)
    private var writeHead = 0
    private var currentSize = 0

    init {
        require(windowCount in 1..10)
        require(requiredPositiveWindows in 1..windowCount)
    }

    @Synchronized
    fun add(score: Float, threshold: Float): Float {
        if (!score.isFinite() || !threshold.isFinite()) return 0f

        scores[writeHead] = score
        writeHead = (writeHead + 1) % windowCount
        if (currentSize < windowCount) currentSize++

        var positiveCount = 0
        var maxPositiveScore = -Float.MAX_VALUE

        for (i in 0 until currentSize) {
            val s = scores[i]
            if (s >= threshold) {
                positiveCount++
                if (s > maxPositiveScore) {
                    maxPositiveScore = s
                }
            }
        }

        if (positiveCount >= requiredPositiveWindows) {
            val confirmedScore = if (maxPositiveScore > -Float.MAX_VALUE) maxPositiveScore else score
            reset()
            return confirmedScore
        }
        return 0f
    }

    @Synchronized
    fun reset() {
        writeHead = 0
        currentSize = 0
    }
}
