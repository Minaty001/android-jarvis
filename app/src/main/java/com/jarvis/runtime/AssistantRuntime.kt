package com.jarvis.runtime

import android.content.Context
import android.util.Log
import com.jarvis.agent.*
import com.jarvis.ai.*
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

    // Execution & Verification
    val policyEngine: PolicyEngine get() = subsystems.policyEngine
    val verificationEngine get() = subsystems.verificationEngine
    val toolRegistry = ToolRegistry()
    val toolExecutor = ToolExecutor(
        toolRegistry,
        policyEngine,
        verificationEngine
    )
    
    // Control Layer (Smart Home & Flight)
    val permissionGate = com.jarvis.control.PermissionGate(
        context = context,
        getOwnerVerifier = { subsystems.ownerVerifier },
        getCandidateAudio = { com.jarvis.service.JarvisForegroundService.instance?.voiceEngine?.continuousAudioStream?.rollingBuffer?.getRecentAudio(48000) ?: ShortArray(0) }
    )
    val haClient = com.jarvis.smart_home.HomeAssistantClient()
    val smartHomeRouter = com.jarvis.smart_home.SmartHomeRouter(haClient)
    val flightAssistant = com.jarvis.flight.FlightAssistant(context)
    val simulatorBridge = com.jarvis.flight.SimulatorBridge()
    val flightRouter = com.jarvis.flight.FlightRouter(flightAssistant, simulatorBridge)

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
        com.jarvis.routine.SmartRoutineEngine(toolExecutor).also {
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
    var swarmOrchestrator: com.jarvis.agent.SwarmOrchestrator? = null
    var clipboardEngine: com.jarvis.clipboard.ClipboardContextEngine? = null
    var clipboardOverlay: com.jarvis.ui.overlay.ClipboardOverlayController? = null
    var diagnosticEngine: com.jarvis.device.SystemDiagnosticEngine? = null
    var patternEngine: com.jarvis.routine.PatternLearningEngine? = null
    var photographicMemoryEngine: com.jarvis.memory.PhotographicMemoryEngine? = null
    var notificationBatchingEngine: com.jarvis.notification.NotificationBatchingEngine? = null
    var ambientPolitenessEngine: com.jarvis.ambient.AmbientPolitenessEngine? = null
    var babelFishInterpreter: com.jarvis.voice.BabelFishInterpreter? = null
    var knowledgeGraphEngine: com.jarvis.memory.KnowledgeGraphEngine? = null
    var vocalEmpathyEngine: com.jarvis.voice.VocalEmpathyEngine? = null
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

    private fun initPlannerAndKernel() {
        (currentLlmClient as? java.io.Closeable)?.close()
        (currentStrongClient as? java.io.Closeable)?.close()

        val secureStore = com.jarvis.ui.security.SecureCredentialStore(context)
        val uiStore = com.jarvis.ui.data.UiPreferencesStore(context)

        val effectiveGroqKey = groqApiKey.ifBlank {
            secureStore.get("groq_api_key") ?: secureStore.get("groq") ?: com.jarvis.BuildConfig.GROQ_API_KEY
        }
        val effectiveGeminiKey = (secureStore.get("gemini_api_key") ?: secureStore.get("gemini") ?: com.jarvis.BuildConfig.GEMINI_API_KEY).orEmpty()
        val effectiveOpenRouterKey = (secureStore.get("openrouter_api_key") ?: secureStore.get("openrouter") ?: com.jarvis.BuildConfig.OPENROUTER_API_KEY).orEmpty()
        val effectiveOllamaUrl = (secureStore.get("ollama_base_url") ?: secureStore.get("ollama") ?: com.jarvis.BuildConfig.OLLAMA_BASE_URL).orEmpty()

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

        if (effectiveOllamaUrl.isNotBlank()) {
            val m = if (isModelFor("Ollama")) preferredModel else LlmConfig.OLLAMA_DEFAULT_MODEL
            providers.add(ProviderEntry("Ollama", OllamaLlm(baseUrl = effectiveOllamaUrl, model = m), priority = if (m == preferredModel) -1 else 3))
            strongProviders.add(ProviderEntry("Ollama", OllamaLlm(baseUrl = effectiveOllamaUrl, model = m), priority = 3))
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
            } else if (name.contains("ollama") && api.baseUrl.isNotBlank() && providers.none { it.name == "Ollama" }) {
                val m = if (isModelFor("Ollama")) preferredModel else LlmConfig.OLLAMA_DEFAULT_MODEL
                providers.add(ProviderEntry("Ollama", OllamaLlm(baseUrl = api.baseUrl, model = m), priority = if (m == preferredModel) -1 else 3))
                strongProviders.add(ProviderEntry("Ollama", OllamaLlm(baseUrl = api.baseUrl, model = m), priority = 3))
            }
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
            // Initialize LangChain Assistant for Memory and RAG
            if (effectiveGroqKey.isNotBlank()) {
                try {
                    ensureActiveScope().launch(Dispatchers.IO) {
                        try {
                        } catch (e: Throwable) {
                            android.util.Log.e("AssistantRuntime", "Failed to ingest profile: ${e.message}", e)
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("AssistantRuntime", "Failed to initialize LangChainAssistant: ${e.message}", e)
                }
            }
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
            if (swarmOrchestrator == null) {
                swarmOrchestrator = com.jarvis.agent.SwarmOrchestrator(llm) { taskId, result ->
                    ensureActiveScope().launch {
                        val announcement = "Subagent $taskId completed its task: $result"
                        Log.i(TAG, announcement)
                        ttsEngine.speak(announcement)
                        augmentedMemoryPipeline.rememberFact(
                            key = "Subagent $taskId",
                            value = announcement,
                            provenance = "SwarmOrchestrator"
                        )
                    }
                }
            }
            
            if (clipboardOverlay == null) {
                clipboardOverlay = com.jarvis.ui.overlay.ClipboardOverlayController(context)
            }
            
            if (clipboardEngine == null) {
                clipboardEngine = com.jarvis.clipboard.ClipboardContextEngine(context, llm) { text, actions ->
                    ensureActiveScope().launch(Dispatchers.Main) {
                        clipboardOverlay?.showContextActions(text, actions) { action ->
                            ensureActiveScope().launch {
                                val msg = "User selected clipboard action '$action' on text: $text"
                                Log.i(TAG, msg)
                                executeCommand(msg, userApprovalGranted = true)
                            }
                        }
                    }
                }
                clipboardEngine?.start()
            }
            
            if (diagnosticEngine == null) {
                diagnosticEngine = com.jarvis.device.SystemDiagnosticEngine(context)
                diagnosticEngine?.start()
            }
            
            if (patternEngine == null) {
                patternEngine = com.jarvis.routine.PatternLearningEngine(llm)
                patternEngine?.start()
            }
            
            if (photographicMemoryEngine == null) {
                photographicMemoryEngine = com.jarvis.memory.PhotographicMemoryEngine(ragIndexStore, llm)
                photographicMemoryEngine?.start()
            }
            
            if (notificationBatchingEngine == null) {
                notificationBatchingEngine = com.jarvis.notification.NotificationBatchingEngine(llm)
            }
            
            if (ambientPolitenessEngine == null) {
                ambientPolitenessEngine = com.jarvis.ambient.AmbientPolitenessEngine(context)
                ambientPolitenessEngine?.start()
            }
            
            if (babelFishInterpreter == null && clipboardOverlay != null) {
                babelFishInterpreter = com.jarvis.voice.BabelFishInterpreter(
                    context, llm, asrEngine, clipboardOverlay!!
                )
            }
            
            if (knowledgeGraphEngine == null) {
                knowledgeGraphEngine = com.jarvis.memory.KnowledgeGraphEngine(context, llm, memoryStore)
            }
            
            if (vocalEmpathyEngine == null) {
                vocalEmpathyEngine = com.jarvis.voice.VocalEmpathyEngine(context)
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
        val smartHomeTool = com.jarvis.tools.SmartHomeTool(smartHomeRouter, permissionGate)
        toolRegistry.register(smartHomeTool)
        val flightTool = com.jarvis.tools.FlightTool(flightRouter, permissionGate)
        toolRegistry.register(flightTool)
        val flightSimTool = com.jarvis.tools.FlightSimTool(flightRouter, permissionGate)
        toolRegistry.register(flightSimTool)

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

        // Swarm Agent
        toolRegistry.registerLazy(
            name = "SPAWN_AGENT",
            description = "Delegates a long-running or complex task to a specialized background subagent. Params: role, task"
        ) {
            com.jarvis.tools.SubagentSpawnTool(swarmOrchestrator!!)
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
        val locate = FileLocateTool(context)
        val read = FileReadTool(context)
        val write = FileWriteTool(context)
        toolRegistry.register(locate, locate.metadata)
        toolRegistry.register(read, read.metadata)
        toolRegistry.register(write, write.metadata)

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
        toolRegistry.register(uiClick, uiClick.metadata)
        toolRegistry.register(uiScroll, uiScroll.metadata)
        toolRegistry.register(uiType, uiType.metadata)
        toolRegistry.register(uiInspect, uiInspect.metadata)
        toolRegistry.register(uiGlobal, uiGlobal.metadata)
        toolRegistry.register(screenLock, screenLock.metadata)
        toolRegistry.register(screenUnlock, screenUnlock.metadata)
        toolRegistry.register(notifs, notifs.metadata)
        toolRegistry.register(battery, battery.metadata)
        toolRegistry.register(briefing, briefing.metadata)

        // Lazy automation and knowledge tools
        val deviceSettings = com.jarvis.tools.DeviceSettingsTool(context)
        toolRegistry.register(deviceSettings, deviceSettings.metadata)
        toolRegistry.registerLazy("ROUTINE_MANAGE", "Creates, executes, and manages smart automation routines") {
            com.jarvis.tools.RoutineManageTool(smartRoutineEngine)
        }
        toolRegistry.registerLazy("MACRO_WORKFLOW", "Runs recorded autonomous UI macro workflows") {
            com.jarvis.tools.MacroWorkflowTool(macroWorkflowEngine)
        }
        toolRegistry.registerLazy("APP_AUTOPILOT", "Autonomously interacts with on-screen UI across third-party apps") {
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
        toolRegistry.registerLazy("FIND_IN_ZARCHIVER", "Searches for a specific file or folder and opens it directly in ZArchiver.") {
            com.jarvis.tools.ZArchiverFindTool(context)
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
        val result = executeCommandInternal(utterance, userApprovalGranted, onEvent)
        result
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
        conversationManager.addMessage("user", utterance)
        knowledgeGraphEngine?.processInteraction("user", utterance)

        // 1. Check local fast-path intent resolver first for sub-millisecond offline execution.
        //    Actions with real-world side effects (or whose outcome needs verification) are
        //    NEVER fast-pathed — they must go through the closed loop so they can be verified.
        val resolvedIntent = IntentResolver.resolve(utterance).let { intent ->
            if (intent?.intent == AssistantIntent.SEARCH_WEB &&
                ForegroundAppSession.contextualSearchQuery(utterance, currentWorldState.activeAppSession) != null
            ) null else intent
        }

        // Cancel is a runtime control, not a tool: handle it before the busy gate and the
        // clarification routing, so "cancel" always works while a task is in flight or
        // awaiting clarification (unlike a normal command, which is rejected while busy).
        if (resolvedIntent?.intent == AssistantIntent.CANCEL_TASK) {
            Log.i(TAG, "Cancel intent detected; cancelling active task")
            cancelCurrentTask()
            val spoken = "Task cancelled."
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

        // If there is a pending task waiting for user clarification, route to task resumption.
        // Only for a bounded number of unanswered re-asks; after that the clarification is
        // considered abandoned and new utterances run as fresh tasks instead of being eaten.
        if (pendingClarificationTaskId != null) {
            if (pendingResumeStrikes < MAX_CLARIFICATION_RESUMES) {
                Log.i(TAG, "Routing utterance '$utterance' to resume pending task $pendingClarificationTaskId")
                return resumePendingTask(utterance, userApprovalGranted, onEvent)
            }
            val stale = pendingClarificationTaskId
            pendingClarificationTaskId = null
            pendingResumeStrikes = 0
            if (stale != null) {
                taskStateManager.updateTaskStatus(stale, "CANCELLED", "Not clarified after repeated asks; superseded by new command")
                Log.i(TAG, "Pending task $stale abandoned (clarification unanswered twice); running new command fresh")
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
                when (resolvedIntent.intent) {
                    AssistantIntent.APP_OPEN -> habitManager.recordAppUsage(resolvedIntent.params["app"].orEmpty())
                    AssistantIntent.PLAY_MEDIA -> habitManager.recordMediaPlayback(resolvedIntent.params["query"].orEmpty())
                    else -> Unit
                }
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

        // 2. Delegate to closed-loop AgentKernel on a tracked job so it is cancellable.
        val kernel = agentKernel ?: run {
            initPlannerAndKernel()
            agentKernel ?: error("AgentKernel is not initialized")
        }
        val historySnapshot = conversationManager.getHistory()
        val job = ensureActiveScope().async {
            kernel.run(
                goal = utterance,
                userApprovalGranted = userApprovalGranted,
                history = historySnapshot,
                resolvedIntent = resolvedIntent?.intent,
                onEvent = { event ->
                    when (event) {
                        is AgentEvent.TaskStarted -> {
                            activeTaskId = event.taskId
                            pendingClarificationTaskId = null
                            pendingResumeStrikes = 0
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
            val fallbackResponse = offlinePhantomEngine.tryHandleOffline(utterance)
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
        userApprovalGranted: Boolean = true,
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
