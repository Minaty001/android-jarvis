package com.jarvis

import com.jarvis.tools.SystemCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val API_ANDROID_13 = 33
private const val API_ANDROID_14 = 34

class CapabilityMatrixTest {

    @Test
    fun wifiOpenableBelowAndroid13() {
        assertTrue(SystemCapability.wifiToggle(31).canAttempt)
        assertFalse(SystemCapability.wifiToggle(API_ANDROID_13).canAttempt)
        assertNotNull(SystemCapability.wifiToggle(API_ANDROID_13).panelAction)
    }

    @Test
    fun bluetoothAttemptedOnAllSupportedVersions() {
        assertTrue(SystemCapability.bluetoothToggle(31).canAttempt)
        assertTrue(SystemCapability.bluetoothToggle(API_ANDROID_13).canAttempt)
    }

    @Test
    fun hotspotBlockedAcrossVersions() {
        val d = SystemCapability.hotspotToggle(API_ANDROID_13)
        assertFalse(d.canAttempt)
        assertNotNull(d.panelAction)
    }

    @Test
    fun dndNeedsPanelAccessOnAndroid14() {
        assertTrue(SystemCapability.dndToggle(API_ANDROID_13).canAttempt)
        val d14 = SystemCapability.dndToggle(API_ANDROID_14)
        assertFalse(d14.canAttempt)
        assertNotNull(d14.panelAction)
    }

    @Test
    fun flashlightAlwaysAttempted() {
        assertTrue(SystemCapability.flashlightToggle(API_ANDROID_13).canAttempt)
    }

    @Test
    fun unknownToggleIsHonestlyUnknown() {
        val d = SystemCapability.decide(35, "teleport")
        assertFalse(d.canAttempt)
        assertNotNull(d.reason)
    }

    @Test
    fun decideRoutesToCorrectMatrix() {
        assertFalse(SystemCapability.decide(API_ANDROID_13, "wifi").canAttempt)
        assertFalse(SystemCapability.decide(API_ANDROID_13, "hotspot").canAttempt)
        assertTrue(SystemCapability.decide(API_ANDROID_13, "dnd").canAttempt)
    }
}