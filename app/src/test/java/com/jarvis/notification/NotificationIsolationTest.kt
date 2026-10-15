package com.jarvis.notification

import org.junit.Assert.*
import org.junit.Test

class NotificationIsolationTest {

    @Test
    fun testOnlyJarvisPackageIsSubjectToInternalDismissal() {
        val externalPackages = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "android"
        )
        for (pkg in externalPackages) {
            assertFalse(
                "External package $pkg must never be marked as a Jarvis notification",
                JarvisNotificationManager.isJarvisNotification(pkg, 123)
            )
        }
    }

    @Test
    fun testJarvisPackageIdentifiedProperly() {
        assertTrue(JarvisNotificationManager.isJarvisNotification("com.jarvis", 1001))
    }
}
