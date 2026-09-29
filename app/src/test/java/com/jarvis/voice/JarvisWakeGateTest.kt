package com.jarvis.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisWakeGateTest {
    private val audio = ShortArray(16_000)

    @Test
    fun scoreBelowModelThresholdIsRejected() {
        val gate = JarvisWakeGate({ false }, { false }, clockMs = { 10_000L })
        assertFalse(gate.processWakeScore(0.69f, audio, 0.70f))
    }

    @Test
    fun oneValidKwsEventActivatesWithoutAnotherHit() {
        val gate = JarvisWakeGate({ false }, { false }, clockMs = { 10_000L })
        assertTrue(gate.processWakeScore(0.71f, audio, 0.70f))
    }

    @Test
    fun cooldownBlocksDuplicateActivation() {
        var now = 10_000L
        val gate = JarvisWakeGate({ false }, { false }, cooldownMs = 1_200L, clockMs = { now })
        assertTrue(gate.processWakeScore(0.9f, audio, 0.7f))
        now += 500L
        assertFalse(gate.processWakeScore(0.9f, audio, 0.7f))
        now += 800L
        assertTrue(gate.processWakeScore(0.9f, audio, 0.7f))
    }

    @Test
    fun optionalOwnerVerificationDoesNotBlockKws() {
        val gate = JarvisWakeGate({ false }, { false }, clockMs = { 10_000L })
        assertTrue(gate.processWakeScore(0.9f, audio, 0.7f))
    }

    @Test
    fun requiredOwnerVerificationFailsClosedWhenModelUnavailable() {
        val gate = JarvisWakeGate(
            ownerVerificationAvailable = { false },
            verifyOwner = { true },
            requireOwnerVerification = true,
            clockMs = { 10_000L }
        )
        assertFalse(gate.processWakeScore(0.9f, audio, 0.7f))
    }

    @Test
    fun requiredOwnerVerificationFailsClosedWhenVerifierThrows() {
        val gate = JarvisWakeGate(
            ownerVerificationAvailable = { true },
            verifyOwner = { error("speaker model failed") },
            requireOwnerVerification = true,
            clockMs = { 10_000L }
        )
        assertFalse(gate.processWakeScore(0.9f, audio, 0.7f))
    }

    @Test
    fun ownerVerificationAllowsWakeWhenExplicitlyRequiredAndAvailable() {
        val gate = JarvisWakeGate(
            ownerVerificationAvailable = { true },
            verifyOwner = { it.size == 16_000 },
            requireOwnerVerification = true,
            clockMs = { 10_000L }
        )
        assertTrue(gate.processWakeScore(0.9f, audio, 0.7f))
    }
}
