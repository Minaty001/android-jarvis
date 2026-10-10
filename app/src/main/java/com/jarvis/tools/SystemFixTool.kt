package com.jarvis.tools

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.jarvis.execution.VerificationResult

class SystemFixTool(private val context: Context) : Tool {
    override val name = "SYSTEM_FIX"
    override val description = "Autonomously kills rogue background apps to free up memory. Params: action (must be 'clean_memory')"

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"] ?: return ToolResult.Failed("Parameter 'action' is required.")
        if (action != "clean_memory") {
            return ToolResult.Failed("Unknown action: $action")
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningApps = activityManager.runningAppProcesses ?: return ToolResult.Failed("Could not retrieve running apps.")

        var killedCount = 0
        for (app in runningApps) {
            // Kill non-essential background processes
            if (app.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                app.pkgList.forEach { pkg ->
                    if (!pkg.contains("jarvis")) {
                        activityManager.killBackgroundProcesses(pkg)
                        killedCount++
                    }
                }
            }
        }

        return ToolResult.Success("Cleared memory. Killed $killedCount background processes.")
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        return if (result.success) {
            VerificationResult.success(result.message)
        } else {
            VerificationResult.failure(result.message)
        }
    }
}
