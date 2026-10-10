package com.jarvis.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val isUpdating = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (isUpdating && action == Intent.ACTION_PACKAGE_ADDED) return
            
            val packageName = intent.data?.schemeSpecificPart ?: return
            
            CoroutineScope(Dispatchers.Default).launch {
                analyzeNewApp(context, packageName, isUpdating)
            }
        }
    }

    private fun analyzeNewApp(context: Context, packageName: String, isUpdating: Boolean) {
        val engine = com.jarvis.service.JarvisForegroundService.instance?.voiceEngine?.assistantRuntime?.privacyDefenderEngine
        if (engine != null) {
            engine.onAppInstalledOrUpdated(packageName)
        } else {
            // Fallback if Jarvis is not running
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                com.jarvis.runtime.JarvisEventBus.shared.post(
                    JarvisEvent.SystemAlert(
                        level = "INFO", 
                        message = "App installed: \$appName"
                    )
                )
            } catch (e: Exception) {}
        }
    }
}
