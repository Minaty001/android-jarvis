package com.jarvis.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisWakeGateTest {
    @Test
    fun wakeWordPassesWhenOwnerVerificationIsUnavailable() {
        val gate = JarvisWakeGate(
            ownerVerificationAvailable = { false },
            verifyOwner = { false }
        )

        assertTrue(gate.processWakeScore(0.8f, shortArrayOf(100, 200)))
    }
}
