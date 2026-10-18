package com.jarvis.tools

import android.content.Context
import com.jarvis.sound.AmbientSoundMonitorEngine
import com.jarvis.sound.SoundAlertDispatcher
import com.jarvis.sound.SoundCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SoundDetectorTool
 *
 * Exposes ambient sound detection and safety alerts to the Jarvis assistant runtime.
 * Actions:
 * - start: Starts monitoring with specified or all categories in battery-saver duty cycle mode.
 * - stop: Stops monitoring and releases wake locks.
 * - status: Reports current monitoring state, active categories, and metrics.
 * - simulate: Emits a simulated sound detection event for testing or demonstration.
 */
class SoundDetectorTool(private val context: Context? = null) : Tool {

    override val name: String = "SOUND_DETECTOR"
    override val description: String =
        "Monitors ambient sounds (baby crying, smoke alarm, doorbell, glass break, car honk) and dispatches safety alerts. Actions: 'start', 'stop', 'status', 'simulate'."

    val metadata = com.jarvis.foundation.ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            com.jarvis.foundation.ParameterSchema("action", "string", "Action: start, stop, status, simulate", required = false),
            com.jarvis.foundation.ParameterSchema("type", "string", "Sound type: all, baby_crying, smoke_alarm, doorbell, glass_break, car_honk", required = false)
        ),
        riskLevel = com.jarvis.foundation.RiskLevel.LOW
    )

    private var emergencyContact: String? = null

    private val detectionListener = com.jarvis.sound.SoundDetectionListener { event ->
        CoroutineScope(Dispatchers.Default).launch {
            val contact = emergencyContact ?: "112"
            SoundAlertDispatcher.dispatchAlert(context, event, emergencyContact = contact)
        }
    }

    init {
        AmbientSoundMonitorEngine.addListener(detectionListener)
    }

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "start"
        val typeParam = params["type"]?.trim()?.lowercase() ?: "all"
        val contactParam = params["contact"] ?: params["emergency_contact"] ?: params["number"]
        if (!contactParam.isNullOrBlank()) {
            emergencyContact = contactParam
        }

        return when (action) {
            "start", "enable", "on", "listen" -> {
                AmbientSoundMonitorEngine.addListener(detectionListener)
                val categories = parseCategories(typeParam)
                val audioStream = try {
                    com.jarvis.service.JarvisForegroundService.instance?.voiceEngine?.continuousAudioStream
                } catch (_: Throwable) {
                    null
                }
                AmbientSoundMonitorEngine.startMonitoring(
                    context = context,
                    categories = categories,
                    dutyCycleMode = AmbientSoundMonitorEngine.DutyCycleMode.BATTERY_SAVER,
                    audioStream = audioStream
                )
                val catNames = categories.joinToString(", ") { it.name }
                ToolResult(
                    success = true,
                    message = "Ambient sound monitoring started for: $catNames in battery-saver mode.",
                    data = mapOf(
                        "is_monitoring" to "true",
                        "categories" to catNames
                    )
                )
            }
            "stop", "disable", "off" -> {
                AmbientSoundMonitorEngine.stopMonitoring()
                ToolResult(
                    success = true,
                    message = "Ambient sound monitoring stopped.",
                    data = mapOf("is_monitoring" to "false")
                )
            }
            "status", "get_status" -> {
                val isMonitoring = AmbientSoundMonitorEngine.isMonitoring()
                val activeCats = AmbientSoundMonitorEngine.getActiveCategories().joinToString(", ") { it.name }
                ToolResult(
                    success = true,
                    message = if (isMonitoring) "Ambient sound monitoring is ACTIVE for: $activeCats" else "Ambient sound monitoring is INACTIVE",
                    data = mapOf(
                        "is_monitoring" to isMonitoring.toString(),
                        "categories" to activeCats
                    )
                )
            }
            "simulate" -> {
                val category = parseCategory(typeParam)
                if (category == SoundCategory.NONE) {
                    ToolResult(
                        success = false,
                        message = "Invalid or unclassified sound category for simulation: $typeParam"
                    )
                } else {
                    AmbientSoundMonitorEngine.simulateDetection(category, 0.95f)
                    val announcement = SoundAlertDispatcher.formatAlertAnnouncement(category)
                    ToolResult(
                        success = true,
                        message = "Simulated detection for $category: $announcement",
                        data = mapOf("simulated_category" to category.name)
                    )
                }
            }
            else -> {
                ToolResult(
                    success = false,
                    message = "Unknown action '$action'. Supported actions: start, stop, status, simulate."
                )
            }
        }
    }

    private fun parseCategory(type: String): SoundCategory {
        return when {
            type.contains("smoke") || type.contains("fire") || type.contains("alarm") -> SoundCategory.SMOKE_ALARM
            type.contains("baby") || type.contains("cry") || type.contains("infant") -> SoundCategory.BABY_CRYING
            type.contains("door") || type.contains("bell") || type.contains("chime") -> SoundCategory.DOORBELL
            type.contains("glass") || type.contains("shatter") || type.contains("break") -> SoundCategory.GLASS_BREAK
            type.contains("honk") || type.contains("car") || type.contains("horn") -> SoundCategory.CAR_HONK
            else -> SoundCategory.NONE
        }
    }

    private fun parseCategories(type: String): Set<SoundCategory> {
        if (type == "all" || type.isBlank()) {
            return SoundCategory.values().filter { it != SoundCategory.NONE }.toSet()
        }
        val single = parseCategory(type)
        return if (single != SoundCategory.NONE) setOf(single) else SoundCategory.values().filter { it != SoundCategory.NONE }.toSet()
    }
}
