package com.jarvis.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PrivacyDefenderEngine(private val context: Context) {
    private val TAG = "PrivacyDefender"
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun onAppInstalledOrUpdated(packageName: String) {
        scope.launch {
            Log.i(TAG, "Privacy Defender checking new/updated app: \$packageName")
            try {
                val pm = context.packageManager
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                
                val requestedPermissions = packageInfo.requestedPermissions ?: return@launch
                
                val dangerousRequests = requestedPermissions.filter {
                    it == "android.permission.CAMERA" || 
                    it == "android.permission.RECORD_AUDIO" ||
                    it == "android.permission.ACCESS_FINE_LOCATION" ||
                    it == "android.permission.READ_SMS"
                }

                if (dangerousRequests.isNotEmpty()) {
                    val appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                    val perms = dangerousRequests.joinToString { it.substringAfterLast('.') }
                    val warningMsg = "Security Alert: \$appName was just installed and requests sensitive permissions: \$perms"
                    Log.w(TAG, warningMsg)
                    
                    // Fire a SystemAlert so Jarvis can proactively warn the user
                    JarvisEventBus.shared.post(
                        JarvisEvent.SystemAlert(level = "WARNING", message = warningMsg)
                    )
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "App not found during security scan: \$packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Error in PrivacyDefender: \${e.message}")
            }
        }
    }
}
