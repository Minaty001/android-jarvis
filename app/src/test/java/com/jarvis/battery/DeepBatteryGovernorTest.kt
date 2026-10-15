package com.jarvis.battery

import org.junit.Assert.*
import org.junit.Test

class DeepBatteryGovernorTest {

    @Test
    fun testThrottleStateTransitions() {
        val governor = DeepBatteryGovernor(null)
        val report = governor.evaluateGovernorState()
        assertEquals(DeepBatteryGovernor.ThrottleState.NORMAL, report.throttleState)
        assertTrue(governor.isHighThroughputAllowed())
    }

    @Test
    fun testThrottleListenerRegistration() {
        val governor = DeepBatteryGovernor(null)
        var notifiedState: DeepBatteryGovernor.ThrottleState? = null
        val listener: (DeepBatteryGovernor.ThrottleState) -> Unit = { state -> notifiedState = state }

        governor.addThrottleListener(listener)
        governor.notifyThrottleChanged(DeepBatteryGovernor.ThrottleState.THERMAL_THROTTLED)
        assertEquals(DeepBatteryGovernor.ThrottleState.THERMAL_THROTTLED, notifiedState)

        governor.removeThrottleListener(listener)
        governor.notifyThrottleChanged(DeepBatteryGovernor.ThrottleState.NORMAL)
        assertEquals(DeepBatteryGovernor.ThrottleState.THERMAL_THROTTLED, notifiedState)
    }
}
