package com.jarvis.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.device.HardwareProfileManager

/**
 * High-performance ADB Bridge Protocol engine.
 * Handles synchronous ordered broadcast intents (PING, EXECUTE_TOOL, EXECUTE_COMMAND)
 * returning immediate resultCode and structured resultData payloads to ADB shell callers.
 */
object AdbBridgeEngine {
    private const val TAG = "AdbBridgeEngine"

    const val ACTION_PING = "com.jarvis.action.PING"
    const val ACTION_EXECUTE_TOOL = "com.jarvis.action.EXECUTE_TOOL"
    const val ACTION_EXECUTE_COMMAND = "com.jarvis.action.EXECUTE_COMMAND"
    const val EXTRA_TOOL = "tool"
    const val EXTRA_PARAMS = "params"

    fun parseParams(rawParams: String?): Map<String, String> {
        if (rawParams.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = rawParams.split(Regex("[,;&]"))
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val delimiter = if (trimmed.contains('=')) '=' else if (trimmed.contains(':')) ':' else continue
            val parts = trimmed.split(delimiter, limit = 2)
            if (parts.size == 2) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
    }

    fun formatPingResponse(context: Context?): String {
        val serviceRunning = JarvisForegroundService.instance != null
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val tier = if (context != null) HardwareProfileManager.getProfile(context).tier.name else "UNKNOWN"
        return "PONG:service=${if (serviceRunning) "RUNNING" else "STOPPED"},cores=$cores,tier=$tier"
    }

    fun formatToolExecutionResult(success: Boolean, message: String, data: Map<String, Any?> = emptyMap()): String {
        val status = if (success) "STATUS:SUCCESS" else "STATUS:FAILED"
        val payload = if (data.isNotEmpty()) {
            val formattedData = data.entries.joinToString(",") { "${it.key}=${it.value}" }
            "$status|$message|$formattedData"
        } else {
            "$status|$message"
        }
        return payload
    }

    fun handleBroadcast(receiver: BroadcastReceiver, context: Context, intent: Intent): Boolean {
        val action = intent.action ?: return false
        val isOrdered = receiver.isOrderedBroadcast

        when (action) {
            ACTION_PING -> {
                val pingResponse = formatPingResponse(context)
                Log.i(TAG, "PING probe handled: $pingResponse")
                if (isOrdered) {
                    receiver.resultCode = Activity.RESULT_OK
                    receiver.resultData = pingResponse
                }
                return true
            }

            ACTION_EXECUTE_TOOL -> {
                val toolName = intent.getStringExtra(EXTRA_TOOL) ?: "unknown"
                val rawParams = intent.getStringExtra(EXTRA_PARAMS)
                val params = parseParams(rawParams)
                Log.i(TAG, "Executing tool '$toolName' with params=$params")

                val service = JarvisForegroundService.instance
                if (service == null) {
                    val result = formatToolExecutionResult(false, "JarvisForegroundService not running")
                    if (isOrdered) {
                        receiver.resultCode = Activity.RESULT_CANCELED
                        receiver.resultData = result
                    }
                    return true
                }

                // Handle built-in wake diagnostic probe
                if (toolName == "wake_diagnostics") {
                    val engine = service.voiceEngine
                    val wakeEngine = engine?.wakeEngine
                    val audioStream = engine?.continuousAudioStream
                    val diag = wakeEngine?.getDiagnostics()
                    val config = wakeEngine?.getConfig()
                    val data = mapOf(
                        "mode" to (diag?.detectorMode?.name ?: "UNKNOWN"),
                        "modelAvailable" to (diag?.modelAvailable ?: false),
                        "latestScore" to "%.3f".format(diag?.latestScore ?: 0f),
                        "threshold" to "%.2f".format(diag?.threshold ?: 0f),
                        "audioRms" to "%.5f".format(audioStream?.getLatestRms() ?: diag?.audioRms ?: 0.0),
                        "energyFloor" to "%.5f".format(config?.minEnergyRms ?: 0.0),
                        "silenceDecayLimit" to (config?.silenceFramesBeforeReset ?: 0),
                        "isRecording" to (audioStream?.isRecording() ?: false),
                        "isHealthy" to (audioStream?.isHealthy() ?: false),
                        "voiceState" to (engine?.conversationManager?.currentStateValue?.name ?: "UNKNOWN")
                    )
                    val result = formatToolExecutionResult(true, "Wake word telemetry captured", data)
                    Log.i(TAG, "wake_diagnostics result: $result")
                    if (isOrdered) {
                        receiver.resultCode = Activity.RESULT_OK
                        receiver.resultData = result
                    }
                    return true
                }

                // Handle simulated wake trigger for ADB end-to-end testing
                if (toolName == "trigger_wake") {
                    val phrase = params["phrase"] ?: "Hey Jarvis"
                    service.voiceEngine?.onWakeDetectedDirect(phrase)
                    val result = formatToolExecutionResult(true, "Simulated wake phrase '$phrase' dispatched")
                    Log.i(TAG, "trigger_wake result: $result")
                    if (isOrdered) {
                        receiver.resultCode = Activity.RESULT_OK
                        receiver.resultData = result
                    }
                    return true
                }

                // Handle direct PCM audio frame injection for end-to-end acoustic testing
                if (toolName == "inject_audio_frame") {
                    val pcmType = params["type"] ?: "synthetic"
                    val samplesCount = (params["samples"]?.toIntOrNull() ?: 1280).coerceIn(640, 16000)
                    val amplitude = params["amplitude"]?.toIntOrNull() ?: 1000
                    val base64Pcm = params["base64"]

                    val pcmData: ShortArray = if (!base64Pcm.isNullOrBlank()) {
                        try {
                            val bytes = android.util.Base64.decode(base64Pcm, android.util.Base64.DEFAULT)
                            val shorts = ShortArray(bytes.size / 2)
                            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                            shorts
                        } catch (e: Exception) {
                            ShortArray(samplesCount) { amplitude.toShort() }
                        }
                    } else if (pcmType == "sine") {
                        val freq = params["freq"]?.toDoubleOrNull() ?: 440.0
                        ShortArray(samplesCount) { i ->
                            (amplitude * kotlin.math.sin(2 * Math.PI * freq * i / 16000)).toInt().toShort()
                        }
                    } else {
                        ShortArray(samplesCount) { amplitude.toShort() }
                    }

                    val wakeEngine = service.voiceEngine?.wakeEngine
                    val accepted = wakeEngine?.acceptAudio(pcmData) ?: false
                    val latestScore = wakeEngine?.getDiagnostics()?.latestScore ?: 0f
                    val result = formatToolExecutionResult(
                        true,
                        "Injected ${pcmData.size} PCM samples (amplitude=$amplitude)",
                        mapOf(
                            "accepted" to accepted,
                            "samples" to pcmData.size,
                            "latestScore" to "%.3f".format(latestScore)
                        )
                    )
                    Log.i(TAG, "inject_audio_frame result: $result")
                    if (isOrdered) {
                        receiver.resultCode = Activity.RESULT_OK
                        receiver.resultData = result
                    }
                    return true
                }

                // Generic tool execution
                val result = formatToolExecutionResult(true, "Tool '$toolName' accepted")
                if (isOrdered) {
                    receiver.resultCode = Activity.RESULT_OK
                    receiver.resultData = result
                }
                return true
            }

            ACTION_EXECUTE_COMMAND, "com.jarvis.intent.ACTION_EXECUTE_COMMAND" -> {
                val command = intent.getStringExtra(CommandReceiver.EXTRA_COMMAND)
                    ?: intent.getStringExtra("query")
                    ?: intent.getStringExtra("command_text")
                    ?: return false

                Log.i(TAG, "CommandReceiver received command: '$command'")
                val service = JarvisForegroundService.instance
                if (service != null) {
                    service.executeTextCommand(command)
                } else {
                    val serviceIntent = Intent(context, JarvisForegroundService::class.java).apply {
                        this.action = JarvisForegroundService.ACTION_EXECUTE
                        putExtra(CommandReceiver.EXTRA_COMMAND, command)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                if (isOrdered) {
                    receiver.resultCode = Activity.RESULT_OK
                    receiver.resultData = "ACCEPTED: $command"
                }
                return true
            }
        }
        return false
    }
}
