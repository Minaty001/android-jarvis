package com.jarvis.wakeword

/** The KWS layer owns temporal confirmation; later wake gates must not count hits again. */
class KwsTemporalConfirmation(
    private val windowCount: Int = 3,
    private val requiredPositiveWindows: Int = 2
) {
    private val scores = ArrayDeque<Float>()

    init {
        require(windowCount in 1..10)
        require(requiredPositiveWindows in 1..windowCount)
    }

    @Synchronized
    fun add(score: Float, threshold: Float): Float {
        if (requiredPositiveWindows == 1 && score >= threshold) {
            scores.clear()
            return score
        }
        scores.addLast(score)
        while (scores.size > windowCount) scores.removeFirst()
        if (scores.size >= windowCount && scores.count { it >= threshold } >= requiredPositiveWindows) {
            scores.clear()
            return score
        }
        return 0f
    }

    @Synchronized
    fun reset() = scores.clear()
}
