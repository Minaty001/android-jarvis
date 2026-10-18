package com.jarvis.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.telecom.CallAndSmsAgent

class ClipboardTool(private val context: Context? = null) : Tool {
    override val name: String = "CLIPBOARD_MANAGE"
    override val description: String = "Reads, copies, pastes clipboard text, and injects the latest SMS OTP code into focused fields."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.MEDIUM)

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("action", "string", "Clipboard action: 'read', 'copy', 'paste', or 'paste_otp'", required = true),
            ParameterSchema("text", "string", "Text to copy when action is 'copy'", required = false)
        ),
        riskLevel = RiskLevel.MEDIUM
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "read"
        val textToCopy = params["text"] ?: params["content"]

        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        when (action) {
            "read", "get" -> {
                if (clipboard == null) {
                    return ToolResult.Success("Clipboard service is currently unavailable.", mapOf("action" to "read", "empty" to "true"))
                }
                val clip = try { clipboard.primaryClip } catch (e: Exception) { null }
                if (clip == null || clip.itemCount == 0) {
                    return ToolResult.Success("Your clipboard is empty.", mapOf("action" to "read", "empty" to "true"))
                }
                val item = clip.getItemAt(0)
                val clipText = item?.text?.toString() ?: item?.coerceToText(context)?.toString()
                if (clipText.isNullOrBlank()) {
                    return ToolResult.Success("Your clipboard is empty.", mapOf("action" to "read", "empty" to "true"))
                }
                return ToolResult.Success(
                    message = "Clipboard contains: \"$clipText\"",
                    data = mapOf("action" to "read", "text" to clipText, "empty" to "false")
                )
            }
            "copy", "set" -> {
                if (clipboard == null) {
                    return ToolResult.Success("Clipboard service is currently unavailable.", mapOf("action" to "copy", "success" to "false"))
                }
                val text = textToCopy.orEmpty()
                if (text.isBlank()) {
                    return ToolResult.Failed("No text provided to copy to clipboard.")
                }
                val clip = ClipData.newPlainText("JARVIS", text)
                Handler(Looper.getMainLooper()).post {
                    clipboard.setPrimaryClip(clip)
                }
                return ToolResult.Success(
                    message = "Copied to clipboard: \"$text\"",
                    data = mapOf("action" to "copy", "text" to text, "success" to "true")
                )
            }
            "paste" -> {
                val clip = try { clipboard?.primaryClip } catch (e: Exception) { null }
                val item = if (clip != null && clip.itemCount > 0) clip.getItemAt(0) else null
                val clipText = item?.text?.toString() ?: item?.coerceToText(context)?.toString()
                if (clipText.isNullOrBlank()) {
                    return ToolResult.Success("Clipboard is empty; nothing to paste.", mapOf("action" to "paste", "success" to "false"))
                }

                val service = JarvisAccessibilityService.instance
                if (service == null) {
                    return ToolResult.Success("Accessibility service is required to paste into the active field.", mapOf("action" to "paste", "success" to "false"))
                }

                val injected = service.injectTextIntoFocusedInput(clipText, append = false)
                return if (injected) {
                    ToolResult.Success("Pasted clipboard text into the active field.", mapOf("action" to "paste", "success" to "true"))
                } else {
                    ToolResult.Success("No active input field found to paste into.", mapOf("action" to "paste", "success" to "false"))
                }
            }
            "paste_otp", "otp" -> {
                val agent = CallAndSmsAgent.instance
                val recentOtps = agent?.readRecentSms(1, otpOnly = true)
                val otpCode = recentOtps?.firstOrNull()?.otpCode

                if (otpCode.isNullOrBlank()) {
                    return ToolResult.Success("No recent OTP code found in your SMS messages.", mapOf("action" to "paste_otp", "success" to "false"))
                }

                // Also copy OTP to clipboard
                if (clipboard != null) {
                    val clip = ClipData.newPlainText("JARVIS_OTP", otpCode)
                    Handler(Looper.getMainLooper()).post {
                        clipboard.setPrimaryClip(clip)
                    }
                }

                val service = JarvisAccessibilityService.instance
                if (service == null) {
                    return ToolResult.Success("Found OTP $otpCode and copied to clipboard, but Accessibility service is not running to type it.", mapOf("action" to "paste_otp", "otp" to otpCode, "typed" to "false"))
                }

                val injected = service.injectTextIntoFocusedInput(otpCode, append = false)
                return if (injected) {
                    ToolResult.Success("Injected OTP code $otpCode into the active field.", mapOf("action" to "paste_otp", "otp" to otpCode, "typed" to "true"))
                } else {
                    ToolResult.Success("Found OTP $otpCode and copied to clipboard, but could not detect an active text field.", mapOf("action" to "paste_otp", "otp" to otpCode, "typed" to "false"))
                }
            }
            else -> return ToolResult.Failed("Unsupported clipboard action: $action")
        }
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        return if (result.success) {
            VerificationResult.success("Clipboard action completed (${result.data["action"] ?: "unknown"})")
        } else {
            VerificationResult.failure("Clipboard action failed: ${result.message}")
        }
    }
}
