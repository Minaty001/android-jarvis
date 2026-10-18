package com.jarvis.assistant

import com.jarvis.ai.AssistantIntent
import com.jarvis.ai.IntentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSettingsHelperTest {

    @Test
    fun testIntentResolverOpenAssistantSettings() {
        val openUtterances = listOf(
            "set jarvis as default assistant",
            "make jarvis default assistant",
            "open assistant settings",
            "default assistant settings",
            "change default assistant"
        )
        for (u in openUtterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be DEFAULT_ASSISTANT_SETTINGS for '$u'",
                AssistantIntent.DEFAULT_ASSISTANT_SETTINGS, resolved?.intent)
            assertEquals("Action should be open for '$u'", "open", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testIntentResolverCheckAssistantStatus() {
        val checkUtterances = listOf(
            "default assistant status",
            "is jarvis default assistant",
            "check default assistant",
            "assistant status"
        )
        for (u in checkUtterances) {
            val resolved = IntentResolver.resolve(u)
            assertNotNull("Should resolve '$u'", resolved)
            assertEquals("Intent should be DEFAULT_ASSISTANT_SETTINGS for '$u'",
                AssistantIntent.DEFAULT_ASSISTANT_SETTINGS, resolved?.intent)
            assertEquals("Action should be check for '$u'", "check", resolved?.params?.get("action"))
        }
    }

    @Test
    fun testFormatStatusMessage() {
        val activeMsg = AssistantSettingsHelper.formatStatusMessage(true)
        assertTrue(activeMsg.contains("default", ignoreCase = true))
        assertTrue(activeMsg.contains("active", ignoreCase = true) || activeMsg.contains("configured", ignoreCase = true))

        val inactiveMsg = AssistantSettingsHelper.formatStatusMessage(false)
        assertTrue(inactiveMsg.contains("not", ignoreCase = true) || inactiveMsg.contains("settings", ignoreCase = true))
    }
}
