package com.jarvis.emotion

import org.json.JSONObject

/**
 * EmotionalJsonContract
 *
 * Implements Schema Version 1 for state observation, diagnostics, and serialization.
 * Converts strongly-typed [EmotionalSnapshot] domain models to/from stable JSON.
 */
object EmotionalJsonContract {
    const val CURRENT_SCHEMA_VERSION = 1

    fun toJson(snapshot: EmotionalSnapshot): String {
        val root = JSONObject()
        root.put("schemaVersion", snapshot.schemaVersion)
        root.put("sessionId", snapshot.sessionId)
        root.put("version", snapshot.version)

        val currentObj = JSONObject()
        currentObj.put("state", snapshot.current.state.name)
        currentObj.put("previousState", snapshot.current.previousState.name)
        currentObj.put("reason", snapshot.current.reason)
        currentObj.put("confidence", snapshot.current.confidence.toDouble())
        currentObj.put("priority", snapshot.current.priority)
        currentObj.put("enteredAtMs", snapshot.current.enteredAtMs)
        if (snapshot.current.expiresAtMs != null) {
            currentObj.put("expiresAtMs", snapshot.current.expiresAtMs)
        } else {
            currentObj.put("expiresAtMs", JSONObject.NULL)
        }
        if (snapshot.current.operationId != null) {
            currentObj.put("operationId", snapshot.current.operationId)
        } else {
            currentObj.put("operationId", JSONObject.NULL)
        }
        root.put("current", currentObj)

        val contextObj = JSONObject()
        contextObj.put("taskPhase", snapshot.context.taskPhase.name)
        contextObj.put("sentiment", snapshot.context.sentiment.name)
        contextObj.put("sentimentConfidence", snapshot.context.sentimentConfidence.toDouble())
        contextObj.put("source", snapshot.context.source.name)
        root.put("context", contextObj)

        val presentationObj = JSONObject()
        presentationObj.put("voiceProfile", snapshot.presentation.voiceProfile)
        presentationObj.put("uiProfile", snapshot.presentation.uiProfile)
        presentationObj.put("humorAllowed", snapshot.presentation.humorAllowed)
        presentationObj.put("speechInterruptible", snapshot.presentation.speechInterruptible)
        root.put("presentation", presentationObj)

        return root.toString()
    }

    fun fromJson(jsonStr: String): EmotionalSnapshot {
        val root = JSONObject(jsonStr)
        val schemaVer = root.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
        val sessionId = root.optString("sessionId", "session_unknown")
        val version = root.optInt("version", 1)

        val currentObj = root.getJSONObject("current")
        val stateStr = currentObj.getString("state")
        val prevStateStr = currentObj.optString("previousState", "FOCUSED")
        val state = runCatching { AssistantState.valueOf(stateStr) }.getOrDefault(AssistantState.FOCUSED)
        val prevState = runCatching { AssistantState.valueOf(prevStateStr) }.getOrDefault(AssistantState.FOCUSED)
        val reason = currentObj.optString("reason", "unknown")
        val confidence = currentObj.optDouble("confidence", 1.0).toFloat()
        val priority = currentObj.optInt("priority", 10)
        val enteredAtMs = currentObj.optLong("enteredAtMs", System.currentTimeMillis())
        val expiresAtMs = if (currentObj.isNull("expiresAtMs")) null else currentObj.optLong("expiresAtMs")
        val operationId = if (currentObj.isNull("operationId")) null else currentObj.optString("operationId")

        val stateDetail = StateDetail(
            state = state,
            previousState = prevState,
            reason = reason,
            confidence = confidence,
            priority = priority,
            enteredAtMs = enteredAtMs,
            expiresAtMs = expiresAtMs,
            operationId = operationId
        )

        val contextObj = root.optJSONObject("context")
        val contextDetail = if (contextObj != null) {
            val taskPhaseStr = contextObj.optString("taskPhase", "IDLE")
            val sentimentStr = contextObj.optString("sentiment", "UNKNOWN")
            val sourceStr = contextObj.optString("source", "NLU")
            ContextDetail(
                taskPhase = runCatching { TaskPhase.valueOf(taskPhaseStr) }.getOrDefault(TaskPhase.IDLE),
                sentiment = runCatching { Sentiment.valueOf(sentimentStr) }.getOrDefault(Sentiment.UNKNOWN),
                sentimentConfidence = contextObj.optDouble("sentimentConfidence", 0.0).toFloat(),
                source = runCatching { SignalSource.valueOf(sourceStr) }.getOrDefault(SignalSource.NLU)
            )
        } else {
            ContextDetail()
        }

        val presObj = root.optJSONObject("presentation")
        val presentationDetail = if (presObj != null) {
            PresentationDetail(
                voiceProfile = presObj.optString("voiceProfile", state.name),
                uiProfile = presObj.optString("uiProfile", state.name),
                humorAllowed = presObj.optBoolean("humorAllowed", true),
                speechInterruptible = presObj.optBoolean("speechInterruptible", true)
            )
        } else {
            PresentationDetail(voiceProfile = state.name, uiProfile = state.name)
        }

        return EmotionalSnapshot(
            schemaVersion = schemaVer,
            sessionId = sessionId,
            version = version,
            current = stateDetail,
            context = contextDetail,
            presentation = presentationDetail
        )
    }

    fun fromJsonSafe(jsonStr: String): EmotionalSnapshot {
        return runCatching { fromJson(jsonStr) }.getOrElse {
            EmotionalSnapshot(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                sessionId = "session_fallback",
                version = 1,
                current = StateDetail(
                    state = AssistantState.FOCUSED,
                    previousState = AssistantState.FOCUSED,
                    reason = "fallback_on_parse_error",
                    confidence = 1.0f,
                    priority = 10,
                    enteredAtMs = System.currentTimeMillis()
                )
            )
        }
    }
}
