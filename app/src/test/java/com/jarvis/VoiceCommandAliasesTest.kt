package com.jarvis

import com.jarvis.ai.VoiceCommandAliases
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandAliasesTest {
    @Test
    fun expandsOnlyAnExactNormalizedAliasOnce() {
        val aliases = mapOf("  launch   my music " to "open youtube", "open youtube" to "play music")

        assertEquals("open youtube", VoiceCommandAliases.expand("LAUNCH my  music", aliases))
        assertEquals("launch my music please", VoiceCommandAliases.expand("launch my music please", aliases))
        assertEquals("unknown command", VoiceCommandAliases.expand("unknown command", aliases))
    }

    @Test
    fun aliasJsonRoundTripsAndMissingDataMeansNoAliases() {
        val aliases = mapOf("start music" to "play music")

        assertEquals(aliases, VoiceCommandAliases.fromJson(VoiceCommandAliases.toJson(aliases)))
        assertEquals(emptyMap<String, String>(), VoiceCommandAliases.fromJson(JSONObject()))
    }
}
