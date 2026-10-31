package com.jarvis.tools

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.app.NotificationManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

/**
 * Device switchboard: Wi-Fi, Bluetooth, Do Not Disturb, hotspot and torch.
 * Honest rule: only reports success when the real system state actually
 * changed. Anything the OS blocks → opens the matching Settings panel and
 * says why in plain terms. Never pretends a toggle flipped.
 */
class SystemSwitchboardTool(private val context: Context) : Tool {
    override val name: String = "SYSTEM_SWITCHBOARD"
    override val description: String =
        "Turns on/off Wi-Fi, Bluetooth, Do Not Disturb, hotspot and the torch. Actions: wifi, bt, dnd, hotspot, led, state. On/off via target param."

    val metadata = ToolMetadata(
        name = "SYSTEM_SWITCHBOARD",
        description = "Toggles device radios, DND and torch; opens Settings when Android blocks the change.",
        parameters = listOf(
            com.jarvis.foundation.ParameterSchema("action", "string", "Action: wifi, bt, dnd, hotspot, led, state, brightness, ringer", required = true),
            com.jarvis.foundation.ParameterSchema("target", "string", "Target state: on, off, true, false. Or for ringer: silent, vibrate, normal", required = false),
            com.jarvis.foundation.ParameterSchema("value", "string", "Value for adjustments, e.g. brightness level 0-255", required = false)
        ),
        riskLevel = RiskLevel.MEDIUM
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "state"
        if (action == "state") return deviceState()
        if (action == "auto_rotate" || action == "rotation" || action == "screen_rotation" || action == "rotate") {
            val target = params["target"]?.trim()?.lowercase()
            val on = when (target) {
                "on", "enable", "1", "true" -> true
                "off", "disable", "0", "false" -> false
                else -> true
            }
            return toggleAutoRotate(on)
        }
        if (action == "brightness" || action == "screen_brightness") return adjustBrightness(params)
        if (action == "ringer" || action == "sound_mode" || action == "silent" || action == "vibrate") {
            val mode = params["mode"] ?: params["target"] ?: action
            return setRingerMode(mode)
        }
        if (action == "volume" || action == "set_volume" || action == "volume_up" || action == "volume_down" || action == "mute" || action == "unmute" ||
            action == "permissions" || action == "storage_permission" || action == "manage_storage" || action == "app_settings") {
            return DeviceSettingsTool(context).execute(params)
        }

        val normalizedAction = when {
            action == "wifi" || action == "set_wifi" || action.contains("wifi") -> "wifi"
            action == "bt" || action == "bluetooth" || action == "set_bluetooth" || action.contains("bluetooth") -> "bt"
            action == "dnd" || action.contains("dnd") || action.contains("do_not_disturb") -> "dnd"
            action == "hotspot" || action.contains("hotspot") -> "hotspot"
            action == "led" || action == "flashlight" || action == "torch" -> "led"
            else -> action
        }

        val target = params["target"]?.trim()?.lowercase()
            ?: params["state"]?.trim()?.lowercase()
            ?: params["mode"]?.trim()?.lowercase()
            ?: params["status"]?.trim()?.lowercase()
            ?: params["value"]?.trim()?.lowercase()
            ?: if (action.endsWith("_on") || action.endsWith("on")) "on" else if (action.endsWith("_off") || action.endsWith("off")) "off" else null

        val routineMode = params["routine_mode"] == "true"
        val on = when (target) {
            "on", "enable", "1", "true" -> true
            "off", "disable", "0", "false" -> false
            null -> null
            else -> null
        }
        if (on == null) {
            return ToolResult.Failed("Missing target for '$action'. Say 'turn wifi on' or 'wifi off'.")
        }

        val decision = SystemCapability.decide(Build.VERSION.SDK_INT, normalizedAction)
        return when (normalizedAction) {
            "wifi" -> toggleWifi(on, decision, routineMode)
            "bt" -> toggleBluetooth(on, decision, routineMode)
            "dnd" -> toggleDnd(on, decision)
            "hotspot" -> blocked(decision, "hotspot")
            "led" -> toggleTorch(on)
            else -> ToolResult.Failed("Unknown action '$action'.")
        }
    }

    private fun toggleWifi(on: Boolean, decision: CapabilityDecision, routineMode: Boolean): ToolResult {
        if (!decision.canAttempt) return openPanel(decision, "wifi", routineMode = routineMode)
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ToolResult.Failed("Wi-Fi manager unavailable.")
        return try {
            @Suppress("DEPRECATION")
            wifi.isWifiEnabled = on
            val state = wifi.isWifiEnabled
            if (state == on) ToolResult.Success("Wi-Fi turned ${if (on) "on" else "off"}.", mapOf("wifi" to state))
            else openPanel(decision, "wifi", "Wi-Fi did not change; opening settings.", routineMode)
        } catch (e: Exception) {
            ToolResult.Failed("Couldn't change Wi-Fi: ${e.message}")
        }
    }

    private fun toggleBluetooth(on: Boolean, decision: CapabilityDecision, routineMode: Boolean): ToolResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return openPanel(decision, "bluetooth", "Bluetooth permission is required to change this setting.", routineMode)
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return ToolResult.Failed("No Bluetooth adapter on this device.")
        return try {
            @Suppress("DEPRECATION")
            if (on) adapter.enable() else adapter.disable()
            val state = adapter.isEnabled
            if (state == on) {
                ToolResult.Success("Bluetooth turned ${if (on) "on" else "off"}.", mapOf("bluetooth" to state))
            } else {
                openPanel(decision, "bluetooth", "This Android version blocks app toggling of Bluetooth; opening settings.", routineMode)
            }
        } catch (e: Exception) {
            ToolResult.Failed("Couldn't change Bluetooth: ${e.message}")
        }
    }

    private fun toggleDnd(on: Boolean, @Suppress("UNUSED_PARAMETER") decision: CapabilityDecision): ToolResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return openPanel(
                CapabilityDecision(canAttempt = false, panelAction = SystemCapability.ACTION_NOTIFICATION_POLICY_ACCESS, reason = "Jarvis needs Do Not Disturb access."),
                "dnd"
            )
        }
        return try {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALARMS else NotificationManager.INTERRUPTION_FILTER_ALL)
            val state = nm.currentInterruptionFilter
            val applied = if (on) NotificationManager.INTERRUPTION_FILTER_ALARMS else NotificationManager.INTERRUPTION_FILTER_ALL
            if (state == applied) ToolResult.Success("Do Not Disturb ${if (on) "on" else "off"}.", mapOf("dnd" to state.toString()))
            else ToolResult.Failed("Do Not Disturb didn't change — check notification access for Jarvis.")
        } catch (e: Exception) {
            ToolResult.Failed("Couldn't change DND: ${e.message}")
        }
    }

    private fun toggleTorch(on: Boolean): ToolResult {
        val camera = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult.Failed("Camera service unavailable.")
        return try {
            val camId = camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
                ?: return ToolResult.Failed("No flashlight on this device.")
            camera.setTorchMode(camId, on)
            ToolResult.Success("Torch turned ${if (on) "on" else "off"}.", mapOf("led" to on))
        } catch (e: Exception) {
            ToolResult.Failed("Couldn't control the torch: ${e.message}")
        }
    }

    private fun blocked(decision: CapabilityDecision, what: String): ToolResult =
        openPanel(decision, what)

    private fun openPanel(decision: CapabilityDecision, what: String, message: String? = null, routineMode: Boolean = false): ToolResult {
        val panel = decision.panelAction
        if (routineMode) return ToolResult.Failed("Couldn't change $what automatically. Android blocked the action; no settings screen was opened.")
        val opened = panel != null
        try {
            if (opened) {
                context.startActivity(Intent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            // settings activity refused; still honest
        }
        val reason = decision.reason ?: "This Android version blocks it."
        val lead = message ?: "I couldn't change $what."
        val text = "$lead $reason" + if (opened) " I've opened the settings screen for you." else ""
        return ToolResult.Success(text, mapOf("opened_settings" to opened, "changed" to false))
    }

    private fun deviceState(): ToolResult {
        val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
        val bt = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled }.getOrNull()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dnd = runCatching { nm.isNotificationPolicyAccessGranted }.getOrNull()
        val text = buildString {
            append("Wi-Fi: ${wifi?.let { if (it) "on" else "off" } ?: "unknown"}")
            append(", Bluetooth: ${bt?.let { if (it) "on" else "off" } ?: "unknown"}")
            append(", Do Not Disturb: ${dnd?.let { if (it) "granted" else "not granted" } ?: "unknown"}")
        }
        return ToolResult.Success(text, mapOf(
            "wifi" to (wifi ?: false).toString(),
            "bluetooth" to (bt ?: false).toString(),
            "dnd_access" to (dnd ?: false).toString()
        ))
    }

    private fun adjustBrightness(params: Map<String, String>): ToolResult {
        val target = params["target"] ?: params["level"] ?: params["brightness"] ?: "50%"
        val percent = target.filter { it.isDigit() }.toIntOrNull() ?: 50
        val brightnessValue = ((percent.coerceIn(0, 100) / 100f) * 255).toInt().coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult.Success("Permission to modify system settings required. I've opened the settings screen.", mapOf("opened_settings" to true))
        }

        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
            ToolResult.Success("Screen brightness set to $percent%.", mapOf("brightness" to brightnessValue, "percent" to percent))
        } catch (e: Exception) {
            ToolResult.Failed("Failed to set brightness: ${e.message}")
        }
    }

    private fun toggleAutoRotate(on: Boolean): ToolResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult.Success("Permission to modify system settings required to toggle screen rotation. I've opened the settings screen.", mapOf("opened_settings" to true))
        }

        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            ToolResult.Success("Auto-rotate turned ${if (on) "on" else "off"}.", mapOf("auto_rotate" to on))
        } catch (e: Exception) {
            ToolResult.Failed("Failed to toggle auto-rotate: ${e.message}")
        }
    }

    private fun setRingerMode(target: String): ToolResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Failed("AudioManager not available")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val mode = when (target.lowercase().trim()) {
            "silent", "mute" -> AudioManager.RINGER_MODE_SILENT
            "vibrate", "vib" -> AudioManager.RINGER_MODE_VIBRATE
            "normal", "sound", "ring" -> AudioManager.RINGER_MODE_NORMAL
            else -> return ToolResult.Failed("Unknown ringer mode '$target'. Use silent, vibrate, or normal.")
        }

        if (mode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == false) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return ToolResult.Success("Do Not Disturb permission required for silent mode. Opened settings screen.", mapOf("opened_settings" to true))
        }

        return try {
            am.ringerMode = mode
            val modeName = when (mode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
            ToolResult.Success("Ringer mode set to $modeName.", mapOf("ringer_mode" to modeName))
        } catch (e: Exception) {
            ToolResult.Failed("Failed to change ringer mode: ${e.message}")
        }
    }

    private fun openBatterySaver(): ToolResult {
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult.Success("Opened Battery Saver settings.", mapOf("opened_settings" to true))
    }
}
