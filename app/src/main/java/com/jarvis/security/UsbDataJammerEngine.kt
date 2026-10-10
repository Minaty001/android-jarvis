package com.jarvis.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * USB Data Jacking Blocker Engine.
 * Listens for hardware USB state broadcasts. Warns the user if a public or untrusted
 * charging port has data connectivity (MTP/PTP/ADB) active rather than pure charging.
 */
class UsbDataJammerEngine(private val context: Context? = null) {

    private val TAG = "UsbDataJammer"

    data class UsbSecurityStatus(
        val isConnected: Boolean,
        val isDataActive: Boolean,
        val isAdbActive: Boolean,
        val warningMessage: String
    )

    private var lastStatus = UsbSecurityStatus(
        isConnected = false,
        isDataActive = false,
        isAdbActive = false,
        warningMessage = "USB disconnected"
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == "android.hardware.usb.action.USB_STATE") {
                val connected = intent.getBooleanExtra("connected", false)
                val configured = intent.getBooleanExtra("configured", false)
                val adb = intent.getBooleanExtra("adb", false)
                val mtp = intent.getBooleanExtra("mtp", false)
                val ptp = intent.getBooleanExtra("ptp", false)

                val dataActive = connected && (adb || mtp || ptp || configured)
                val msg = if (dataActive) {
                    "ALERT: USB data connection detected. Untrusted charging port could access files. Switch to Charge Only."
                } else if (connected) {
                    "USB connected in power-only mode (Safe)."
                } else {
                    "USB disconnected."
                }

                lastStatus = UsbSecurityStatus(
                    isConnected = connected,
                    isDataActive = dataActive,
                    isAdbActive = adb,
                    warningMessage = msg
                )

                if (dataActive) {
                    Log.w(TAG, msg)
                }
            }
        }
    }

    fun register() {
        context?.let { ctx ->
            try {
                val filter = IntentFilter("android.hardware.usb.action.USB_STATE")
                ctx.registerReceiver(usbReceiver, filter)
                Log.i(TAG, "UsbDataJammer receiver registered")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register USB receiver: ${e.message}")
            }
        }
    }

    fun unregister() {
        context?.let { ctx ->
            try {
                ctx.unregisterReceiver(usbReceiver)
            } catch (_: Exception) {}
        }
    }

    fun getStatus(): UsbSecurityStatus = lastStatus
}
