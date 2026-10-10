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
        try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val appInfo = pkgInfo.applicationInfo ?: return
            val appName = pm.getApplicationLabel(appInfo).toString()
            
            val perms = pkgInfo.requestedPermissions ?: emptyArray()
            val hasOverlay = perms.contains("android.permission.SYSTEM_ALERT_WINDOW")
            val hasSms = perms.contains("android.permission.READ_SMS")
            val hasCamera = perms.contains("android.permission.CAMERA")
            val hasMic = perms.contains("android.permission.RECORD_AUDIO")
            
            if (hasOverlay) {
                JarvisEventBus.shared.post(
                    JarvisEvent.SystemAlert(
                        level = "HIGH", 
                        message = "Warning. The app $appName requests permission to draw over other apps. This is a common tactic used by screen hijacking malware."
                    )
                )
            } else if (hasCamera && hasMic && hasSms) {
                JarvisEventBus.shared.post(
                    JarvisEvent.SystemAlert(
                        level = "MEDIUM", 
                        message = "Notice. The app $appName requests access to your camera, microphone, and messages. Please ensure you trust this application."
                    )
                )
            }
        } catch (e: Exception) {
            // App might have been uninstalled immediately or not found
        }
    }
}
