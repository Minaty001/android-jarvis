package com.jarvis.voice.emotion

import kotlin.math.sqrt

enum class EmotionState {
    NEUTRAL,
    EXCITED,
    FRUSTRATED,
    QUIET
}

class AcousticEmotionClassifier {
    private var totalRms = 0f
    private var totalZcr = 0f
    private var frameCount = 0

    fun processFrame(samples: ShortArray) {
        if (samples.isEmpty()) return
        
        var sumSquares = 0.0
        var zeroCrossings = 0
        
        for (i in samples.indices) {
            val s = samples[i].toDouble()
            sumSquares += s * s
            
            if (i > 0) {
                if ((samples[i] >= 0 && samples[i-1] < 0) || (samples[i] < 0 && samples[i-1] >= 0)) {
                    zeroCrossings++
                }
            }
        }
        
        val rms = sqrt(sumSquares / samples.size).toFloat()
        val zcr = zeroCrossings.toFloat() / samples.size
        
        totalRms += rms
        totalZcr += zcr
        frameCount++
    }

    fun reset() {
        totalRms = 0f
        totalZcr = 0f
        frameCount = 0
    }

    fun classifyEmotion(): EmotionState {
        if (frameCount == 0) return EmotionState.NEUTRAL
        
        val avgRms = totalRms / frameCount
        val avgZcr = totalZcr / frameCount
        
        return when {
            avgRms < 150f -> EmotionState.QUIET
            avgRms > 600f && avgZcr > 0.12f -> EmotionState.EXCITED
            avgRms > 600f && avgZcr <= 0.12f -> EmotionState.FRUSTRATED
            else -> EmotionState.NEUTRAL
        }
    }
}
