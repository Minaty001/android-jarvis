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
        when (action) {
            "clean_memory", "kill_apps", "memory" -> {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningApps = activityManager.runningAppProcesses ?: return ToolResult.Failed("Could not retrieve running apps.")

                var killedCount = 0
                for (app in runningApps) {
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

            "clean_storage", "storage_clean", "clean_cache" -> {
                val janitor = com.jarvis.storage.AutomatedStorageJanitorEngine(context)
                val res = janitor.cleanStaleStorage()
                return ToolResult.Success(res.message, mapOf("freed_bytes" to res.freedBytes, "files_deleted" to res.filesDeleted))
            }

            "fix_network", "network_recovery", "restart_network" -> {
                val netEngine = com.jarvis.network.SelfRecoveryNetworkEngine(context)
                val res = netEngine.attemptRecovery()
                val details = res.actionsTaken.joinToString("; ")
                return ToolResult.Success(
                    message = "${res.currentStatus} Actions: $details",
                    data = mapOf("recovered" to res.success, "actions_count" to res.actionsTaken.size)
                )
            }

            "adapt_audio", "ambient_audio", "volume_adapt" -> {
                val audioProfiler = com.jarvis.control.DynamicAudioProfileEngine(context)
                val targetVol = audioProfiler.applyAdaptiveVolume()
                val rep = audioProfiler.evaluateAmbientProfile()
                return ToolResult.Success(
                    message = "Adapted audio volume to $targetVol% (${rep.acousticLevel.name}). ${rep.rationale}",
                    data = mapOf("volume_percent" to targetVol, "acoustic_level" to rep.acousticLevel.name)
                )
            }

            else -> return ToolResult.Failed("Unknown action: $action. Supported: clean_memory, clean_storage, fix_network, adapt_audio.")
        }
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        return if (result.success) {
            VerificationResult.success(result.message)
        } else {
            VerificationResult.failure(result.message)
        }
    }
}
