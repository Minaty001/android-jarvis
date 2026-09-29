package com.jarvis.wakeword

object WakeWordModelContract {
    val inputShape = intArrayOf(1, KwsFeatureSpec.DEFAULT.numFrames, KwsFeatureSpec.DEFAULT.numMfcc, 1)
    val outputShape = intArrayOf(1, 3)

    fun validate(inputShape: IntArray, inputType: String, outputShape: IntArray, outputType: String) {
        require(inputShape.contentEquals(this.inputShape) && inputType == "INT8") {
            "Expected input ${this.inputShape.contentToString()} INT8; actual ${inputShape.contentToString()} $inputType"
        }
        require(outputShape.contentEquals(this.outputShape) && outputType == "INT8") {
            "Expected output ${this.outputShape.contentToString()} INT8; actual ${outputShape.contentToString()} $outputType"
        }
    }
}
