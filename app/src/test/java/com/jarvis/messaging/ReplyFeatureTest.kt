package com.jarvis.messaging

import com.jarvis.agent.AgentWorkingMemory
import com.jarvis.agent.skills.CommunicationSkill
import com.jarvis.agent.skills.SkillContext
import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import com.jarvis.foundation.PolicyEngine
import com.jarvis.notification.NotificationItem
import com.jarvis.notification.NotificationStore
import com.jarvis.telecom.ContactInfo
import com.jarvis.telecom.ContactResolver
import com.jarvis.tools.TelephonyTool
import com.jarvis.tools.WhatsAppTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReplyFeatureTest {

    private fun createContext(goal: String = ""): SkillContext =
        SkillContext(goal = goal, workingMemory = AgentWorkingMemory(goal = goal))

    @Before
    fun setUp() {
        NotificationStore.clear()
        AutoReplyPolicyManager.setMode(AutoReplyMode.OFF)
    }

    @Test
    fun testIntentResolverNotificationReply() {
        // Direct notification reply with "with"
        val res1 = IntentResolver.resolve("reply to notification 1 with yes")
        assertNotNull(res1)
        assertEquals(AssistantIntent.NOTIFICATION_INTERACTION, res1?.intent)
        assertEquals("trigger_action", res1?.params?.get("action"))
        assertEquals("1", res1?.params?.get("index"))
        assertEquals("yes", res1?.params?.get("text"))

        // Direct notification reply with "saying"
        val res2 = IntentResolver.resolve("reply to notification 2 saying on my way")
        assertNotNull(res2)
        assertEquals("2", res2?.params?.get("index"))
        assertEquals("on my way", res2?.params?.get("text"))

        // Notification reply without "to"
        val res3 = IntentResolver.resolve("reply notification 3: I will be late")
        assertNotNull(res3)
        assertEquals("3", res3?.params?.get("index"))
        assertEquals("I will be late", res3?.params?.get("text"))

        // Hinglish notification reply
        val res4 = IntentResolver.resolve("notification 4 ko reply karo call back in 5 mins")
        assertNotNull(res4)
        assertEquals("4", res4?.params?.get("index"))
        assertEquals("call back in 5 mins", res4?.params?.get("text"))
    }

    @Test
    fun testIntentResolverContactAndContextualReply() {
        // Contact reply with "saying"
        val res1 = IntentResolver.resolve("reply to Rahul saying I am on the way")
        assertNotNull(res1)
        assertEquals(AssistantIntent.WHATSAPP_SEND, res1?.intent)
        assertEquals("reply", res1?.params?.get("action"))
        assertEquals("Rahul", res1?.params?.get("recipient"))
        assertEquals("I am on the way", res1?.params?.get("message"))

        // Contact reply with multi-word name and "that"
        val res2 = IntentResolver.resolve("reply to John Doe that I will be late")
        assertNotNull(res2)
        assertEquals("reply", res2?.params?.get("action"))
        assertEquals("John Doe", res2?.params?.get("recipient"))
        assertEquals("I will be late", res2?.params?.get("message"))

        // Contact reply with colon
        val res3 = IntentResolver.resolve("reply to Alice: on my way")
        assertNotNull(res3)
        assertEquals("reply", res3?.params?.get("action"))
        assertEquals("Alice", res3?.params?.get("recipient"))
        assertEquals("on my way", res3?.params?.get("message"))

        // Hinglish contact reply
        val res4 = IntentResolver.resolve("Rahul ko reply karo call you soon")
        assertNotNull(res4)
        assertEquals("reply", res4?.params?.get("action"))
        assertEquals("Rahul", res4?.params?.get("recipient"))
        assertEquals("call you soon", res4?.params?.get("message"))

        // Contextual reply with "that"
        val res5 = IntentResolver.resolve("reply that I am driving")
        assertNotNull(res5)
        assertEquals("reply", res5?.params?.get("action"))
        assertEquals("I am driving", res5?.params?.get("message"))

        // Contextual reply with "saying"
        val res6 = IntentResolver.resolve("reply saying I will be late")
        assertNotNull(res6)
        assertEquals("reply", res6?.params?.get("action"))
        assertEquals("I will be late", res6?.params?.get("message"))

        // Contextual direct reply
        val res7 = IntentResolver.resolve("reply I am busy")
        assertNotNull(res7)
        assertEquals("reply", res7?.params?.get("action"))
        assertEquals("I am busy", res7?.params?.get("message"))
    }

    @Test
    fun testCommunicationSkillReply() = runBlocking {
        val skill = CommunicationSkill()

        // Multi-word name
        val res1 = skill.execute("reply to John Doe saying I am outside", createContext())
        assertTrue(res1.handled)
        assertEquals("WHATSAPP", res1.proposedAction?.type)
        assertEquals("reply", res1.proposedAction?.params?.get("action"))
        assertEquals("John Doe", res1.proposedAction?.params?.get("recipient"))
        assertEquals("I am outside", res1.proposedAction?.params?.get("message"))

        // Hinglish reply
        val res2 = skill.execute("Alice ko reply karo call you soon", createContext())
        assertTrue(res2.handled)
        assertEquals("reply", res2.proposedAction?.params?.get("action"))
        assertEquals("Alice", res2.proposedAction?.params?.get("recipient"))
        assertEquals("call you soon", res2.proposedAction?.params?.get("message"))

        // Contextual reply
        val res3 = skill.execute("reply saying call you later", createContext())
        assertTrue(res3.handled)
        assertEquals("reply", res3.proposedAction?.params?.get("action"))
        assertEquals("call you later", res3.proposedAction?.params?.get("message"))
    }

    @Test
    fun testWhatsAppToolContextualRecipientInference() = runBlocking {
        val contactResolver = ContactResolver(null)
        val engine = UniversalMessagingEngine(context = null, contactResolver = contactResolver)
        val tool = WhatsAppTool(context = null, messagingEngine = engine)

        // Add recent notification to NotificationStore
        NotificationStore.addNotification(
            NotificationItem(
                id = "com.whatsapp:10",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Alice",
                text = "Are you coming?",
                timestamp = System.currentTimeMillis() - 5000,
                isOngoing = false
            )
        )

        // Reply without recipient specified -> should infer Alice from the recent notification
        val res = tool.execute(mapOf("action" to "reply", "message" to "Yes, on my way"))
        assertTrue(res.success)
        assertTrue(res.message.contains("Alice"))
    }

    @Test
    fun testPolicyEngineAllowsReplyWithoutConfirmation() {
        val policyEngine = PolicyEngine(autonomousFullAuto = false)

        // Direct reply should NOT be blocked by manual confirmation gate
        val replyEval = policyEngine.evaluateAction("WHATSAPP", mapOf("action" to "reply", "recipient" to "John", "message" to "ok"))
        assertTrue(replyEval.allowed)
        assertFalse("Direct reply must be auto-guarded rather than requiring manual confirmation", replyEval.requiresApproval)

        // Read messages should NOT require approval
        val readEval = policyEngine.evaluateAction("WHATSAPP", mapOf("action" to "read_messages"))
        assertTrue(readEval.allowed)
        assertFalse(readEval.requiresApproval)

        // Auto reply mode configuration should NOT require approval
        val autoReplyEval = policyEngine.evaluateAction("WHATSAPP", mapOf("action" to "auto_reply", "mode" to "driving"))
        assertTrue(autoReplyEval.allowed)
        assertFalse(autoReplyEval.requiresApproval)
    }

    @Test
    fun testTelephonyToolAutoReplyUpdatesPolicyManager() = runBlocking {
        val tool = TelephonyTool(context = null)

        // Enable driving auto-reply
        val resDriving = tool.execute(mapOf("action" to "auto_reply", "mode" to "driving"))
        assertTrue(resDriving.success)
        assertEquals(AutoReplyMode.DRIVING, AutoReplyPolicyManager.mode)
        assertTrue(AutoReplyPolicyManager.isEnabled())

        // Disable auto-reply
        val resOff = tool.execute(mapOf("action" to "auto_reply", "mode" to "off"))
        assertTrue(resOff.success)
        assertEquals(AutoReplyMode.OFF, AutoReplyPolicyManager.mode)
        assertFalse(AutoReplyPolicyManager.isEnabled())
    }
}
