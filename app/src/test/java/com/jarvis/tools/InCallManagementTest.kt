package com.jarvis.tools

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.telecom.CallAndSmsAgent
import com.jarvis.telecom.CallSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallManagementTest {

    @Test
    fun testIntentResolverCallOnSpeaker() {
        val cases = listOf(
            "call mom on speaker" to "mom",
            "call john on speaker" to "john",
            "dial 123456 on speaker" to "123456"
        )
        for ((utterance, expectedTarget) in cases) {
            val resolved = IntentResolver.resolve(utterance)
            assertNotNull("Should resolve '$utterance'", resolved)
            assertEquals(AssistantIntent.TELEPHONY_CONTROL, resolved?.intent)
            assertEquals("call", resolved?.params?.get("action"))
            assertEquals("true", resolved?.params?.get("speaker"))
            assertEquals(expectedTarget, resolved?.params?.get("recipient"))
        }
    }

    @Test
    fun testIntentResolverSpeakerAndMuteControls() {
        val speakerOn = IntentResolver.resolve("put on speaker")
        assertNotNull(speakerOn)
        assertEquals(AssistantIntent.TELEPHONY_CONTROL, speakerOn?.intent)
        assertEquals("speaker_on", speakerOn?.params?.get("action"))

        val speakerOff = IntentResolver.resolve("speaker off")
        assertNotNull(speakerOff)
        assertEquals(AssistantIntent.TELEPHONY_CONTROL, speakerOff?.intent)
        assertEquals("speaker_off", speakerOff?.params?.get("action"))

        val muteMic = IntentResolver.resolve("mute mic")
        assertNotNull(muteMic)
        assertEquals(AssistantIntent.TELEPHONY_CONTROL, muteMic?.intent)
        assertEquals("mute_mic", muteMic?.params?.get("action"))

        val unmuteMic = IntentResolver.resolve("unmute mic")
        assertNotNull(unmuteMic)
        assertEquals(AssistantIntent.TELEPHONY_CONTROL, unmuteMic?.intent)
        assertEquals("unmute_mic", unmuteMic?.params?.get("action"))

        val hangUp = IntentResolver.resolve("hang up")
        assertNotNull(hangUp)
        assertEquals(AssistantIntent.TELEPHONY_CONTROL, hangUp?.intent)
        assertEquals("end_call", hangUp?.params?.get("action"))
    }

    @Test
    fun testTelephonyToolInCallExecution() = runBlocking {
        val agent = CallAndSmsAgent(null)
        val tool = TelephonyTool(context = null, callAndSmsAgent = agent)

        // 1. Speaker On
        val resSpeakerOn = tool.execute(mapOf("action" to "speaker_on"))
        assertTrue("Speaker on should succeed", resSpeakerOn is ToolResult.Success)

        // 2. Speaker Off
        val resSpeakerOff = tool.execute(mapOf("action" to "speaker_off"))
        assertTrue("Speaker off should succeed", resSpeakerOff is ToolResult.Success)

        // 3. Mute Mic
        val resMute = tool.execute(mapOf("action" to "mute_mic"))
        assertTrue("Mute mic should succeed", resMute is ToolResult.Success)

        // 4. Unmute Mic
        val resUnmute = tool.execute(mapOf("action" to "unmute_mic"))
        assertTrue("Unmute mic should succeed", resUnmute is ToolResult.Success)

        // 5. End Call
        val resEnd = tool.execute(mapOf("action" to "end_call"))
        assertTrue("End call should succeed", resEnd is ToolResult.Success)
    }

    @Test
    fun testAutoSpeakerphoneOnAnswerWhenNoHeadset() {
        val agent = CallAndSmsAgent(null)
        agent.onIncomingCallRinging("9876543210")
        val answered = agent.answerCall()
        assertTrue("Call answer should succeed", answered)
        assertTrue("Speakerphone should be activated automatically without headset", agent.isSpeakerphoneOnSimulation)
    }
}
