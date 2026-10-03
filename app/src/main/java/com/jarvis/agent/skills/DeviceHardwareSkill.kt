package com.jarvis.agent.skills

import com.jarvis.agent.AgentAction

class DeviceHardwareSkill : Skill {
    override val id: String = "device_hardware_controls"
    override val name: String = "Device Hardware & Switchboard Control Skill"
    override val description: String =
        "Controls device hardware settings such as flashlight/torch, Wi-Fi, Bluetooth, media volume, screen brightness, and ringer mode."

    override val triggers: List<String> = listOf(
        // English
        "flashlight on", "flashlight off", "torch on", "torch off", "turn on flashlight", "turn off flashlight", "toggle torch",
        "wifi on", "wifi off", "turn on wifi", "turn off wifi", "enable wifi", "disable wifi",
        "bluetooth on", "bluetooth off", "turn on bluetooth", "turn off bluetooth", "enable bluetooth", "disable bluetooth",
        "set volume", "volume to", "volume up", "volume down", "mute volume", "unmute volume",
        "set brightness", "brightness to", "screen brightness", "brightness full", "brightness low",
        "silent mode", "vibrate mode", "normal mode", "ringer mode", "phone silent",
        // Hindi / Hinglish
        "torch chalu karo", "torch band karo", "flashlight chalu karo", "flashlight band karo", "torch lagao",
        "wifi chalu karo", "wifi band karo", "wifi on karo", "wifi off karo",
        "bluetooth chalu karo", "bluetooth band karo", "bluetooth on karo", "bluetooth off karo",
        "volume badhao", "volume kam karo", "volume 50 percent", "volume full karo",
        "brightness badhao", "brightness kam karo", "brightness full",
        "silent karo", "vibrate karo", "ringer normal karo"
    )

    override suspend fun canHandle(goal: String, context: SkillContext): Boolean {
        val lower = goal.lowercase().trim()
        return triggers.any { lower.contains(it) } ||
                ((lower.contains("torch") || lower.contains("flashlight") || lower.contains("wifi") ||
                  lower.contains("bluetooth") || lower.contains("volume") || lower.contains("brightness") ||
                  lower.contains("silent") || lower.contains("vibrate")) &&
                 (lower.contains("on") || lower.contains("off") || lower.contains("set") || lower.contains("chalu") ||
                  lower.contains("band") || lower.contains("karo") || lower.contains("badhao") || lower.contains("kam")))
    }

    override suspend fun execute(goal: String, context: SkillContext): SkillResult {
        val lower = goal.lowercase().trim()

        val action = when {
            // Flashlight / Torch
            lower.contains("torch") || lower.contains("flashlight") -> {
                val isOn = lower.contains("on") || lower.contains("chalu") || lower.contains("lagao") || lower.contains("enable") || lower.contains("start")
                AgentAction(type = "FLASHLIGHT", params = mapOf("mode" to if (isOn) "on" else "off"))
            }

            // Wi-Fi
            lower.contains("wifi") || lower.contains("wi-fi") -> {
                val isOn = lower.contains("on") || lower.contains("chalu") || lower.contains("enable") || lower.contains("start")
                AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "wifi", "target" to if (isOn) "on" else "off"))
            }

            // Bluetooth
            lower.contains("bluetooth") || lower.contains("bt") -> {
                val isOn = lower.contains("on") || lower.contains("chalu") || lower.contains("enable") || lower.contains("start")
                AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "bt", "target" to if (isOn) "on" else "off"))
            }

            // Brightness
            lower.contains("brightness") -> {
                val num = Regex("""\b(\d{1,3})%?\b""").find(lower)?.groupValues?.get(1) ?: "70"
                AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "brightness", "value" to num))
            }

            // Ringer Mode (Silent / Vibrate / Normal)
            lower.contains("silent") -> AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "ringer", "target" to "silent"))
            lower.contains("vibrate") -> AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "ringer", "target" to "vibrate"))
            lower.contains("normal") || lower.contains("ring") -> AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "ringer", "target" to "normal"))

            // Volume
            lower.contains("volume") -> {
                val num = Regex("""\b(\d{1,3})%?\b""").find(lower)?.groupValues?.get(1) ?: "50"
                AgentAction(type = "DEVICE_SETTINGS", params = mapOf("action" to "set_volume", "volume_percent" to num))
            }

            else -> AgentAction(type = "SYSTEM_SWITCHBOARD", params = mapOf("action" to "state"))
        }

        return SkillResult(
            handled = true,
            proposedAction = action,
            explanation = "Handling hardware switchboard action: $goal"
        )
    }
}
