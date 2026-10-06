package com.jarvis

import com.jarvis.tools.AudioPresetTool
import com.jarvis.tools.PowerOptimizerTool
import com.jarvis.tools.SmartVoiceMemoTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesTest {

    @Test
    fun testPowerOptimizerToolSimulation() = runBlocking {
        val tool = PowerOptimizerTool(null)
        val statusResult = tool.execute(mapOf("action" to "status"))
        assertTrue("Status result should be successful", statusResult.success)
        assertTrue("Message should mention battery temp", statusResult.message.contains("Battery temp"))

        val thermalResult = tool.execute(mapOf("action" to "thermal"))
        assertTrue("Thermal result should be successful", thermalResult.success)

        val optimizeResult = tool.execute(mapOf("action" to "optimize"))
        assertTrue("Optimize result should be successful", optimizeResult.success)
        assertEquals("POWER_OPTIMIZER", tool.name)
    }

    @Test
    fun testAudioPresetToolSimulation() = runBlocking {
        val tool = AudioPresetTool(null)
        val bassResult = tool.execute(mapOf("preset" to "bass_boost"))
        assertTrue("Bass boost result should be successful", bassResult.success)
        assertEquals("bass_boost", bassResult.data["preset"])

        val vocalResult = tool.execute(mapOf("preset" to "vocal_clarity"))
        assertTrue("Vocal clarity result should be successful", vocalResult.success)

        val movieResult = tool.execute(mapOf("preset" to "movie"))
        assertTrue("Movie result should be successful", movieResult.success)

        val resetResult = tool.execute(mapOf("preset" to "reset"))
        assertTrue("Reset result should be successful", resetResult.success)
        assertEquals("AUDIO_PRESET", tool.name)
    }

    @Test
    fun testSmartVoiceMemoTool() = runBlocking {
        val tool = SmartVoiceMemoTool(null, null)
        
        // Record memo
        val recordResult = tool.execute(mapOf("action" to "record", "title" to "Team Sync Strategy", "query" to "Review Q4 deliverables and AI assistant deployment."))
        assertTrue("Record result should be successful", recordResult.success)
        assertTrue("Message should confirm recording", recordResult.message.contains("recorded"))

        // List memos
        val listResult = tool.execute(mapOf("action" to "list"))
        assertTrue("List result should be successful", listResult.success)
        assertEquals(1, listResult.data["count"])

        // Search memo
        val searchResult = tool.execute(mapOf("action" to "search", "query" to "Q4 deliverables"))
        assertTrue("Search result should be successful", searchResult.success)
        assertEquals(1, searchResult.data["matchCount"])

        // Summarize memo
        val summaryResult = tool.execute(mapOf("action" to "summarize", "title" to "Team Sync"))
        assertTrue("Summarize result should be successful", summaryResult.success)
        assertTrue("Summary should contain title", summaryResult.message.contains("Team Sync Strategy"))
        assertEquals("VOICE_MEMO", tool.name)
    }
}
