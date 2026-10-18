package com.jarvis

import com.jarvis.agent.*
import com.jarvis.agent.skills.MediaSkill
import com.jarvis.agent.skills.SkillContext
import com.jarvis.execution.VerificationResult
import com.jarvis.execution.VerificationStatus
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AcceptanceScenariosTest {

    private val evaluator = GoalEvaluator()
    private val recovery = AgentRecoveryManager()

    /**
     * Test Case 1: "Mere liye koi badiya song chalao"
     * -> Song playback intent dispatched
     * -> If unverified (audio stream inactive): Goal MUST NOT be satisfied
     * -> If verified (audio active): Goal satisfied
     */
    @Test
    fun testScenario1_SongPlaybackVerificationGating() = runBlocking {
        val goal = "Mere liye koi badiya song chalao"
        val state = AgentState(
            taskId = 101,
            goal = goal,
            iteration = 1,
            lastAction = AgentAction("MUSIC_PLAY", mapOf("query" to "Arijit Singh top hits"))
        )

        val toolResult = ToolResult.Success("Searching and playing 'Arijit Singh top hits'")
        val observation = AgentObservation("TOOL", true, toolResult.message)

        // Case 1A: Audio not active yet -> UNVERIFIED
        val unverifiedResult = VerificationResult(
            status = VerificationStatus.UNKNOWN,
            message = "Playback intent dispatched for 'Arijit Singh top hits', but audio stream is not active yet"
        )
        val evaluationUnverified = evaluator.evaluate(goal, state, observation, unverifiedResult)
        assertFalse("Goal MUST NOT be satisfied if playback verification is false", evaluationUnverified.satisfied)
        assertTrue(evaluationUnverified.missingRequirements.any { it.contains("Media playback audio stream unconfirmed") })

        // Case 1B: Audio active on device -> VERIFIED
        val verifiedResult = VerificationResult(
            status = VerificationStatus.VERIFIED,
            message = "Media playback active on device: Arijit Singh top hits"
        )
        val evaluationVerified = evaluator.evaluate(goal, state, observation, verifiedResult)
        assertTrue("Goal MUST be satisfied only when playback is verified active", evaluationVerified.satisfied)
    }

    /**
     * Test Case 2: "Mere liye ek comedy video chalao"
     * -> Candidate research / skill gathers comedy video candidates
     * -> Top candidate selected and played
     * -> If playback unverified -> recovery selects alternative candidate
     * -> Playback verified -> goal completed
     */
    @Test
    fun testScenario2_ComedyVideoResearchAndAlternativeRecovery() = runBlocking {
        val goal = "Mere liye ek comedy video chalao"
        val workingMemory = AgentWorkingMemory(goal)
        val mediaSkill = MediaSkill()

        // 1. Skill handles goal and provides ranked comedy candidates
        val skillContext = SkillContext(
            goal = goal,
            workingMemory = workingMemory,
            userPreferences = mapOf("comedy_preference" to "standup comedy")
        )
        assertTrue(mediaSkill.canHandle(goal, skillContext))
        val skillResult = mediaSkill.execute(goal, skillContext)
        assertTrue(skillResult.handled)
        assertTrue(skillResult.candidateQueries.isNotEmpty())

        workingMemory.candidates.addAll(skillResult.candidateQueries)

        // 2. Select first candidate
        val firstCandidate = workingMemory.candidates.first()

        val state = AgentState(
            taskId = 102,
            goal = goal,
            iteration = 1,
            lastAction = AgentAction("YOUTUBE_PLAY", mapOf("query" to firstCandidate)),
            failureCount = 1
        )

        // 3. First candidate playback fails verification
        val failedObs = AgentObservation("TOOL", false, "Playback intent dispatched, but audio stream is not active yet")
        val recoveryPlan = recovery.determineRecovery(state.lastAction!!, failedObs, state, workingMemory)

        assertEquals("Recovery MUST switch to alternative candidate", RecoveryStrategy.SELECT_ALTERNATIVE, recoveryPlan.strategy)
        assertNotNull(recoveryPlan.modifiedAction)
        val altQuery = recoveryPlan.modifiedAction?.params?.get("query").orEmpty()
        assertNotEquals("Alternative query must be different from failed query", firstCandidate, altQuery)

        // 4. Alternative candidate succeeds and verifies
        state.lastAction = recoveryPlan.modifiedAction
        val successObservation = AgentObservation("TOOL", true, "Playing $altQuery on YouTube")
        val verifiedResult = VerificationResult(
            status = VerificationStatus.VERIFIED,
            message = "Media playback active on device: $altQuery"
        )
        val evaluation = evaluator.evaluate(goal, state, successObservation, verifiedResult)
        assertTrue("Goal satisfied after alternative candidate playback is verified", evaluation.satisfied)
    }

    /**
     * Test Case 3: "YouTube kholo aur comedy video chalao"
     * -> Compound / multi-step goal ("kholo" + "chalao")
     * -> Step 1: OPEN_APP alone MUST NOT complete goal
     * -> Step 2: YOUTUBE_PLAY + Verification = COMPLETE
     */
    @Test
    fun testScenario3_MultiStepCompoundGoalEvaluation() = runBlocking {
        val goal = "YouTube kholo aur comedy video chalao"

        // Step 1: OPEN_APP executed and app opened
        val step1State = AgentState(
            taskId = 103,
            goal = goal,
            iteration = 1,
            lastAction = AgentAction("OPEN_APP", mapOf("app" to "youtube")),
            lastDecision = AgentDecision(
                type = DecisionType.ACT,
                action = AgentAction("OPEN_APP", mapOf("app" to "youtube")),
                successCriteria = listOf("YouTube open", "comedy video playing")
            )
        )
        val step1Obs = AgentObservation("TOOL", true, "Opened youtube successfully")
        val step1Verif = VerificationResult(VerificationStatus.VERIFIED, "Target app 'youtube' verified installed and launched")

        val step1Eval = evaluator.evaluate(goal, step1State, step1Obs, step1Verif)
        assertFalse("OPEN_APP step alone MUST NOT satisfy multi-step goal", step1Eval.satisfied)
        assertTrue("Must indicate missing comedy video playback requirement", step1Eval.missingRequirements.any { it.contains("comedy video playing") || it.contains("Subsequent") })

        // Step 2: YOUTUBE_PLAY executed and verified. The kernel marks the OPEN_APP
        // criterion satisfied in step 1 via AgentState.satisfiedCriteria; both
        // requirements must be independently verified before the goal completes.
        val step2State = AgentState(
            taskId = 103,
            goal = goal,
            iteration = 2,
            completedSteps = 1,
            lastAction = AgentAction("YOUTUBE_PLAY", mapOf("query" to "bassi standup comedy")),
            lastDecision = AgentDecision(
                type = DecisionType.ACT,
                action = AgentAction("YOUTUBE_PLAY", mapOf("query" to "bassi standup comedy")),
                successCriteria = listOf("YouTube open", "comedy video playing")
            )
        ).apply { satisfiedCriteria.add("YouTube open".lowercase().trim()) }
        val step2Obs = AgentObservation("TOOL", true, "Playing bassi standup comedy on YouTube")
        val step2Verif = VerificationResult(VerificationStatus.VERIFIED, "Media playback active on device: bassi standup comedy")

        val step2Eval = evaluator.evaluate(goal, step2State, step2Obs, step2Verif)
        assertTrue("Goal satisfied when both app open and playback are verified", step2Eval.satisfied)
    }

    /**
     * Test Case 4: Ambient Sound Detection & Safety Alert Flow
     * -> User says "detect smoke alarm"
     * -> Fast-path Intent resolves to SOUND_DETECTION (tool: SOUND_DETECTOR, action: start, type: smoke_alarm)
     * -> Monitor engine begins listening
     * -> Acoustic pure-tone at 3000 Hz is classified as SMOKE_ALARM
     * -> Safety alert is dispatched with urgent announcement & haptic pulse
     * -> User says "stop sound detection" -> monitoring terminates cleanly
     */
    @Test
    fun testScenario4_AmbientSoundDetectionAndSafetyAlertFlow() = runBlocking {
        val intentModel = com.jarvis.ai.IntentModel()

        // 1. Resolve voice trigger
        val startIntent = intentModel.classify("detect smoke alarm")
        assertNotNull(startIntent)
        assertEquals(com.jarvis.ai.AssistantIntent.SOUND_DETECTION, startIntent?.intent)
        assertEquals("start", startIntent?.params?.get("action"))
        assertEquals("smoke_alarm", startIntent?.params?.get("type"))

        // 2. Execute SoundDetectorTool
        val tool = com.jarvis.tools.SoundDetectorTool(context = null)
        val startResult = tool.execute(startIntent!!.params)
        assertTrue(startResult.success)
        assertTrue(com.jarvis.sound.AmbientSoundMonitorEngine.isMonitoring())
        assertTrue(com.jarvis.sound.AmbientSoundMonitorEngine.getActiveCategories().contains(com.jarvis.sound.SoundCategory.SMOKE_ALARM))

        // 3. Classify synthetic 3000 Hz smoke alarm acoustic audio frame
        val classifier = com.jarvis.sound.AmbientSoundClassifier(sampleRate = 16000, windowSize = 1024)
        val audioSamples = ShortArray(1024)
        for (i in 0 until 1024) {
            val angle = 2.0 * Math.PI * 3000.0 * i / 16000.0
            audioSamples[i] = (22000 * Math.sin(angle)).toInt().toShort()
        }
        val classification = classifier.classify(audioSamples, 1024)
        assertEquals(com.jarvis.sound.SoundCategory.SMOKE_ALARM, classification.category)
        assertTrue(classification.confidence >= 0.70f)

        // 4. Dispatch safety alert
        var announcedText = ""
        val alertEvent = com.jarvis.sound.SoundDetectionEvent(
            category = classification.category,
            confidence = classification.confidence,
            dominantFrequencyHz = classification.dominantFrequencyHz
        )
        val dispatched = com.jarvis.sound.SoundAlertDispatcher.dispatchAlert(
            context = null,
            event = alertEvent,
            ttsSpeaker = { announcedText = it }
        )
        assertTrue(dispatched)
        assertTrue(announcedText.contains("Smoke alarm", ignoreCase = true))

        // 5. User stops sound detection
        val stopIntent = intentModel.classify("stop sound detection")
        assertNotNull(stopIntent)
        val stopResult = tool.execute(stopIntent!!.params)
        assertTrue(stopResult.success)
        assertFalse(com.jarvis.sound.AmbientSoundMonitorEngine.isMonitoring())
    }
}
