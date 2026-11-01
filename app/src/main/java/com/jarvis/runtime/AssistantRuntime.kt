package com.jarvis.runtime

import android.content.Context
import android.util.Log
import com.jarvis.agent.*
import com.jarvis.ai.*
import com.jarvis.emotion.*
import com.jarvis.conversation.ConversationManager
import com.jarvis.execution.VerificationEngine
import com.jarvis.foundation.*
import com.jarvis.foundation.worldstate.ForegroundAppSession
import com.jarvis.interaction.BrowserDomTool
import com.jarvis.interaction.OsSystemInspectionTool
import com.jarvis.memory.AugmentedMemoryPipeline
import com.jarvis.memory.MemoryStore
import com.jarvis.tools.*
import com.jarvis.ui.data.UiPreferencesStore
import com.jarvis.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RuntimeExecutionResult(
    val success: Boolean,
    val spokenResponse: String,
    val stepResults: List<ToolResult>,
    val verified: Boolean = true
)

class AssistantRuntime(
    val context: Context,
    private var groqApiKey: String = "",
    private var groqModel: String = LlmConfig.DEFAULT_MODEL
) {
    private val TAG = "AssistantRuntime"
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun ensureActiveScope(): CoroutineScope {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }
        return scope
    }

    // Subsystem Container
    val subsystems = SubsystemManager(context)

    // Infrastructure & Services
    val memoryStore get() = subsystems.memoryStore
    val conversationManager get() = subsystems.conversationManager
    val augmentedMemoryPipeline get() = subsystems.augmentedMemoryPipeline
    val taskStateManager get() = subsystems.taskStateManager

    // Voice subsystem
    val vadEngine get() = subsystems.vadEngine
    val wakeEngine: WakeWordEngine get() = subsystems.wakeEngine
    var asrEngine: AndroidSpeechRecognizerEngine
        get() = subsystems.asrEngine
        set(value) { subsystems.asrEngine = value }
    val ttsEngine get() = subsystems.ttsEngine
    val offlinePhantomEngine = com.jarvis.voice.OfflinePhantomEngine(context)

    // Emotional Intelligence Subsystem
    val emotionalController: EmotionalLifecycleController by lazy {
        val store = UiPreferencesStore(context)
        val settings = store.loadSettings()
        val prefs = EmotionalPreferences(
            emotionalPresentationEnabled = settings.emotionalPresentationEnabled,
            humorEnabled = settings.humorEnabled,
            voiceExpressionEnabled = settings.voiceExpressionEnabled,
            uiEmotionAnimationsEnabled = settings.uiEmotionAnimationsEnabled,
            sentimentConfidenceThreshold = settings.sentimentConfidenceThreshold,
            reducedMotionEnabled = settings.reducedMotionEnabled
        )
        EmotionalLifecycleController(
            scope = ensureActiveScope(),
            initialPreferences = prefs
        )
    }

    val emotionalAdapter: EmotionalIntegrationAdapter by lazy {
        EmotionalIntegrationAdapter(controller = emotionalController).also {
            it.startEventBusSubscription(ensureActiveScope())
        }
    }

    val voiceProfileAdapter = VoiceProfileAdapter()
    val responseStylePolicyEngine = ResponseStylePolicyEngine()

    // Execution & Verification
    val policyEngine: PolicyEngine get() = subsystems.policyEngine
    val verificationEngine get() = subsystems.verificationEngine
    val toolRegistry = ToolRegistry()
    val toolExecutor = ToolExecutor(
        toolRegistry,
        policyEngine,
        verificationEngine
    )

    // Cognitive Core (JCI)
    val cognitiveOrchestrator: com.jarvis.cognitive.CognitiveOrchestrator by lazy {
        val store = UiPreferencesStore(context)
        val settings = store.loadSettings()
        val config = com.jarvis.cognitive.CognitiveConfig(
            enabled = settings.jciEnabled,
            shadowMode = settings.jciShadow,
            proactive = settings.jciProactive
        )
        com.jarvis.cognitive.CognitiveOrchestrator(initialConfig = config)
    }
    
    // Control Layer (Smart Home & Flight)
    val permissionGate = com.jarvis.control.PermissionGate(
        context = context,
        getOwnerVerifier = { subsystems.ownerVerifier },
        getCandidateAudio = { com.jarvis.service.JarvisForegroundService.instance?.voiceEngine?.continuousAudioStream?.rollingBuffer?.getRecentAudio(48000) ?: ShortArray(0) }
    )
    val haClient = com.jarvis.smart_home.HomeAssistantClient()


    // Autonomous Control Plane Architecture (Layers 0 - 10)

    // Reliability & Persistence
    val checkpointManager get() = subsystems.checkpointManager

    // RAG & Knowledge Vault Subsystem
    val ragIndexStore get() = subsystems.ragIndexStore
    val ragChunker get() = subsystems.ragChunker
    val ragRetriever get() = subsystems.ragRetriever
    val ragContextInjector get() = subsystems.ragContextInjector

    // Camera Optical Perception Subsystem
    val cameraPerceptionEngine get() = subsystems.cameraPerceptionEngine
    val cameraDetector get() = subsystems.cameraDetector

    // Autonomous WhatsApp & Messaging Subsystem
    val contactResolver get() = subsystems.contactResolver
    val callAndSmsAgent get() = subsystems.callAndSmsAgent
    val whatsappMessagingEngine get() = subsystems.whatsappMessagingEngine

    // System Telemetry & Log Reader Subsystem
    val logReaderEngine by lazy { com.jarvis.logs.LogReaderEngine() }

    // Agent Intelligence Architecture
    val mediaSessionManager get() = subsystems.mediaSessionManager
    val calendarManager get() = subsystems.calendarManager
    val ambientContextEngine get() = subsystems.ambientContextEngine
    val proactiveMessagingEngine get() = subsystems.proactiveMessagingEngine
    val financialTrackerEngine get() = subsystems.financialTrackerEngine
    val worldStateProvider by lazy { com.jarvis.autonomous.AndroidStateProvider(context, ambientContextEngine, mediaSessionManager, taskStateManager) }
    val worldStateService by lazy { com.jarvis.foundation.worldstate.WorldStateService(worldStateProvider) }
    val contextRouter by lazy {
        AgentContextRouter(
            memoryPipeline = augmentedMemoryPipeline,
            ragInjector = ragContextInjector,
            stateProvider = worldStateProvider
        )
    }
    val recoveryManager = AgentRecoveryManager()
    val goalEvaluator: GoalEvaluator = GoalEvaluator()
    val experienceManager by lazy { AgentExperienceManager(taskStateManager, augmentedMemoryPipeline) }
    val habitManager by lazy { com.jarvis.habit.HabitManager(memoryStore) }
    val smartRoutineEngine by lazy {
        com.jarvis.routine.SmartRoutineEngine(toolExecutor, repository = com.jarvis.routine.CustomRoutineRepository(context)).also {
            com.jarvis.routine.SmartRoutineEngine.instance = it
        }
    }
    val macroWorkflowEngine get() = subsystems.macroWorkflowEngine
    val fileManager get() = subsystems.fileManager
    val autonomousDaemon by lazy {
        com.jarvis.autonomous.AutonomousDaemon(
            ambientEngine = ambientContextEngine,
            calendarManager = calendarManager,
            fileManager = fileManager,
            toolExecutor = toolExecutor,
            verificationEngine = verificationEngine,
            // A proactive 60s heartbeat must never race an in-flight user command.
            busyProvider = { activeTaskJob?.isActive == true || pendingClarificationTaskId != null }
        )
    }
    val selfHealingSupervisor: com.jarvis.autonomous.SelfHealingSupervisor by lazy {
        com.jarvis.autonomous.SelfHealingSupervisor(
            toolExecutor = toolExecutor,
            onDeferredTaskDue = ::replayDeferredTask
        )
    }

    /**
     * Self-healing: network is back — re-run the deferred goal through the full
     * agent loop. If it fails again, re-queue it for the next network restore.
     */
    private fun replayDeferredTask(task: com.jarvis.autonomous.DeferredTask) {
        scope.launch {
            val result = executeCommand(task.goal)
            if (!result.success) {
                selfHealingSupervisor.queueDeferredTask(
                    task.goal,
                    "Replay failed: ${result.spokenResponse.take(120)}"
                )
                Log.w(TAG, "Deferred task replay failed; re-queued '${task.goal}'")
            }
        }
    }
    val networkStateMonitor = com.jarvis.routine.NetworkStateMonitor(
        context = context,
        onWifiConnected = { ssid ->
            smartRoutineEngine.onWifiConnected(ssid)
            selfHealingSupervisor.setNetworkStatus(true)
        },
        onWifiDisconnected = {
            smartRoutineEngine.onWifiDisconnected()
            selfHealingSupervisor.setNetworkStatus(false)
        }
    )
    val learnedSkillStore = com.jarvis.skilllearning.LearnedSkillStore()
    val persistentSkillStore = com.jarvis.skills.db.PersistentSkillStore(context)
    val skillRegistry = com.jarvis.agent.skills.SkillRegistry().apply {
        register(com.jarvis.agent.skills.SongSearchAndPlaySkill())
        register(com.jarvis.agent.skills.MediaSkill())
        register(com.jarvis.agent.skills.AppControlSkill())
        register(com.jarvis.agent.skills.CommunicationSkill())
        register(com.jarvis.agent.skills.ResearchSkill())
        register(com.jarvis.agent.skills.BrowserSkill())
        register(com.jarvis.agent.skills.VisionSkill())
        register(com.jarvis.agent.skills.ProactiveSkill())
        register(com.jarvis.agent.skills.RoutineSkill())
        register(com.jarvis.agent.skills.MacroSkill(macroWorkflowEngine))
        register(com.jarvis.agent.skills.AppAutopilotSkill())
        register(com.jarvis.agent.skills.TelecomSkill())
        register(com.jarvis.agent.skills.CalendarSkill())
        register(com.jarvis.agent.skills.SecuritySkill())
        register(com.jarvis.agent.skills.FileSkill())
        register(com.jarvis.agent.skills.AutonomousSkill())
        register(com.jarvis.agent.skills.RagSkill())
        register(com.jarvis.agent.skills.LogSkill())
        register(com.jarvis.agent.skills.TimeSkill())
        register(com.jarvis.agent.skills.LocationSkill())
        register(com.jarvis.agent.skills.DeviceHardwareSkill())
        register(com.jarvis.agent.skills.AppLauncherSkill())
    }
    val adaptiveSkillEvolution = com.jarvis.skilllearning.AdaptiveSkillEvolution(
        context = context,
        skillStore = persistentSkillStore,
        skillRegistry = skillRegistry
    ).also {
        it.loadPersistedSkillsIntoRegistry()
    }
    val skillDiscoveryEngine by lazy { com.jarvis.agent.SkillDiscoveryEngine(context, persistentSkillStore, skillRegistry) }

    private var activeTaskJob: Job? = null
    var activeTaskId: Long? = null
        private set
    var pendingClarificationTaskId: Long? = null
        private set
    private var pendingResumeStrikes = 0
    private companion object {
        const val MAX_CLARIFICATION_RESUMES = 2
    }

    private var llmPlanner: LlmPlanner? = null
    var agentKernel: AgentKernel? = null
        private set

    private val recentlySpokenUtterances = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun recordSpokenUtterance(text: String) {
        if (text.isNotBlank()) {
            recentlySpokenUtterances.add(text.trim().lowercase())
        }
    }

    fun wasSpokenRecently(text: String): Boolean {
        val target = text.trim().lowercase()
        if (target.isBlank()) return false
        return recentlySpokenUtterances.any { spoken ->
            spoken == target || spoken.contains(target) || target.contains(spoken)
        }
    }

    fun clearRecentlySpoken() {
        recentlySpokenUtterances.clear()
    }

    private suspend fun announceIncomingWhatsAppMessage(sender: String, message: String, platform: String) {
        val msgType = whatsappMessagingEngine.classifyMessageType(message)
        val displayMsg = if (msgType != "text") {
            "a $msgType"
        } else if (message.isNotBlank()) {
            ": ${message.take(100)}"
        } else {
            ""
        }
        val announcement = "New message on $platform from $sender$displayMsg"
        // Record silently in memory instead of speaking
        Log.i(TAG, "Silently recorded WhatsApp notification: $announcement")
    }

    var recallService: com.jarvis.memory.JarvisRecallService? = null
    var clipboardOverlay: com.jarvis.ui.overlay.ClipboardOverlayController? = null
    var diagnosticEngine: com.jarvis.device.SystemDiagnosticEngine? = null
    var photographicMemoryEngine: com.jarvis.memory.PhotographicMemoryEngine? = null
    var knowledgeGraphEngine: com.jarvis.memory.KnowledgeGraphEngine? = null
    var privacyDefenderEngine: com.jarvis.security.PrivacyDefenderEngine? = null

    init {
        registerDefaultTools()
        reapInterruptedTasks()
        initPlannerAndKernel()
        networkStateMonitor.start()
        
        loadVoiceProfile()
        scope.launch(Dispatchers.IO) {
            com.jarvis.tools.AppScanner.scanInstalledApps(context)
        }
        // Start passive memory indexing
        recallService = com.jarvis.memory.JarvisRecallService(subsystems.ragIndexStore)
        recallService?.start()
        
        // Hook up accessibility window changes to trigger memory captures
        com.jarvis.accessibility.JarvisAccessibilityService.onWindowContentChanged = {
            recallService?.requestCapture()
            photographicMemoryEngine?.requestCapture()
        }
    }

    private fun loadVoiceProfile() {
        runCatching {
            val store = UiPreferencesStore(context)
            val profile = store.loadVoiceProfile()
            if (profile.isEnrolled) {
                wakeEngine.applyVoiceProfile(profile)
            }
        }
    }

    // A fresh process means no closed loop is still alive. Tasks the previous
    // process left mid-flight or waiting on a clarification are orphaned:
    // mark them CANCELLED so the ledger never shows a live task nobody will finish.
    private fun reapInterruptedTasks() {
        val interrupted = listOf("RUNNING", "THINKING", "EXECUTING", "VERIFYING", "WAITING_FOR_USER")
        for (task in taskStateManager.getRecentTasks(limit = 20)) {
            if (task.status in interrupted) {
                taskStateManager.updateTaskStatus(task.id, "CANCELLED", "Session restarted; task interrupted")
                Log.i(TAG, "Reaped orphaned task ${task.id} (${task.status}) left by previous process")
            }
        }
    }

    private var currentLlmClient: LlmClient? = null
    private var currentStrongClient: LlmClient? = null
    var backendClient: com.jarvis.backend.JarvisBackendClient? = null
    var remoteActionDispatcher: com.jarvis.backend.RemoteActionDispatcher? = null

    private fun initPlannerAndKernel() {
        (currentLlmClient as? java.io.Closeable)?.close()
        (currentStrongClient as? java.io.Closeable)?.close()

        val secureStore = com.jarvis.ui.security.SecureCredentialStore(context)
        val uiStore = com.jarvis.ui.data.UiPreferencesStore(context)
        val appSettings = uiStore.loadSettings()

        if (appSettings.backendServerEnabled) {
            val backend = backendClient ?: com.jarvis.backend.JarvisBackendClient(
                serverUrl = appSettings.backendServerUrl,
                authToken = appSettings.backendAuthToken
            ).also { client ->
                backendClient = client
                remoteActionDispatcher = com.jarvis.backend.RemoteActionDispatcher(toolExecutor, client)
                client.connect()
            }
            backend.updateConfig(appSettings.backendServerUrl, appSettings.backendAuthToken)
        }

        val effectiveGroqKey = groqApiKey.ifBlank {
            secureStore.get("groq_api_key") ?: secureStore.get("groq") ?: com.jarvis.BuildConfig.GROQ_API_KEY
        }
        val effectiveGeminiKey = (secureStore.get("gemini_api_key") ?: secureStore.get("gemini") ?: com.jarvis.BuildConfig.GEMINI_API_KEY).orEmpty()
        val effectiveOpenRouterKey = (secureStore.get("openrouter_api_key") ?: secureStore.get("openrouter") ?: com.jarvis.BuildConfig.OPENROUTER_API_KEY).orEmpty()


        val providers = mutableListOf<ProviderEntry>()
        val strongProviders = mutableListOf<ProviderEntry>()
        
        val preferredModel = groqModel
        fun isModelFor(provider: String): Boolean {
            return com.jarvis.ui.model.ProviderRegistry.providers[provider]?.contains(preferredModel) == true
        }

        if (effectiveGroqKey.isNotBlank()) {
            val m = if (isModelFor("Groq")) preferredModel else LlmConfig.DEFAULT_MODEL
            providers.add(ProviderEntry("Groq", GroqLlm(effectiveGroqKey, model = m), priority = if (m == preferredModel) -1 else 0))
            strongProviders.add(ProviderEntry("Groq-Strong", GroqLlm(effectiveGroqKey, model = LlmConfig.STRONG_MODEL), priority = 0))
        }

        if (effectiveGeminiKey.isNotBlank()) {
            val m = if (isModelFor("Gemini")) preferredModel else LlmConfig.GEMINI_DEFAULT_MODEL
            providers.add(ProviderEntry("Gemini", GeminiLlm(effectiveGeminiKey, model = m), priority = if (m == preferredModel) -1 else 1))
            strongProviders.add(ProviderEntry("Gemini-Strong", GeminiLlm(effectiveGeminiKey, model = LlmConfig.GEMINI_STRONG_MODEL), priority = 1))
        }

        if (effectiveOpenRouterKey.isNotBlank()) {
            val m = if (isModelFor("OpenRouter")) preferredModel else LlmConfig.OPENROUTER_DEFAULT_MODEL
            providers.add(ProviderEntry("OpenRouter", OpenRouterLlm(effectiveOpenRouterKey, model = m), priority = if (m == preferredModel) -1 else 2))
            strongProviders.add(ProviderEntry("OpenRouter-Strong", OpenRouterLlm(effectiveOpenRouterKey, model = LlmConfig.OPENROUTER_STRONG_MODEL), priority = 2))
        }


        // Also check custom APIs configured via ApiManager
        for (api in uiStore.loadApis().filter { it.active }) {
            val key = api.credentialId?.let { secureStore.get(it) }.orEmpty()
            val name = api.name.lowercase()
            if (key.isNotBlank()) {
                if (name.contains("gemini") && providers.none { it.name == "Gemini" }) {
                    val m = if (isModelFor("Gemini")) preferredModel else LlmConfig.GEMINI_DEFAULT_MODEL
                    providers.add(ProviderEntry("Gemini", GeminiLlm(key, model = m), priority = if (m == preferredModel) -1 else 1))
                    strongProviders.add(ProviderEntry("Gemini-Strong", GeminiLlm(key, model = LlmConfig.GEMINI_STRONG_MODEL), priority = 1))
                } else if ((name.contains("openrouter") || name.contains("claude") || name.contains("deepseek")) && providers.none { it.name == "OpenRouter" }) {
                    val m = if (isModelFor("OpenRouter")) preferredModel else LlmConfig.OPENROUTER_DEFAULT_MODEL
                    providers.add(ProviderEntry("OpenRouter", OpenRouterLlm(key, model = m), priority = if (m == preferredModel) -1 else 2))
                    strongProviders.add(ProviderEntry("OpenRouter-Strong", OpenRouterLlm(key, model = LlmConfig.OPENROUTER_STRONG_MODEL), priority = 2))
                } else if (name.contains("groq") && providers.none { it.name == "Groq" }) {
                    val m = if (isModelFor("Groq")) preferredModel else LlmConfig.DEFAULT_MODEL
                    providers.add(ProviderEntry("Groq", GroqLlm(key, model = m), priority = if (m == preferredModel) -1 else 0))
                    strongProviders.add(ProviderEntry("Groq-Strong", GroqLlm(key, model = LlmConfig.STRONG_MODEL), priority = 0))
                }
            }
        }

        if (appSettings.backendServerEnabled && backendClient != null) {
            val backendLlm = com.jarvis.backend.BackendLlmClient(backendClient!!)
            providers.add(0, ProviderEntry("JARVIS-Backend", backendLlm, priority = -10, isEnabled = true))
            strongProviders.add(0, ProviderEntry("JARVIS-Backend-Strong", backendLlm, priority = -10, isEnabled = true))
        }

        val client: LlmClient? = if (providers.isNotEmpty()) MultiProviderLlmClient(providers) else null
        val strongClient: LlmClient? = if (strongProviders.isNotEmpty()) MultiProviderLlmClient(strongProviders) else null

        currentLlmClient = client
        currentStrongClient = strongClient
        
        // Inject LlmClient into messaging subsystems for AI smart reply
        subsystems.whatsappMessagingEngine.llmClient = client
        subsystems.callAndSmsAgent.llmClient = client

        val userProfileStore = com.jarvis.ui.data.UserProfileStore(context)
        val activeUserProfile = userProfileStore.loadProfile()

        if (client != null) {
            llmPlanner = LlmPlanner(
                client,
                router = com.jarvis.ai.ModelRouter(defaultModel = groqModel),
                strongLlm = strongClient,
                userProfile = activeUserProfile
            )
        }
        toolRegistry.registerLazy(
            name = "SCREEN_VISION",
            description = "Analyzes on-screen content and active application context"
        ) {
            com.jarvis.tools.ScreenVisionTool(context, currentLlmClient)
        }
        toolRegistry.registerLazy(
            name = "CAMERA_VISION",
            description = "Captures and analyzes real-world camera images via on-device TFLite MobileNet detection"
        ) {
            com.jarvis.tools.CameraVisionTool(context, cameraPerceptionEngine, cameraDetector, currentLlmClient)
        }

        agentKernel = AgentKernel(
            planner = llmPlanner,
            contextRouter = contextRouter,
            executor = toolExecutor,
            verifier = verificationEngine,
            taskState = taskStateManager,
            memory = augmentedMemoryPipeline,
            recovery = recoveryManager,
            goalEvaluator = goalEvaluator,
            experienceManager = experienceManager,
            skillRegistry = skillRegistry,
            learnedSkillStore = learnedSkillStore,
            checkpointManager = checkpointManager,
            skillDiscoveryEngine = skillDiscoveryEngine,
            llmClient = currentLlmClient
        )

        currentLlmClient?.let { llm ->
            if (clipboardOverlay == null) {
                clipboardOverlay = com.jarvis.ui.overlay.ClipboardOverlayController(context)
            }
            
            if (diagnosticEngine == null) {
                diagnosticEngine = com.jarvis.device.SystemDiagnosticEngine(context)
                diagnosticEngine?.start()
            }
            
            if (photographicMemoryEngine == null) {
                photographicMemoryEngine = com.jarvis.memory.PhotographicMemoryEngine(ragIndexStore, llm)
                photographicMemoryEngine?.start()
            }
            
            if (knowledgeGraphEngine == null) {
                knowledgeGraphEngine = com.jarvis.memory.KnowledgeGraphEngine(context, llm, memoryStore)
            }
            
            if (privacyDefenderEngine == null) {
                privacyDefenderEngine = com.jarvis.security.PrivacyDefenderEngine(context)
            }
            
            if (toolExecutor.biometricAuthenticator == null) {
                toolExecutor.biometricAuthenticator = com.jarvis.security.BiometricAuthenticator(context)
            }
        }
    }

    fun cancelCurrentTask() {
        val toCancel = activeTaskId ?: pendingClarificationTaskId
        Log.i(TAG, "Cancelling task: $toCancel")
        activeTaskJob?.cancel()
        toCancel?.let { id ->
            taskStateManager.updateTaskStatus(id, "CANCELLED", "User cancelled task")
        }
        activeTaskId = null
        pendingClarificationTaskId = null
        pendingResumeStrikes = 0
    }

    private fun registerDefaultTools() {
        toolRegistry.register(com.jarvis.finance.ExpenseReportTool(context))

        // Control Layer Tools
        val smartHomeTool = com.jarvis.tools.SmartHomeTool(haClient, permissionGate)
        toolRegistry.register(smartHomeTool)


        // App controls & Discovery
        val appListTool = ListInstalledAppsTool(context)
        toolRegistry.register(appListTool, appListTool.metadata)
        toolRegistry.register(OpenAppTool(context))
        val installAppTool = InstallAppTool(context)
        toolRegistry.register(installAppTool, installAppTool.metadata)
        toolRegistry.register(CloseAppTool(context))
        toolRegistry.register(AppsCloseAllTool(context))

        // Media & YouTube
        toolRegistry.register(MusicPlayTool(context))
        toolRegistry.register(YouTubePlayTool(context))
        toolRegistry.register(MediaPlaybackControlTool(context))
        toolRegistry.registerLazy("SPOTIFY_PLAY", "Controls Spotify playback and playlists") {
            SpotifyControlTool(context, mediaSessionManager)
        }
        
        // UI Autopilot
        toolRegistry.registerLazy(
            name = "AUTONOMOUS_UI_AGENT",
            description = "Agentic loop for multi-step UI reasoning and interaction using LLM vision"
        ) {
            com.jarvis.tools.AutonomousUiAgentTool(context, currentLlmClient)
        }

        // Web Agent
        toolRegistry.registerLazy(
            name = "WEB_AGENT",
            description = "Navigates the web invisibly to complete tasks like searching, booking, or extracting information"
        ) {
            com.jarvis.tools.WebAgentTool(context, currentLlmClient)
        }


        // System Diagnostic Fix
        toolRegistry.registerLazy(
            name = "SYSTEM_FIX",
            description = "Autonomously kills rogue background apps to free up memory. Params: action (must be 'clean_memory')"
        ) {
            com.jarvis.tools.SystemFixTool(context)
        }



        // Communication (Lazy)
        toolRegistry.registerLazy(
            name = "WHATSAPP",
            description = "Sends messages, reads unread messages, interactively replies, or configures auto-reply for WhatsApp and WhatsApp Business",
            metadata = com.jarvis.foundation.ToolMetadata(
                name = "WHATSAPP",
                description = "Sends messages, reads unread messages, interactively replies, or configures auto-reply for WhatsApp and WhatsApp Business",
                parameters = listOf(
                    com.jarvis.foundation.ParameterSchema("recipient", "string", "Recipient name or phone number", required = false),
                    com.jarvis.foundation.ParameterSchema("message", "string", "Message to send", required = false),
                    com.jarvis.foundation.ParameterSchema("action", "string", "Action: send_message, read_messages, reply, auto_reply, open", required = false),
                    com.jarvis.foundation.ParameterSchema("mode", "string", "Auto-reply mode: driving, meeting, busy, off", required = false),
                    com.jarvis.foundation.ParameterSchema("platform", "string", "Target platform: standard (default) or business", required = false),
                    com.jarvis.foundation.ParameterSchema("sender", "string", "Filter messages by sender name", required = false),
                    com.jarvis.foundation.ParameterSchema("limit", "integer", "Maximum messages to read (1-10)", required = false),
                    com.jarvis.foundation.ParameterSchema("visual", "boolean", "If true, visually read chat from screen", required = false)
                ),
                riskLevel = com.jarvis.foundation.RiskLevel.HIGH
            )
        ) {
            WhatsAppTool(context, whatsappMessagingEngine, currentLlmClient)
        }
        // System & Knowledge
        val flashlight = FlashlightTool(context)
        toolRegistry.register(flashlight, flashlight.metadata)
        val deepResearch = DeepResearchTool(context, fileManager) { currentLlmClient }
        toolRegistry.register(deepResearch, deepResearch.metadata)
        toolRegistry.register(WebSearchTool(context))
        toolRegistry.register(CalculatorTool(context))
        toolRegistry.register(WeatherTool(context))
        toolRegistry.register(TranslatorTool(context))
        toolRegistry.register(ClockTool(context))
        toolRegistry.register(QuickNotesTool(context))
        val protocolTool = JarvisProtocolTool(context)
        toolRegistry.register(protocolTool, protocolTool.metadata)
        val navTool = NavigationTool(context)
        toolRegistry.register(navTool, navTool.metadata)
        val unitConverterTool = UnitCurrencyConverterTool(context)
        toolRegistry.register(unitConverterTool, unitConverterTool.metadata)
        toolRegistry.registerLazy("SPEAK", "Speaks text aloud using on-device TTS") {
            SpeakTool(ttsEngine) { text -> recordSpokenUtterance(text) }
        }
        toolRegistry.registerLazy("NOTE", "Saves notes and long-term memory") {
            NoteTool(memoryStore)
        }
        toolRegistry.registerLazy("SEARCH_MEMORY", "Searches encrypted on-device memory store") {
            SearchMemoryTool(memoryStore)
        }


        // Foundation File & System tools
        val jarvisDbTool = JarvisDbTool(context)
        toolRegistry.register(jarvisDbTool, jarvisDbTool.metadata)

        val osInspect = OsSystemInspectionTool(context)
        toolRegistry.register(osInspect, osInspect.metadata)
        val browserDom = BrowserDomTool(context)
        toolRegistry.register(browserDom, browserDom.metadata)

        // UI & Screen Automation tools
        val uiClick = UiClickTool(context)
        val uiScroll = UiScrollTool(context)
        val uiType = UiTypeTool(context)
        val uiInspect = UiInspectTool(context)
        val uiGlobal = UiGlobalActionTool(context)
        val notifs = com.jarvis.tools.NotificationsTool(context)
        val battery = com.jarvis.tools.BatteryStatusTool(context)
        val briefing = com.jarvis.tools.DailyBriefingTool(context)
        val screenLock = com.jarvis.tools.ScreenLockTool(context)
        val screenUnlock = com.jarvis.tools.ScreenUnlockTool(context)
        val voiceAccess = com.jarvis.tools.VoiceAccessTool(context)
        val screenReader = com.jarvis.tools.ScreenReaderTool(context, ttsEngine)
        val defaultAssistant = com.jarvis.tools.DefaultAssistantTool(context)
        val cameraTool = com.jarvis.tools.HandsFreeCameraTool(context)
        val airGestureEngine = com.jarvis.sensors.AirGestureEngine(context)
        val airGestureTool = com.jarvis.tools.AirGestureTool(context, airGestureEngine)
        val voiceTypingTool = com.jarvis.tools.VoiceTypingTool(context)
        val drivingModeEngine = com.jarvis.automotive.DrivingModeEngine(context)
        val drivingModeTool = com.jarvis.tools.DrivingModeTool(context, drivingModeEngine)
        val uiGesture = com.jarvis.tools.UiGestureTool(context)
        toolRegistry.register(uiGesture)
        val clipboardTool = com.jarvis.tools.ClipboardTool(context)
        toolRegistry.register(clipboardTool, clipboardTool.metadata)

        airGestureEngine.onWaveDetected = {
            ensureActiveScope().launch(Dispatchers.IO) {
                val mediaSessions = com.jarvis.media.MediaSessionManager(context)
                if (mediaSessions.isCurrentlyPlaying()) {
                    toolRegistry.get("MEDIA_CONTROL")?.execute(mapOf("action" to "play_pause"))
                } else {
                    toolRegistry.get("UI_SCROLL")?.execute(mapOf("direction" to "down"))
                }
            }
        }

        toolRegistry.register(uiClick, uiClick.metadata)
        toolRegistry.register(uiScroll, uiScroll.metadata)
        toolRegistry.register(uiType, uiType.metadata)
        toolRegistry.register(uiInspect, uiInspect.metadata)
        toolRegistry.register(uiGlobal, uiGlobal.metadata)
        toolRegistry.register(screenLock, screenLock.metadata)
        toolRegistry.register(screenUnlock, screenUnlock.metadata)
        toolRegistry.register(voiceAccess, voiceAccess.metadata)
        toolRegistry.register(screenReader, screenReader.metadata)
        toolRegistry.register(defaultAssistant, defaultAssistant.metadata)
        toolRegistry.register(cameraTool, cameraTool.metadata)
        toolRegistry.register(airGestureTool, airGestureTool.metadata)
        toolRegistry.register(voiceTypingTool, voiceTypingTool.metadata)
        toolRegistry.register(drivingModeTool, drivingModeTool.metadata)
        toolRegistry.register(notifs, notifs.metadata)
        toolRegistry.register(battery, battery.metadata)
        toolRegistry.register(briefing, briefing.metadata)

        val keyguardUnlock = com.jarvis.tools.KeyguardUnlockTool(context)
        val emergencySos = com.jarvis.tools.EmergencySosTool(context)
        val soundDetector = com.jarvis.tools.SoundDetectorTool(context)
        toolRegistry.register(keyguardUnlock, keyguardUnlock.metadata)
        toolRegistry.register(emergencySos, emergencySos.metadata)
        toolRegistry.register(soundDetector, soundDetector.metadata)

        // Lazy automation and knowledge tools
        val deviceSettings = com.jarvis.tools.DeviceSettingsTool(context)
        toolRegistry.register(deviceSettings, deviceSettings.metadata)
        toolRegistry.registerLazy("ROUTINE_MANAGE", "Routine creation: call action=prepare_create with name, trigger_type, trigger_parameter, and actions_json (JSON array of ordered actions). Allowed triggers: TIME_SCHEDULE (daily HH:mm), WIFI_CONNECTED (SSID), WIFI_DISCONNECTED, BATTERY_LEVEL_DROPS_BELOW (1-99), BATTERY_FULL, CHARGER_CONNECTED, CHARGER_DISCONNECTED. Allowed actions: SPEAK text; DEVICE_SETTINGS set_volume/set_ringer; MEDIA_CONTROL action=stop; SYSTEM_SWITCHBOARD action=wifi|bt|led with target=on|off. The user must confirm aloud before save. Never use another tool to satisfy a routine creation request.") {
            com.jarvis.tools.RoutineManageTool(smartRoutineEngine)
        }
        toolRegistry.registerLazy("HABIT_LIST", "Inspects and suggests learned user habits and daily patterns") {
            com.jarvis.tools.HabitTool(habitManager)
        }
        toolRegistry.registerLazy("MACRO_WORKFLOW", "Runs recorded autonomous UI macro workflows") {
            com.jarvis.tools.MacroWorkflowTool(macroWorkflowEngine)
        }
        toolRegistry.registerLazy(
            name = "APP_AUTOPILOT",
            description = "Autonomously interacts with on-screen UI across third-party apps"
        ) {
            com.jarvis.tools.AppAutopilotTool(context)
        }
        toolRegistry.registerLazy("TELEPHONY_CONTROL", "Places calls, reads SMS, answers/declines calls") {
            com.jarvis.tools.TelephonyTool(context, contactResolver, callAndSmsAgent)
        }
        val calendarTool = com.jarvis.tools.CalendarTool(context)
        toolRegistry.register(calendarTool, calendarTool.metadata)
        val securityTool = com.jarvis.tools.SecurityAuditorTool(context)
        toolRegistry.register(securityTool, securityTool.metadata)
        toolRegistry.registerLazy("FILE_MANAGER", "Searches storage, inspects disk space, and suggests cleanup") {
            com.jarvis.tools.FileManagerTool(context)
        }
        toolRegistry.registerLazy("AUTONOMOUS_CONTROL", "Configures ambient autopilot and situational modes") {
            com.jarvis.tools.AutonomousModeTool(ambientContextEngine, autonomousDaemon)
        }
        toolRegistry.registerLazy("RAG_RETRIEVE", "Retrieves relevant passages and facts from local knowledge vault") {
            com.jarvis.tools.RagQueryTool(ragRetriever)
        }
        toolRegistry.registerLazy("RAG_INDEX", "Indexes local documents and notes into vector knowledge vault") {
            com.jarvis.tools.RagIndexTool(ragIndexStore, ragChunker)
        }
        val logsTool = com.jarvis.tools.LogsTool(logReaderEngine)
        toolRegistry.register(logsTool)
        val reminderTool = com.jarvis.tools.ReminderSchedulerTool(context)
        toolRegistry.register(reminderTool, reminderTool.metadata)
        val locationTool = com.jarvis.tools.LocationTool(context)
        toolRegistry.register(locationTool, locationTool.metadata)
        val switchboardTool = com.jarvis.tools.SystemSwitchboardTool(context)
        toolRegistry.register(switchboardTool, switchboardTool.metadata)
        val messageReaderTool = com.jarvis.tools.MessageReaderTool(context, permissionGate)
        toolRegistry.register(messageReaderTool, messageReaderTool.metadata)

    }

    /**
     * Responds to OS memory pressure signals from ComponentCallbacks2.
     * Evicts idle vision detectors, flushes RAG vector caches, and frees idle lazy tools.
     */
    fun onTrimMemory(level: Int) {
        subsystems.onTrimMemory(level)
        toolRegistry.evictIdleLazyTools()
    }

    fun updateConfig(apiKey: String, model: String) {
        groqApiKey = apiKey
        groqModel = model.ifBlank { LlmConfig.DEFAULT_MODEL }
        initPlannerAndKernel()
        Log.i(TAG, "AssistantRuntime updated with LLM config (model: $groqModel)")
    }

    fun reloadLlmProviders() {
        initPlannerAndKernel()
        Log.i(TAG, "AssistantRuntime LLM providers reloaded")
    }

    private val commandMutex = Mutex()

    private var lastInteractionTime: Long = 0
    private var currentSessionId: String = "session_${System.currentTimeMillis()}"
    private val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes

    suspend fun executeCommand(
        utterance: String,
        userApprovalGranted: Boolean = false,   // SECURITY: fail-closed — callers must explicitly grant approval
        onEvent: (AgentEvent) -> Unit = {}
    ): RuntimeExecutionResult = commandMutex.withLock {
        cognitiveOrchestrator.execute(
            utterance = utterance,
            userApprovalGranted = userApprovalGranted,
            onEvent = onEvent,
            legacyFallback = {
                executeCommandInternal(utterance, userApprovalGranted, onEvent)
            }
        )
    }

    private suspend fun executeCommandInternal(
        utterance: String,
        userApprovalGranted: Boolean = false,   // SECURITY: fail-closed
        onEvent: (AgentEvent) -> Unit = {}
    ): RuntimeExecutionResult {
        Log.i(TAG, "Runtime executing command: '$utterance'")
        
        val now = System.currentTimeMillis()
        if (now - lastInteractionTime > SESSION_TIMEOUT_MS) {
            com.jarvis.memory.MemorySummarizerJob.summarizeSession(currentSessionId, augmentedMemoryPipeline, currentLlmClient)
            currentSessionId = "session_$now"
            Log.i(TAG, "Starting new conversation session: $currentSessionId")
        }
        lastInteractionTime = now
        contextRouter.activeSessionId = currentSessionId
        
        clearRecentlySpoken()
        val currentWorldState = worldStateService.fresh()
        val commandUtterance = com.jarvis.ai.VoiceCommandAliases.expand(
            utterance,
            UiPreferencesStore(context).loadVoiceCommandAliases()
        )
        conversationManager.addMessage("user", utterance)
        knowledgeGraphEngine?.processInteraction("user", utterance)
        emotionalAdapter.onUserInput(utterance)

        smartRoutineEngine.pendingDraft()?.let { draft ->
            val normalized = utterance.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()
            val cancel = Regex("\\b(no|nope|nah|nahi|nahin|cancel|discard|stop|do not|dont)\\b").containsMatchIn(normalized)
            val response = when {
                com.jarvis.agent.AgentKernel.isExplicitApprovalReply(utterance) -> {
                    val saved = smartRoutineEngine.confirmPendingDraft()
                    if (saved != null) "Saved daily routine '${saved.name}'." else "That routine draft expired; please create it again."
                }
                cancel -> {
                    smartRoutineEngine.discardPendingDraft()
                    "Discarded the routine draft."
                }
                else -> "I’m still waiting to save '${draft.name}'. Say yes to confirm or cancel to discard it."
            }
            conversationManager.addMessage("assistant", response)
            onEvent(AgentEvent.Completed(response))
            return RuntimeExecutionResult(success = true, spokenResponse = response, stepResults = emptyList(), verified = false)
        }

        // 1. Check local fast-path intent resolver first for sub-millisecond offline execution.
        //    Actions with real-world side effects (or whose outcome needs verification) are
        //    NEVER fast-pathed — they must go through the closed loop so they can be verified.
        val resolvedIntent = (if (com.jarvis.agent.skills.RoutineSkill.isRoutineCreationRequest(commandUtterance)) null
            else IntentResolver.resolve(commandUtterance)).let { intent ->
            if (intent?.intent == AssistantIntent.SEARCH_WEB &&
                ForegroundAppSession.contextualSearchQuery(commandUtterance, currentWorldState.activeAppSession) != null
            ) null else intent
        }

        // Cancel is a runtime control, not a tool: handle it before the busy gate and the
        // clarification routing, so "cancel" always works while a task is in flight or
        // awaiting clarification (unlike a normal command, which is rejected while busy).
        if (resolvedIntent?.intent == AssistantIntent.CANCEL_TASK) {
            Log.i(TAG, "Cancel intent detected; cancelling active task")
            cancelCurrentTask()
            val spoken = "Task cancelled."
            emotionalAdapter.onTaskCancelled(activeTaskId?.toString() ?: "cancel", spoken)
            conversationManager.addMessage("assistant", spoken)
            onEvent(AgentEvent.Cancelled(spoken))
            return RuntimeExecutionResult(
                success = true,
                spokenResponse = spoken,
                stepResults = emptyList(),
                verified = false
            )
        }

        // Busy gate: a task is already running — don't stack a second closed loop.
        if (activeTaskJob?.isActive == true) {
            Log.w(TAG, "A task is already active (taskId=$activeTaskId); rejecting concurrent command.")
            return RuntimeExecutionResult(
                success = false,
                spokenResponse = "Still working on the previous task. Please wait or cancel it first.",
                stepResults = emptyList(),
                verified = false
            )
        }

        // If there is a pending task waiting for user clarification, route to task resumption
        // UNLESS the user is giving a new independent command.
        if (pendingClarificationTaskId != null) {
            val isDirectNewCommand = resolvedIntent?.directPlan != null || resolvedIntent?.intent != null
            if (!isDirectNewCommand && pendingResumeStrikes < MAX_CLARIFICATION_RESUMES) {
                Log.i(TAG, "Routing utterance '$utterance' to resume pending task $pendingClarificationTaskId")
                val explicitConfirmation = userApprovalGranted &&
                    com.jarvis.agent.AgentKernel.isExplicitApprovalReply(utterance)
                return resumePendingTask(utterance, explicitConfirmation, onEvent)
            }
            val stale = pendingClarificationTaskId
            pendingClarificationTaskId = null
            pendingResumeStrikes = 0
            if (stale != null) {
                taskStateManager.updateTaskStatus(stale, "CANCELLED", "Superseded by new command: $utterance")
                Log.i(TAG, "Pending task $stale superseded; running new command '$utterance' fresh")
            }
        }

        val fastPathSafe = resolvedIntent?.directPlan?.actions?.none { action ->
            toolRegistry.requiresOutcomeVerification(action.type)
        } == true

        if (fastPathSafe) {
            val directPlan = resolvedIntent!!.directPlan!!
            Log.i(TAG, "Executing fast-path plan for intent: ${resolvedIntent.intent}")
            val stepResults = mutableListOf<ToolResult>()
            var overallSuccess = true

            for (action in directPlan.actions) {
                val res = toolExecutor.execute(action.type, action.params, userApprovalGranted)
                stepResults.add(res)
                if (!res.success) {
                    overallSuccess = false
                    break
                }
            }

            augmentedMemoryPipeline.rememberUserTurn(currentSessionId, utterance)
            augmentedMemoryPipeline.rememberAssistantTurn(currentSessionId, directPlan.response)
            conversationManager.addMessage("assistant", directPlan.response)
            onEvent(AgentEvent.Completed(directPlan.response))

            if (overallSuccess) {
                emotionalAdapter.onTaskCompleted("fast_path_$now", verified = false, message = directPlan.response)
                when (resolvedIntent.intent) {
                    AssistantIntent.APP_OPEN -> habitManager.recordAppUsage(resolvedIntent.params["app"].orEmpty())
                    AssistantIntent.PLAY_MEDIA -> habitManager.recordMediaPlayback(resolvedIntent.params["query"].orEmpty())
                    else -> Unit
                }
            } else {
                emotionalAdapter.onTaskFailed("fast_path_$now", reason = directPlan.response)
            }

            return RuntimeExecutionResult(
                success = overallSuccess,
                spokenResponse = directPlan.response,
                stepResults = stepResults,
                // 'verified' means a real-world outcome was confirmed, NOT just that the tool call returned success.
                // Dispatching is not verification. The VerificationEngine is the only authoritative source of verified=true.
                verified = false
            )
        }

        // 1.5. Offload to dedicated backend server if enabled and connected
        val appSettings = UiPreferencesStore(context).loadSettings()
        if (appSettings.backendServerEnabled && backendClient?.isConnected == true) {
            Log.i(TAG, "Offloading query '$commandUtterance' to dedicated backend server...")

            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val isWifi = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ?: true

            // Real-time SentenceChunker for sub-400ms time-to-first-sound
            var hasStreamedAudio = false
            val chunker = com.jarvis.voice.SentenceChunker { chunk ->
                hasStreamedAudio = true
                ensureActiveScope().launch {
                    ttsEngine.speak(chunk)
                }
            }

            val originalTokenCallback = backendClient!!.onTokenReceived
            backendClient!!.onTokenReceived = { token ->
                chunker.appendToken(token)
                originalTokenCallback?.invoke(token)
            }

            val backendJob = ensureActiveScope().async {
                backendClient!!.sendQuery(
                    utterance = commandUtterance,
                    sessionId = currentSessionId,
                    battery = if (currentWorldState.batteryPercent >= 0) currentWorldState.batteryPercent else 100,
                    isCharging = currentWorldState.isCharging,
                    wifiConnected = isWifi
                )
            }
            activeTaskJob = backendJob
            val backendRes = try {
                val res = backendJob.await()
                chunker.flush()
                res
            } catch (e: Exception) {
                Result.failure<String>(e)
            } finally {
                backendClient?.onTokenReceived = originalTokenCallback
                activeTaskJob = null
            }

            if (backendRes.isSuccess) {
                val spoken = backendRes.getOrThrow()
                if (hasStreamedAudio) {
                    recordSpokenUtterance(spoken)
                }
                augmentedMemoryPipeline.rememberUserTurn(currentSessionId, utterance)
                augmentedMemoryPipeline.rememberAssistantTurn(currentSessionId, spoken)
                conversationManager.addMessage("assistant", spoken)
                onEvent(AgentEvent.Completed(spoken))
                emotionalAdapter.onTaskCompleted("backend_$now", verified = true, message = spoken)
                return RuntimeExecutionResult(
                    success = true,
                    spokenResponse = spoken,
                    stepResults = emptyList(),
                    verified = true
                )
            } else {
                Log.w(TAG, "Backend query failed (${backendRes.exceptionOrNull()?.message}); falling back to on-device AgentKernel.")
            }
        }

        // 2. Delegate to closed-loop AgentKernel on a tracked job so it is cancellable.
        val kernel = agentKernel ?: run {
            initPlannerAndKernel()
            agentKernel ?: error("AgentKernel is not initialized")
        }
        val historySnapshot = conversationManager.getHistory()
        val job = ensureActiveScope().async {
            kernel.run(
                goal = commandUtterance,
                userApprovalGranted = userApprovalGranted,
                history = historySnapshot,
                resolvedIntent = resolvedIntent?.intent,
                onEvent = { event ->
                    when (event) {
                        is AgentEvent.TaskStarted -> {
                            activeTaskId = event.taskId
                            pendingClarificationTaskId = null
                            pendingResumeStrikes = 0
                            emotionalAdapter.onTaskStarted(event.taskId.toString(), commandUtterance)
                            onEvent(event)
                        }
                        is AgentEvent.Executing -> {
                            emotionalAdapter.onToolStarted(activeTaskId?.toString() ?: "active", event.action)
                            onEvent(event)
                        }
                        is AgentEvent.Failed -> {
                            emotionalAdapter.onTaskFailed(activeTaskId?.toString() ?: "active", event.message)
                            onEvent(event)
                        }
                        is AgentEvent.Cancelled -> {
                            emotionalAdapter.onTaskCancelled(activeTaskId?.toString() ?: "active", event.message)
                            onEvent(event)
                        }
                        else -> onEvent(event)
                    }
                }
            )
        }
        activeTaskJob = job

        val agentResult = try {
            job.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "Task cancelled: ${e.message}")
            emotionalAdapter.onTaskCancelled(activeTaskId?.toString() ?: "cancel", "Task cancelled by user")
            taskStateManager.updateTaskStatus(activeTaskId ?: -1L, "CANCELLED", "Task cancelled by user")
            onEvent(AgentEvent.Cancelled("Task cancelled by user"))
            return RuntimeExecutionResult(
                success = false,
                spokenResponse = "Task cancelled.",
                stepResults = emptyList(),
                verified = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "AgentKernel failed, falling back to Offline Phantom Mode", e)
            emotionalAdapter.onTaskFailed(activeTaskId?.toString() ?: "failed", e.message ?: "AgentKernel failed")
            val fallbackResponse = offlinePhantomEngine.tryHandleOffline(commandUtterance)
            if (fallbackResponse != null) {
                conversationManager.addMessage("assistant", fallbackResponse)
                onEvent(AgentEvent.Completed(fallbackResponse))
                return RuntimeExecutionResult(
                    success = true,
                    spokenResponse = fallbackResponse,
                    stepResults = emptyList(),
                    verified = false
                )
            } else {
                return RuntimeExecutionResult(
                    success = false,
                    spokenResponse = "Network unavailable and offline fallback could not handle the request.",
                    stepResults = emptyList(),
                    verified = false
                )
            }
        } finally {
            activeTaskJob = null
            activeTaskId = null
        }

        val completedTaskId = agentResult.taskId.toString()
        if (agentResult.success) {
            val isVerified = agentResult.verifiedActions.isNotEmpty() || agentResult.verified
            emotionalAdapter.onTaskCompleted(completedTaskId, verified = isVerified, message = agentResult.response)
        } else {
            emotionalAdapter.onTaskFailed(completedTaskId, reason = agentResult.response)
        }

        if (agentResult.response.isNotBlank()) {
            augmentedMemoryPipeline.rememberUserTurn(currentSessionId, utterance)
            augmentedMemoryPipeline.rememberAssistantTurn(currentSessionId, agentResult.response)
            conversationManager.addMessage("assistant", agentResult.response)
        }

        val taskRec = taskStateManager.getTask(agentResult.taskId)
        if (taskRec?.status == "WAITING_FOR_USER") {
            pendingClarificationTaskId = agentResult.taskId
            Log.i(TAG, "Task ${agentResult.taskId} set as pending clarification")
        } else {
            pendingClarificationTaskId = null
        }

        val toolResults = agentResult.steps.map {
            ToolResult(it.success, it.message)
        }

        if (agentResult.success && agentResult.verifiedActions.isNotEmpty()) {
            adaptiveSkillEvolution.onTaskCompleted(utterance, agentResult.verifiedActions)
        }

        return RuntimeExecutionResult(
            success = agentResult.success,
            spokenResponse = agentResult.response,
            stepResults = toolResults,
            verified = agentResult.verified
        )
    }

    suspend fun resumePendingTask(
        clarification: String,
        userApprovalGranted: Boolean = false,
        onEvent: (AgentEvent) -> Unit = {}
    ): RuntimeExecutionResult {
        val taskId = pendingClarificationTaskId
        val kernel = agentKernel ?: error("AgentKernel is not initialized")
        if (taskId != null) {
            pendingClarificationTaskId = null
            conversationManager.addMessage("user", clarification)
            val historySnapshot = conversationManager.getHistory()
            val job = scope.async {
                kernel.resumeTask(
                    taskId = taskId,
                    clarification = clarification,
                    userApprovalGranted = userApprovalGranted,
                    history = historySnapshot,
                    onEvent = { event ->
                        when (event) {
                            is AgentEvent.TaskStarted -> {
                                activeTaskId = event.taskId
                                pendingClarificationTaskId = null
                            }
                            else -> onEvent(event)
                        }
                    }
                )
            }
            activeTaskJob = job

            val agentResult = try {
                job.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Task cancelled during resume: ${e.message}")
                taskStateManager.updateTaskStatus(activeTaskId ?: taskId, "CANCELLED", "Task cancelled by user")
                onEvent(AgentEvent.Cancelled("Task cancelled by user"))
                return RuntimeExecutionResult(
                    success = false,
                    spokenResponse = "Task cancelled.",
                    stepResults = emptyList(),
                    verified = false
                )
            } finally {
                activeTaskJob = null
                activeTaskId = null
            }

            val taskRec = taskStateManager.getTask(agentResult.taskId)
            if (taskRec?.status == "WAITING_FOR_USER") {
                pendingClarificationTaskId = agentResult.taskId
                // A resume that leaves the task still asking was fruitless — count it so an
                // unfixable task is abandoned after MAX_CLARIFICATION_RESUMES instead of
                // swallowing every later command as a clarification of a stuck thread.
                pendingResumeStrikes++
            } else {
                pendingClarificationTaskId = null
                pendingResumeStrikes = 0
            }

            augmentedMemoryPipeline.rememberUserTurn(currentSessionId, clarification)
            augmentedMemoryPipeline.rememberAssistantTurn(currentSessionId, agentResult.response)

            val toolResults = agentResult.steps.map {
                ToolResult(it.success, it.message)
            }

            return RuntimeExecutionResult(
                success = agentResult.success,
                spokenResponse = agentResult.response,
                stepResults = toolResults,
                verified = agentResult.verified
            )
        }
        return executeCommand(clarification, userApprovalGranted, onEvent)
    }

    fun release() {
        cancelCurrentTask()
        networkStateMonitor.stop()
        currentLlmClient?.close()
        currentStrongClient?.close()
        subsystems.releaseAll()
        scope.cancel()
    }
}

object MemoryNamespaces {
    const val DOCUMENTS = "documents"
    const val CONVERSATION = "conversation"
    const val LONG_TERM = "long_term"
    const val PREFERENCES = "preferences"
}
