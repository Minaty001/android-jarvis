package com.jarvis.emotion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionalJsonContractTest {

    @Test
    fun testSerializationAndDeserializationOfThinkingSnapshot() {
        val snapshot = EmotionalSnapshot(
            schemaVersion = 1,
            sessionId = "session-test-123",
            version = 1,
            current = StateDetail(
                state = AssistantState.THINKING,
                previousState = AssistantState.FOCUSED,
                reason = "work_pending",
                confidence = 1.0f,
                priority = 80,
                enteredAtMs = 1000L,
                expiresAtMs = null,
                operationId = "op-456"
            ),
            context = ContextDetail(
                taskPhase = TaskPhase.WAITING_FOR_TOOL,
                sentiment = Sentiment.UNKNOWN,
                sentimentConfidence = 0.0f,
                source = SignalSource.TASK_MANAGER
            ),
            presentation = PresentationDetail(
                voiceProfile = "THINKING",
                uiProfile = "THINKING",
                humorAllowed = false,
                speechInterruptible = true
            )
        )

        val jsonString = EmotionalJsonContract.toJson(snapshot)
        assertNotNull(jsonString)

        val json = JSONObject(jsonString)
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("session-test-123", json.getString("sessionId"))
        assertEquals(1, json.getInt("version"))

        val currentObj = json.getJSONObject("current")
        assertEquals("THINKING", currentObj.getString("state"))
        assertEquals("FOCUSED", currentObj.getString("previousState"))
        assertEquals("work_pending", currentObj.getString("reason"))
        assertEquals(1.0, currentObj.getDouble("confidence"), 0.001)
        assertEquals(80, currentObj.getInt("priority"))
        assertEquals(1000L, currentObj.getLong("enteredAtMs"))
        assertTrue(currentObj.isNull("expiresAtMs"))
        assertEquals("op-456", currentObj.getString("operationId"))

        val contextObj = json.getJSONObject("context")
        assertEquals("WAITING_FOR_TOOL", contextObj.getString("taskPhase"))
        assertEquals("UNKNOWN", contextObj.getString("sentiment"))
        assertEquals(0.0, contextObj.getDouble("sentimentConfidence"), 0.001)
        assertEquals("TASK_MANAGER", contextObj.getString("source"))

        val presObj = json.getJSONObject("presentation")
        assertEquals("THINKING", presObj.getString("voiceProfile"))
        assertEquals("THINKING", presObj.getString("uiProfile"))
        assertFalse(presObj.getBoolean("humorAllowed"))
        assertTrue(presObj.getBoolean("speechInterruptible"))

        // Round-trip deserialization
        val deserialized = EmotionalJsonContract.fromJson(jsonString)
        assertEquals(snapshot, deserialized)
    }

    @Test
    fun testSerializationOfAllSixStates() {
        val states = listOf(
            AssistantState.HAPPY,
            AssistantState.SAD,
            AssistantState.KIDDING,
            AssistantState.PANIC,
            AssistantState.THINKING,
            AssistantState.FOCUSED
        )

        for (state in states) {
            val snapshot = EmotionalSnapshot.defaultForState(state, sessionId = "session-$state")
            val jsonStr = EmotionalJsonContract.toJson(snapshot)
            val deserialized = EmotionalJsonContract.fromJson(jsonStr)
            assertEquals(state, deserialized.current.state)
            assertEquals("session-$state", deserialized.sessionId)
        }
    }

    @Test
    fun testSerializationWithExpiryTimestamp() {
        val snapshot = EmotionalSnapshot(
            schemaVersion = 1,
            sessionId = "session-happy-1",
            version = 2,
            current = StateDetail(
                state = AssistantState.HAPPY,
                previousState = AssistantState.THINKING,
                reason = "task_verified_success",
                confidence = 0.95f,
                priority = 50,
                enteredAtMs = 5000L,
                expiresAtMs = 9000L,
                operationId = "task-789"
            ),
            context = ContextDetail(
                taskPhase = TaskPhase.SUCCEEDED,
                sentiment = Sentiment.POSITIVE,
                sentimentConfidence = 0.90f,
                source = SignalSource.TASK_MANAGER
            ),
            presentation = PresentationDetail(
                voiceProfile = "HAPPY",
                uiProfile = "HAPPY",
                humorAllowed = true,
                speechInterruptible = true
            )
        )

        val jsonStr = EmotionalJsonContract.toJson(snapshot)
        val json = JSONObject(jsonStr)
        val currentObj = json.getJSONObject("current")
        assertEquals(9000L, currentObj.getLong("expiresAtMs"))

        val deserialized = EmotionalJsonContract.fromJson(jsonStr)
        assertEquals(9000L, deserialized.current.expiresAtMs)
        assertEquals("task-789", deserialized.current.operationId)
    }

    @Test
    fun testDeserializationFallbackOnMalformedJson() {
        val fallback = EmotionalJsonContract.fromJsonSafe("{ invalid json }")
        assertNotNull(fallback)
        assertEquals(AssistantState.FOCUSED, fallback.current.state)
        assertEquals("fallback_on_parse_error", fallback.current.reason)
    }
}
