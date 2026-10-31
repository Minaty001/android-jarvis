package com.jarvis.ui.screens

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jarvis.R
import com.jarvis.service.JarvisForegroundService
import com.jarvis.ui.JarvisWaveformView
import com.jarvis.ui.components.Ui
import com.jarvis.ui.model.AppSettings
import com.jarvis.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern, human-crafted conversational dashboard for J.A.R.V.I.S.
 * Features:
 * - Minimalist top HUD with clean status indicator and direct settings access
 * - Compact interactive voice status & live audio waveform card (~68dp)
 * - True multi-turn scrollable conversation feed with user and assistant message bubbles
 * - Context-aware active task execution card
 * - Curated quick-action prompt chips with vector icons
 * - Fixed bottom command capsule with dynamic vector actions (mic, send, stop)
 */
class DashboardScreen(
    context: Context,
    private val owner: LifecycleOwner,
    private val settings: () -> AppSettings,
    private val onOpenSettings: () -> Unit,
    private val onOpenSkills: (() -> Unit)? = null,
    private val onOpenMemory: (() -> Unit)? = null
) : LinearLayout(context) {

    private val statusText = TextView(context)
    private val activityDetail = TextView(context)
    private val serviceBadge = Ui.hudBadge(context, "● READY", Ui.SUCCESS)
    private val masterStopBtn = TextView(context)
    private val waveform = JarvisWaveformView(context)

    // Conversation feed
    private val chatScroll = ScrollView(context)
    private val chatMessagesContainer = LinearLayout(context)

    // Task Execution Card
    private val protocolCard: View
    private val protocolBadge = Ui.hudBadge(context, "STANDBY", Ui.PRIMARY)
    private val protocolGoalText = TextView(context)
    private val protocolStepText = TextView(context)

    // Input Bar
    private val input = EditText(context)
    private val smartActionBtn = ImageView(context)

    private var currentVoiceState: VoiceState = VoiceState.WAITING_FOR_WAKE
    private var commandJob: Job? = null
    private var statusJob: Job? = null
    private var boundService: JarvisForegroundService? = null

    private var lastRecordedResponse: String = ""
    private var lastRecordedTranscript: String = ""

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private fun String.safeDisplay(): String = replace(Regex("[\\r\\n\\t]"), " ").trim()
    private fun currentTimeString(): String = timeFormat.format(Date())

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.BG)
        setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 10))

        // 1. Top HUD Header
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(context, 8)
            }
        }

        val brandCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "JARVIS"
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.TEXT)
                letterSpacing = 0.06f
            })
            addView(TextView(context).apply {
                text = "Personal AI Assistant"
                textSize = 11f
                setTextColor(Ui.MUTED)
                letterSpacing = 0.02f
            })
        }
        header.addView(brandCol)

        serviceBadge.apply {
            setOnClickListener {
                val state = boundService?.voiceEngine?.status?.value?.voiceState
                if (state == VoiceState.IDLE || boundService == null) {
                    startEngine()
                    Toast.makeText(context, "Starting Jarvis service…", Toast.LENGTH_SHORT).show()
                } else {
                    stopEngine()
                    Toast.makeText(context, "Jarvis service halted", Toast.LENGTH_SHORT).show()
                }
            }
        }
        header.addView(serviceBadge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        masterStopBtn.apply {
            text = "STOP"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            setTextColor(Ui.ERROR)
            background = Ui.rounded(context, Ui.SURFACE_2, 0x44EF4444.toInt(), 6)
            setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(context, 6)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val state = boundService?.voiceEngine?.status?.value?.voiceState
                if (state == VoiceState.IDLE || boundService == null) {
                    startEngine()
                    Toast.makeText(context, "Jarvis service started", Toast.LENGTH_SHORT).show()
                } else {
                    stopEngine()
                    Toast.makeText(context, "Jarvis background service stopped", Toast.LENGTH_SHORT).show()
                }
            }
        }
        header.addView(masterStopBtn)

        val settingsBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_nav_settings)
            imageTintList = ColorStateList.valueOf(Ui.MUTED)
            val btnSize = Ui.dp(context, 34)
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                marginStart = Ui.dp(context, 8)
            }
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 8)
            setPadding(Ui.dp(context, 7), Ui.dp(context, 7), Ui.dp(context, 7), Ui.dp(context, 7))
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpenSettings() }
            contentDescription = "Open settings"
        }
        header.addView(settingsBtn)
        addView(header)

        // 2. Compact Voice & Waveform Status Card (~68dp)
        val voiceCard = Ui.glassCard(context, Ui.BORDER).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(context, 8)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { triggerListen() }
        }

        val voiceInner = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8))
        }

        val statusRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusText.apply {
            text = "LISTENING FOR ${settings().wakeWord.uppercase()}…"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            setTextColor(Ui.PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        statusRow.addView(statusText)

        activityDetail.apply {
            text = "Tap to speak"
            textSize = 10f
            setTextColor(Ui.MUTED)
        }
        statusRow.addView(activityDetail)
        voiceInner.addView(statusRow)

        waveform.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 34)).apply {
            topMargin = Ui.dp(context, 4)
        }
        voiceInner.addView(waveform)
        voiceCard.addView(voiceInner)
        addView(voiceCard)

        // 3. Context-Aware Active Task / Protocol Card (Hidden when idle)
        protocolCard = Ui.glassCard(context, Ui.BORDER_GLOW).apply {
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(context, 8)
            }
        }
        val protocolInner = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8))
        }

        val protoHead = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val protoTitle = TextView(context).apply {
            text = "ACTIVE TASK"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setTextColor(Ui.PRIMARY)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        protoHead.addView(protoTitle)
        protoHead.addView(protocolBadge)

        val cancelBtn = TextView(context).apply {
            text = "CANCEL"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.ERROR)
            background = Ui.rounded(context, Ui.SURFACE_2, 0x44EF4444.toInt(), 6)
            setPadding(Ui.dp(context, 8), Ui.dp(context, 3), Ui.dp(context, 8), Ui.dp(context, 3))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(context, 6)
            }
            setOnClickListener { cancelActiveTask() }
        }
        protoHead.addView(cancelBtn)
        protocolInner.addView(protoHead)

        protocolGoalText.apply {
            text = "Analyzing context..."
            textSize = 11.5f
            setTextColor(Ui.TEXT)
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 2))
        }
        protocolStepText.apply {
            text = "Executing autonomous action…"
            textSize = 10.5f
            setTextColor(Ui.MUTED)
        }
        protocolInner.addView(protocolGoalText)
        protocolInner.addView(protocolStepText)
        protocolCard.addView(protocolInner)
        addView(protocolCard)

        // 4. Conversation Feed (Scrollable message thread)
        chatScroll.apply {
            isFillViewport = true
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = Ui.dp(context, 8)
            }
        }

        chatMessagesContainer.apply {
            orientation = VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4))
        }
        chatScroll.addView(chatMessagesContainer)
        addView(chatScroll)

        // Initial assistant greeting
        addAssistantMessage("Hello! I am Jarvis, your personal AI assistant. How can I help you today?", currentTimeString())

        // 5. Quick Suggestion Prompt Chips
        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(context, 8)
            }
        }
        val chipsRow = LinearLayout(context).apply { orientation = HORIZONTAL }

        chipsRow.addView(Ui.vectorChip(context, "Play Music", R.drawable.ic_send) {
            input.setText("play music")
            sendCommand()
        })
        chipsRow.addView(Ui.vectorChip(context, "Send Message", R.drawable.ic_nav_chat) {
            input.setText("send whatsapp message to ")
            input.setSelection(input.text.length)
            input.requestFocus()
        })
        chipsRow.addView(Ui.vectorChip(context, "Search Web", R.drawable.ic_send) {
            input.setText("search ")
            input.setSelection(input.text.length)
            input.requestFocus()
        })
        chipsRow.addView(Ui.vectorChip(context, "Skills Hub", R.drawable.ic_nav_skills) {
            onOpenSkills?.invoke()
        })
        chipsRow.addView(Ui.vectorChip(context, "Floating Orb", R.drawable.ic_nav_chat) {
            toggleFloatingOverlay(context)
        })

        chipsScroll.addView(chipsRow)
        addView(chipsScroll)

        // 6. Modern Command Input Bar Capsule
        val composer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 26)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 52))
        }

        input.apply {
            hint = "Ask Jarvis anything…"
            textSize = 14f
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        composer.addView(input)

        smartActionBtn.apply {
            val iconBtnSize = Ui.dp(context, 40)
            layoutParams = LayoutParams(iconBtnSize, iconBtnSize)
            setPadding(Ui.dp(context, 9), Ui.dp(context, 9), Ui.dp(context, 9), Ui.dp(context, 9))
            isClickable = true
            isFocusable = true
        }
        updateSmartActionButton()

        smartActionBtn.setOnClickListener {
            val isTaskActive = currentVoiceState in setOf(
                VoiceState.THINKING, VoiceState.EXECUTING, VoiceState.VERIFYING, VoiceState.RESEARCHING
            )
            val hasText = !input.text.isNullOrBlank()

            if (isTaskActive) {
                cancelActiveTask()
            } else if (hasText) {
                sendCommand()
            } else {
                triggerListen()
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSmartActionButton()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendCommand()
                true
            } else {
                false
            }
        }

        composer.addView(smartActionBtn)
        addView(composer)

        waveform.setState(VoiceState.WAITING_FOR_WAKE)
    }

    private fun addUserMessage(text: String, time: String) {
        val bubbleContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(context, 4)
                bottomMargin = Ui.dp(context, 4)
            }
        }

        val bubble = LinearLayout(context).apply {
            orientation = VERTICAL
            background = Ui.rounded(context, Ui.PRIMARY, Color.TRANSPARENT, 14)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8))
            val maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                marginStart = Ui.dp(context, 48)
            }
        }

        val msgText = TextView(context).apply {
            this.text = text
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.15f)
        }
        val timeText = TextView(context).apply {
            this.text = time
            textSize = 9.5f
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.END
            setPadding(0, Ui.dp(context, 2), 0, 0)
        }

        bubble.addView(msgText)
        bubble.addView(timeText)
        bubbleContainer.addView(bubble)
        chatMessagesContainer.addView(bubbleContainer)
        scrollToBottom()
    }

    private fun addAssistantMessage(text: String, time: String, executionBadge: String? = null) {
        val bubbleContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.START
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(context, 4)
                bottomMargin = Ui.dp(context, 4)
            }
        }

        val bubble = LinearLayout(context).apply {
            orientation = VERTICAL
            background = Ui.rounded(context, Ui.SURFACE_2, Ui.BORDER, 14)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 10))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START
                marginEnd = Ui.dp(context, 36)
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(context, 3)
            }
        }

        val nameTag = TextView(context).apply {
            this.text = "Jarvis"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.PRIMARY)
        }
        headerRow.addView(nameTag)

        val timeTag = TextView(context).apply {
            this.text = " • $time"
            textSize = 9.5f
            setTextColor(Ui.MUTED)
        }
        headerRow.addView(timeTag)

        if (!executionBadge.isNullOrBlank()) {
            val badge = TextView(context).apply {
                this.text = "  [$executionBadge]"
                textSize = 9f
                setTextColor(Ui.SUCCESS)
            }
            headerRow.addView(badge)
        }
        bubble.addView(headerRow)

        val msgText = TextView(context).apply {
            this.text = text
            textSize = 13.5f
            setTextColor(Ui.TEXT)
            setLineSpacing(0f, 1.18f)
        }
        bubble.addView(msgText)

        bubbleContainer.addView(bubble)
        chatMessagesContainer.addView(bubbleContainer)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateSmartActionButton() {
        val isTaskActive = currentVoiceState in setOf(
            VoiceState.THINKING, VoiceState.EXECUTING, VoiceState.VERIFYING, VoiceState.RESEARCHING
        )
        val hasText = !input.text.isNullOrBlank()

        if (isTaskActive) {
            smartActionBtn.setImageResource(R.drawable.ic_stop)
            smartActionBtn.imageTintList = ColorStateList.valueOf(Ui.ERROR)
            smartActionBtn.background = Ui.rounded(context, 0x33EF4444.toInt(), Ui.ERROR, 20)
            smartActionBtn.contentDescription = "Stop active task"
        } else if (hasText) {
            smartActionBtn.setImageResource(R.drawable.ic_send)
            smartActionBtn.imageTintList = ColorStateList.valueOf(Color.WHITE)
            smartActionBtn.background = Ui.rounded(context, Ui.PRIMARY, Color.TRANSPARENT, 20)
            smartActionBtn.contentDescription = "Send command"
        } else {
            smartActionBtn.setImageResource(R.drawable.ic_mic)
            smartActionBtn.imageTintList = ColorStateList.valueOf(Ui.PRIMARY)
            smartActionBtn.background = Ui.rounded(context, Ui.SURFACE, Ui.BORDER, 20)
            smartActionBtn.contentDescription = "Voice input"
        }
    }

    private fun triggerListen() {
        val engine = boundService?.voiceEngine
        val state = engine?.status?.value?.voiceState
        if (state == VoiceState.SPEAKING || state == VoiceState.THINKING || state == VoiceState.RESEARCHING || state == VoiceState.EXECUTING) {
            if (!engine.bargeIn()) {
                engine.triggerManualListening()
            }
        } else {
            if (boundService == null || state == VoiceState.IDLE || state == null) {
                startEngine()
            }
            engine?.triggerManualListening()
        }
    }

    private fun sendCommand() {
        val command = input.text?.toString()?.trim().orEmpty()
        if (command.isBlank()) return
        input.text?.clear()
        updateSmartActionButton()

        addUserMessage(command.safeDisplay(), currentTimeString())

        statusText.text = "THINKING…"
        activityDetail.text = "Processing request"
        protocolGoalText.text = "Request: \"${command.take(32)}\""
        protocolStepText.text = "Planning execution steps…"
        protocolBadge.apply {
            text = "● PLANNING"
            setTextColor(Ui.PRIMARY)
        }
        protocolCard.visibility = View.VISIBLE

        waveform.setState(VoiceState.THINKING)

        commandJob?.cancel()
        commandJob = owner.lifecycleScope.launch {
            try {
                val engine = boundService?.voiceEngine
                if (engine == null) {
                    addAssistantMessage("Voice service is unavailable.", currentTimeString())
                    statusText.text = "SERVICE OFFLINE"
                    serviceBadge.apply {
                        text = "● OFFLINE"
                        setTextColor(Ui.ERROR)
                    }
                    waveform.setState(VoiceState.ERROR)
                    protocolCard.visibility = View.GONE
                    return@launch
                }
                val ok = engine.processTextCommand(command)
                if (!ok) {
                    addAssistantMessage("Busy processing previous command.", currentTimeString())
                    statusText.text = "BUSY"
                    waveform.setState(engine.status.value.voiceState)
                }
            } catch (t: Throwable) {
                Log.e("Dashboard", "Command failed", t)
                addAssistantMessage("An error occurred while executing command.", currentTimeString())
                statusText.text = "ERROR"
                waveform.setState(VoiceState.ERROR)
            }
        }
    }

    private fun cancelActiveTask() {
        boundService?.voiceEngine?.assistantRuntime?.cancelCurrentTask()
        statusText.text = "TASK CANCELLED"
        activityDetail.text = "Cancelled by user"
        protocolStepText.text = "Cancelled by user"
        protocolBadge.apply {
            text = "● CANCELLED"
            setTextColor(Ui.ERROR)
        }
        waveform.setState(VoiceState.CANCELLED)
        currentVoiceState = VoiceState.CANCELLED
        updateSmartActionButton()
        addAssistantMessage("Task was cancelled.", currentTimeString())
    }

    private fun startEngine() {
        JarvisForegroundService.startService(context)
        boundService?.startEngine()
        statusText.text = "STARTING ENGINE…"
        serviceBadge.apply {
            text = "● READY"
            setTextColor(Ui.SUCCESS)
        }
        masterStopBtn.apply {
            text = "STOP"
            setTextColor(Ui.ERROR)
            background = Ui.rounded(context, Ui.SURFACE_2, 0x44EF4444.toInt(), 6)
        }
        activityDetail.text = "Listening for wake word"
        waveform.setState(VoiceState.WAITING_FOR_WAKE)
        currentVoiceState = VoiceState.WAITING_FOR_WAKE
        updateSmartActionButton()
    }

    private fun stopEngine() {
        owner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { JarvisForegroundService.stopService(context) }
        statusText.text = "ENGINE HALTED"
        serviceBadge.apply {
            text = "● HALTED"
            setTextColor(Ui.ERROR)
        }
        masterStopBtn.apply {
            text = "START"
            setTextColor(Ui.SUCCESS)
            background = Ui.rounded(context, Ui.SURFACE_2, 0x4410B981.toInt(), 6)
        }
        activityDetail.text = "Tap START to resume"
        waveform.setState(VoiceState.IDLE)
        currentVoiceState = VoiceState.IDLE
        protocolCard.visibility = View.GONE
        updateSmartActionButton()
    }

    fun bindService(service: JarvisForegroundService) {
        boundService = service
        serviceBadge.apply {
            text = "● READY"
            setTextColor(Ui.SUCCESS)
        }
        service.voiceEngine?.let { engine ->
            val wakePhrase = engine.wakeEngine.availableWakeWords()
                .joinToString(" / ")
                .ifBlank { settings().wakeWord }
            statusJob?.cancel()
            statusJob = owner.lifecycleScope.launch {
                engine.status.collect { s ->
                    currentVoiceState = s.voiceState
                    updateSmartActionButton()

                    if (s.voiceState == VoiceState.IDLE) {
                        serviceBadge.apply {
                            text = "● HALTED"
                            setTextColor(Ui.ERROR)
                        }
                        masterStopBtn.apply {
                            text = "START"
                            setTextColor(Ui.SUCCESS)
                            background = Ui.rounded(context, Ui.SURFACE_2, 0x4410B981.toInt(), 6)
                        }
                    } else {
                        serviceBadge.apply {
                            text = "● READY"
                            setTextColor(Ui.SUCCESS)
                        }
                        masterStopBtn.apply {
                            text = "STOP"
                            setTextColor(Ui.ERROR)
                            background = Ui.rounded(context, Ui.SURFACE_2, 0x44EF4444.toInt(), 6)
                        }
                    }

                    statusText.text = when (s.voiceState) {
                        VoiceState.WAITING_FOR_WAKE -> if (s.wakeModelAvailable) "LISTENING FOR ${wakePhrase.uppercase()}…" else "WAKE MODEL UNAVAILABLE • TAP TO TALK"
                        VoiceState.WAKE_DETECTED -> "WAKE WORD DETECTED"
                        VoiceState.LISTENING -> "LISTENING…"
                        VoiceState.THINKING -> "THINKING…"
                        VoiceState.EXECUTING -> "EXECUTING…"
                        VoiceState.VERIFYING -> "VERIFYING…"
                        VoiceState.RESEARCHING -> "RESEARCHING…"
                        VoiceState.SPEAKING -> "SPEAKING…"
                        VoiceState.ERROR -> "ERROR"
                        else -> s.statusMessage.ifBlank { "READY" }.uppercase()
                    }

                    activityDetail.text = when (s.voiceState) {
                        VoiceState.WAITING_FOR_WAKE -> "Tap to speak"
                        VoiceState.LISTENING -> "Listening… Speak now"
                        VoiceState.THINKING -> "Analyzing request…"
                        VoiceState.EXECUTING -> "Running: ${s.lastTaskGoal?.take(22) ?: "Automation"}"
                        VoiceState.VERIFYING -> "Verifying outcome…"
                        VoiceState.SPEAKING -> "Tap to interrupt"
                        VoiceState.IDLE -> "Tap badge to resume"
                        VoiceState.ERROR -> "Error occurred"
                        else -> s.statusMessage.ifBlank { "Ready" }
                    }

                    val isTaskActive = s.voiceState in setOf(
                        VoiceState.THINKING, VoiceState.EXECUTING, VoiceState.VERIFYING, VoiceState.RESEARCHING, VoiceState.WAITING_FOR_USER
                    ) || (!s.lastTaskGoal.isNullOrBlank() && s.voiceState != VoiceState.WAITING_FOR_WAKE && s.voiceState != VoiceState.IDLE)

                    protocolCard.visibility = if (isTaskActive) View.VISIBLE else View.GONE

                    if (isTaskActive) {
                        val badgeLabel = when (s.voiceState) {
                            VoiceState.THINKING -> "PLANNING"
                            VoiceState.EXECUTING -> "EXECUTING"
                            VoiceState.VERIFYING -> "VERIFYING"
                            VoiceState.RESEARCHING -> "RESEARCHING"
                            VoiceState.WAITING_FOR_USER -> "AWAITING INPUT"
                            else -> "ACTIVE"
                        }
                        protocolBadge.apply {
                            text = "● $badgeLabel"
                            setTextColor(if (s.voiceState == VoiceState.ERROR) Ui.ERROR else Ui.PRIMARY)
                        }

                        if (!s.lastTaskGoal.isNullOrBlank()) {
                            protocolGoalText.text = "Goal: ${s.lastTaskGoal}"
                        }
                        if (s.voiceState == VoiceState.EXECUTING) {
                            protocolStepText.text = "Executing autonomous action…"
                        } else if (s.voiceState == VoiceState.VERIFYING) {
                            protocolStepText.text = "Verifying outcome assertions…"
                        }
                    }

                    // Feed partial transcripts or completed responses to chat
                    if (s.partialTranscript.isNotBlank() && s.partialTranscript != lastRecordedTranscript) {
                        lastRecordedTranscript = s.partialTranscript
                        if (s.voiceState == VoiceState.THINKING || s.voiceState == VoiceState.EXECUTING) {
                            addUserMessage(s.partialTranscript.safeDisplay(), currentTimeString())
                        }
                    }

                    if (s.lastResponse.isNotBlank() && s.lastResponse != lastRecordedResponse) {
                        lastRecordedResponse = s.lastResponse
                        addAssistantMessage(s.lastResponse.safeDisplay(), currentTimeString())
                    }

                    waveform.setState(s.voiceState)

                    if (s.voiceState == VoiceState.LISTENING) {
                        val level = if (s.audioLevelRms > 0.05f) s.audioLevelRms else 0.25f
                        waveform.setAudioLevel(level)
                    } else {
                        waveform.setAudioLevel(0.5f)
                    }
                }
            }
        }
    }

    private fun toggleFloatingOverlay(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(context)
        ) {
            Toast.makeText(context, "Please grant Overlay Permission for J.A.R.V.I.S.", Toast.LENGTH_LONG).show()
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return
        }

        if (com.jarvis.overlay.JarvisFloatingOverlayService.isRunning) {
            val intent = android.content.Intent(context, com.jarvis.overlay.JarvisFloatingOverlayService::class.java).apply {
                action = com.jarvis.overlay.JarvisFloatingOverlayService.ACTION_STOP
            }
            context.stopService(intent)
            Toast.makeText(context, "Floating Orb stopped", Toast.LENGTH_SHORT).show()
        } else {
            val intent = android.content.Intent(context, com.jarvis.overlay.JarvisFloatingOverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Toast.makeText(context, "Floating Orb activated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDetachedFromWindow() {
        commandJob?.cancel()
        statusJob?.cancel()
        boundService = null
        super.onDetachedFromWindow()
    }
}
