package com.jarvis.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Direct Command Receiver for ADB automation, desktop bridge, and external integrations.
 */
class CommandReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CommandReceiver"
        const val ACTION_EXECUTE_COMMAND = "com.jarvis.action.EXECUTE_COMMAND"
        const val EXTRA_COMMAND = "command"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val handled = AdbBridgeEngine.handleBroadcast(this, context, intent)
        if (!handled) {
            Log.d(TAG, "CommandReceiver received unhandled action: ${intent.action}")
        }
    }
}
