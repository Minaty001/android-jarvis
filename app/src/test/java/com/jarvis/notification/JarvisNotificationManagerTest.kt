package com.jarvis.notification

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JarvisNotificationManagerTest {

    @Before
    fun setup() {
        JarvisNotificationManager.resetThrottlerForTesting()
    }

    @Test
    fun testThrottlerSuppressesIdenticalContent() {
        assertTrue(JarvisNotificationManager.shouldUpdateNotification("Wake word active", 1000L))
        assertFalse("Same content must not trigger notification update", 
            JarvisNotificationManager.shouldUpdateNotification("Wake word active", 1100L))
    }

    @Test
    fun testThrottlerEnforcesMinimumIntervalForDifferentContent() {
        assertTrue(JarvisNotificationManager.shouldUpdateNotification("Wake word active", 1000L))
        assertFalse("Rapid change within 500ms must be throttled", 
            JarvisNotificationManager.shouldUpdateNotification("Listening...", 1200L))
        assertTrue("Change after 500ms must be allowed", 
            JarvisNotificationManager.shouldUpdateNotification("Listening...", 1501L))
    }

    @Test
    fun testStrictThirdPartyIsolation() {
        assertTrue(JarvisNotificationManager.isJarvisNotification("com.jarvis", 1001))
        assertFalse(JarvisNotificationManager.isJarvisNotification("com.whatsapp", 1001))
        assertFalse(JarvisNotificationManager.isJarvisNotification("com.google.android.gm", 1001))
    }

    @Test
    fun testChannelConstantsDefined() {
        assertEquals("jarvis_service_channel", JarvisNotificationManager.CHANNEL_SERVICE)
        assertEquals("jarvis_reminders", JarvisNotificationManager.CHANNEL_REMINDERS)
        assertEquals("jarvis_proactive_channel", JarvisNotificationManager.CHANNEL_ALERTS)
        assertEquals("jarvis_briefing_channel", JarvisNotificationManager.CHANNEL_BRIEFINGS)
    }
}
