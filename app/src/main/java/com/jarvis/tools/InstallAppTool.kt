package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jarvis.accessibility.AutonomousUiNavigator
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import kotlinx.coroutines.delay

/**
 * Tool to launch Google Play Store to search and install applications.
 * Uses search intent (`market://search?q=...`) and automated UI accessibility click on Install.
 */
class InstallAppTool(private val context: Context? = null) : Tool {
    private val TAG = "InstallAppTool"

    override val name: String = "INSTALL_APP"
    override val description: String = "Searches Google Play Store for an app and initiates download/installation. Parameter: app (string, e.g. 'telegram', 'whatsapp')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.MEDIUM)

    val metadata = ToolMetadata(
        name = "INSTALL_APP",
        description = "Launches Google Play Store to search for and install an app.",
        parameters = listOf(
            com.jarvis.foundation.ParameterSchema("app", "string", "Name of the app to search and install from Play Store", required = true)
        ),
        riskLevel = RiskLevel.MEDIUM
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val appName = params["app"]?.trim()
            ?: return ToolResult.Failed("App name missing for installation")

        val ctx = context ?: return ToolResult.Success("Play Store launch simulated for $appName")

        Log.i(TAG, "Initiating Play Store installation for '$appName'")

        // 1. Launch Play Store search intent for the target application
        val searchUri = Uri.parse("market://search?q=${Uri.encode(appName)}")
        val marketIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launched = try {
            ctx.startActivity(marketIntent)
            true
        } catch (e: Exception) {
            // Fallback: Open Play Store main page if market search URI fails
            val playStoreIntent = ctx.packageManager.getLaunchIntentForPackage("com.android.vending")
            if (playStoreIntent != null) {
                playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(playStoreIntent)
                true
            } else {
                false
            }
        }

        if (!launched) {
            return ToolResult.Failed("Could not open Google Play Store on this device")
        }

        // 2. Automated UI interaction via Accessibility Service if active
        val service = JarvisAccessibilityService.instance
        if (service != null) {
            delay(1500) // Allow Play Store UI to load

            val installTargets = listOf(
                "install", "download", "installieren", "इनस्टॉल", "इनस्टॉल करें", "get"
            )

            var installClicked = false
            for (target in installTargets) {
                val node = AutonomousUiNavigator.findNodeFuzzy(target, service.rootInActiveWindow)
                if (node != null && node.isClickable) {
                    installClicked = service.clickNode(node)
                    if (installClicked) break
                }
            }

            if (!installClicked) {
                // Try searching inside Play Store if search results screen wasn't opened directly
                service.searchForegroundApp(appName)
                delay(1200)
                for (target in installTargets) {
                    val node = AutonomousUiNavigator.findNodeFuzzy(target, service.rootInActiveWindow)
                    if (node != null) {
                        installClicked = service.clickNode(node)
                        if (installClicked) break
                    }
                }
            }

            if (installClicked) {
                return ToolResult.Success("Opened Play Store and clicked Install for '$appName'")
            }
        }

        return ToolResult.Success("Opened Play Store search for '$appName'")
    }
}
