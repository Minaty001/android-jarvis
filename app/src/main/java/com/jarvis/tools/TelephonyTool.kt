package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.telecom.CallAndSmsAgent
import com.jarvis.telecom.ContactResolver

class TelephonyTool(
    private val context: Context? = null,
    private val contactResolver: ContactResolver = ContactResolver(context),
    private val callAndSmsAgent: CallAndSmsAgent? = null
) : Tool {

    override val name: String = "TELEPHONY_CONTROL"
    override val description: String =
        "Manages phone calls, SMS drafting/sending, incoming call controls, and reading SMS/OTPs. Actions: call, dial, answer_call, reject_call, caller_info, sms_send, sms_draft, sms_read, search_contact."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = false, riskLevel = RiskLevel.HIGH)

    val metadata = ToolMetadata(
        name = "TELEPHONY_CONTROL",
        description = "Handles phone calls, SMS text messages, call answer/reject, and contact lookups.",
        parameters = emptyList(),
        riskLevel = RiskLevel.HIGH
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "call"
        val rawTarget = params["target"] ?: params["recipient"] ?: params["contact"] ?: params["number"] ?: params["name"] ?: ""
        val message = params["message"] ?: params["body"] ?: params["text"] ?: ""

        val agent = callAndSmsAgent ?: CallAndSmsAgent.instance ?: CallAndSmsAgent(context, contactResolver)

        when (action) {
            "answer_call", "answer", "pickup", "pick_up" -> {
                val answered = agent.answerCall()
                return if (answered) {
                    ToolResult.Success("Call answered successfully.")
                } else {
                    ToolResult.Failed("Could not answer call. Please check call answering permissions or tap manually.")
                }
            }

            "reject_call", "reject", "decline", "end_call", "cut_call", "hang_up" -> {
                val autoSms = message.ifBlank { null }
                val rejected = agent.rejectCall(autoReplySms = autoSms)
                return if (rejected) {
                    val note = if (autoSms != null) " and auto-reply SMS sent: \"$autoSms\"" else ""
                    val verb = if (action in listOf("end_call", "cut_call", "hang_up")) "ended" else "rejected"
                    ToolResult.Success("Call $verb$note.")
                } else {
                    ToolResult.Failed("Could not end/reject call.")
                }
            }

            "speaker_on", "speakerphone_on" -> {
                val success = agent.setSpeakerphone(true)
                return if (success) {
                    ToolResult.Success("Speakerphone enabled.", mapOf("action" to "speaker_on", "speakerphone" to "true"))
                } else {
                    ToolResult.Failed("Could not enable speakerphone.")
                }
            }

            "speaker_off", "speakerphone_off" -> {
                val success = agent.setSpeakerphone(false)
                return if (success) {
                    ToolResult.Success("Speakerphone disabled.", mapOf("action" to "speaker_off", "speakerphone" to "false"))
                } else {
                    ToolResult.Failed("Could not disable speakerphone.")
                }
            }

            "mute_mic", "mute_microphone" -> {
                val success = agent.setMicrophoneMute(true)
                return if (success) {
                    ToolResult.Success("Microphone muted.", mapOf("action" to "mute_mic", "muted" to "true"))
                } else {
                    ToolResult.Failed("Could not mute microphone.")
                }
            }

            "unmute_mic", "unmute_microphone" -> {
                val success = agent.setMicrophoneMute(false)
                return if (success) {
                    ToolResult.Success("Microphone unmuted.", mapOf("action" to "unmute_mic", "muted" to "false"))
                } else {
                    ToolResult.Failed("Could not unmute microphone.")
                }
            }

            "caller_info", "who_calling", "check_call" -> {
                val session = agent.activeCallSession
                return if (session != null) {
                    val state = if (session.isRinging) "ringing" else "active"
                    ToolResult.Success(
                        message = "Incoming call from ${session.contactName} (${session.number}) is $state.",
                        data = mapOf("caller" to session.contactName, "number" to session.number, "state" to state)
                    )
                } else {
                    ToolResult.Success("There are no active or incoming phone calls at the moment.")
                }
            }

            "sms_read", "read_sms", "get_otp", "read_otp" -> {
                val otpOnly = action.contains("otp") || params["otp_only"]?.toBoolean() == true
                val senderFilter = params["sender"] ?: rawTarget.ifBlank { null }
                val summary = agent.formatSmsSummary(limit = 5, senderFilter = senderFilter, otpOnly = otpOnly)
                return ToolResult.Success(
                    message = summary,
                    data = mapOf("otpOnly" to otpOnly, "summary" to summary)
                )
            }

            "call", "dial", "phone" -> {
                if (rawTarget.isBlank()) {
                    return ToolResult.Failed("Please specify a contact name or phone number to call.")
                }

                val contact = contactResolver.resolveContact(rawTarget)
                val targetNumber = contact?.phoneNumber ?: rawTarget.filter { it.isDigit() || it == '+' }

                if (targetNumber.isBlank()) {
                    return ToolResult.Failed("Could not resolve phone number for '$rawTarget'.")
                }

                val displayName = contact?.name ?: targetNumber
                val uri = Uri.parse("tel:$targetNumber")
                val useSpeaker = params["speaker"]?.toBoolean() == true || params["mode"] == "speaker"
                if (useSpeaker) {
                    agent.setSpeakerphone(true)
                }

                val ctx = context ?: return ToolResult.Success(
                    message = "Calling $displayName ($targetNumber)${if (useSpeaker) " on speaker" else ""} [Simulation Mode].",
                    data = mapOf("number" to targetNumber, "contact" to displayName, "speaker" to useSpeaker.toString())
                )

                // Check direct CALL_PHONE permission
                val canDirectCall = ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED && action != "dial"

                val intent = if (canDirectCall) {
                    Intent(Intent.ACTION_CALL, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                } else {
                    Intent(Intent.ACTION_DIAL, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                }

                return try {
                    ctx.startActivity(intent)
                    val mode = if (canDirectCall) "Calling" else "Opened dialer for"
                    ToolResult.Success(
                        message = "$mode $displayName ($targetNumber).",
                        data = mapOf("number" to targetNumber, "contact" to displayName, "direct" to canDirectCall)
                    )
                } catch (t: Throwable) {
                    ToolResult.Failed("Failed to initiate call to $displayName: ${t.message}")
                }
            }

            "sms_send", "sms", "text", "send_sms" -> {
                if (rawTarget.isBlank()) {
                    return ToolResult.Failed("Please specify a contact or phone number for SMS.")
                }
                if (message.isBlank()) {
                    return ToolResult.Failed("Please provide a message body to send.")
                }

                val contact = contactResolver.resolveContact(rawTarget)
                val targetNumber = contact?.phoneNumber ?: rawTarget.filter { it.isDigit() || it == '+' }
                val displayName = contact?.name ?: targetNumber

                if (targetNumber.isBlank()) {
                    return ToolResult.Failed("Could not resolve phone number for '$rawTarget'.")
                }

                val ctx = context ?: return ToolResult.Success(
                    message = "SMS sent to $displayName ($targetNumber): \"$message\" [Simulation Mode]",
                    data = mapOf("number" to targetNumber, "contact" to displayName, "status" to "sent")
                )

                val hasSmsPermission = ctx.checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

                if (hasSmsPermission) {
                    return try {
                        val smsManager = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                        smsManager.sendTextMessage(targetNumber, null, message, null, null)
                        ToolResult.Success(
                            message = "SMS sent to $displayName ($targetNumber): \"$message\"",
                            data = mapOf("number" to targetNumber, "contact" to displayName, "status" to "sent")
                        )
                    } catch (t: Throwable) {
                        launchSmsComposer(targetNumber, message, displayName)
                    }
                } else {
                    return launchSmsComposer(targetNumber, message, displayName)
                }
            }

            "sms_draft", "draft_sms", "compose_sms" -> {
                val contact = if (rawTarget.isNotBlank()) contactResolver.resolveContact(rawTarget) else null
                val targetNumber = contact?.phoneNumber ?: rawTarget.filter { it.isDigit() || it == '+' }
                val displayName = contact?.name ?: targetNumber
                return launchSmsComposer(targetNumber, message, displayName)
            }

            "search_contact", "find_contact", "lookup" -> {
                if (rawTarget.isBlank()) {
                    return ToolResult.Failed("Please specify a contact name or query to search.")
                }
                val matches = contactResolver.searchContacts(rawTarget, limit = 5)
                if (matches.isEmpty()) {
                    return ToolResult.Failed("No contacts found matching '$rawTarget'.")
                }
                val summary = matches.joinToString("\n") { "• ${it.name}: ${it.phoneNumber} (${it.type})" }
                return ToolResult.Success(
                    message = "Found ${matches.size} contact(s):\n$summary",
                    data = mapOf("count" to matches.size, "top_match" to matches.first().name)
                )
            }

            "auto_reply", "set_auto_reply", "auto_reply_mode" -> {
                val modeStr = params["mode"]?.trim()?.lowercase() ?: "driving"
                val template = params["template"] ?: message.ifBlank { null }

                val targetMode = mapOf(
                    "off" to com.jarvis.messaging.AutoReplyMode.OFF, "disable" to com.jarvis.messaging.AutoReplyMode.OFF, "stop" to com.jarvis.messaging.AutoReplyMode.OFF,
                    "driving" to com.jarvis.messaging.AutoReplyMode.DRIVING, "drive" to com.jarvis.messaging.AutoReplyMode.DRIVING,
                    "meeting" to com.jarvis.messaging.AutoReplyMode.MEETING, "meet" to com.jarvis.messaging.AutoReplyMode.MEETING,
                    "busy" to com.jarvis.messaging.AutoReplyMode.BUSY,
                    "custom" to com.jarvis.messaging.AutoReplyMode.CUSTOM,
                    "ai" to com.jarvis.messaging.AutoReplyMode.AI_SMART_REPLY, "smart" to com.jarvis.messaging.AutoReplyMode.AI_SMART_REPLY,
                    "on" to com.jarvis.messaging.AutoReplyMode.DRIVING, "enable" to com.jarvis.messaging.AutoReplyMode.DRIVING
                )[modeStr] ?: com.jarvis.messaging.AutoReplyMode.OFF

                com.jarvis.messaging.AutoReplyPolicyManager.setMode(targetMode, template)

                return if (targetMode == com.jarvis.messaging.AutoReplyMode.OFF) {
                    ToolResult.Success("SMS and Calls auto-reply has been turned off.")
                } else {
                    ToolResult.Success("Auto-reply active in ${targetMode.name} mode for calls, SMS, and WhatsApp.")
                }
            }

            "sim_info", "sim_cards", "dual_sim" -> {
                val routingEngine = com.jarvis.telecom.DualSimRoutingEngine(context)
                val sims = routingEngine.getActiveSimCards()
                if (sims.isEmpty()) {
                    return ToolResult.Success("No multi-SIM cards detected or permission required.", mapOf("sim_count" to 0))
                }
                val desc = sims.joinToString("\n") { "• Slot ${it.simSlotIndex + 1}: ${it.displayName} (${it.carrierName})" }
                return ToolResult.Success("Active SIM Cards:\n$desc", mapOf("sim_count" to sims.size))
            }

            "recommend_sim", "choose_sim" -> {
                val routingEngine = com.jarvis.telecom.DualSimRoutingEngine(context)
                val isWork = params["context"]?.contains("work", ignoreCase = true) == true
                val decision = routingEngine.getRecommendedSim(rawTarget, isWork)
                return ToolResult.Success(
                    "Recommended: ${decision.simDisplayName} (Slot ${decision.recommendedSlot + 1}). ${decision.rationale}",
                    mapOf("slot" to decision.recommendedSlot, "name" to decision.simDisplayName)
                )
            }

            else -> return ToolResult.Failed("Unknown telephony action: '$action'. Supported: call, dial, answer_call, reject_call, caller_info, sms_send, sms_draft, sms_read, auto_reply, search_contact, sim_info, recommend_sim.")
        }
    }

    private fun launchSmsComposer(targetNumber: String, message: String, displayName: String): ToolResult {
        val ctx = context ?: return ToolResult.Success(
            message = "Opened SMS composer for $displayName: \"$message\" [Simulation Mode]",
            data = mapOf("number" to targetNumber, "contact" to displayName, "status" to "drafted")
        )

        return try {
            val uri = if (targetNumber.isNotBlank()) Uri.parse("smsto:$targetNumber") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("sms_body", message)
            }
            ctx.startActivity(intent)
            ToolResult.Success(
                message = "Opened SMS composer for ${if (displayName.isNotBlank()) displayName else "new message"}.",
                data = mapOf("number" to targetNumber, "contact" to displayName, "status" to "drafted")
            )
        } catch (t: Throwable) {
            ToolResult.Failed("Failed to open SMS composer: ${t.message}")
        }
    }
}
