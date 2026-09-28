package com.jarvis.ui.screens

import android.util.Log
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.jarvis.ui.components.Ui
import com.jarvis.ui.model.ApiConfig
import com.jarvis.ui.model.AuthMethod
import com.jarvis.ui.model.ProviderRegistry
import com.jarvis.ui.reliability.ApiManager
import com.jarvis.ui.data.UiPreferencesStore
import com.jarvis.ui.dialogs.VoiceEnrollmentDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ApiManagerScreen(
    context: Context,
    private val owner: LifecycleOwner,
    private val manager: ApiManager,
    private val voiceEngine: com.jarvis.voice.VoiceEngine? = null,
    private val onChanged: (() -> Unit)? = null
) : ScrollView(context) {
    private val list = LinearLayout(context)
    private var testJob: Job? = null
    private val uiStore = UiPreferencesStore(context)

    init {
        setBackgroundColor(Ui.BG)
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 24))
        }
        addView(root)

        // Header
        val header = Ui.row(context)
        val brand = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(context).apply {
            text = "BRAIN & WAKE CONFIG"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.12f
        })
        brand.addView(TextView(context).apply {
            text = "KEYSTORE AES-GCM HARDWARE ENCRYPTED"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.MUTED)
            letterSpacing = 0.08f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Ui.hudBadge(context, "SECURED", Ui.SUCCESS))
        root.addView(header)

        root.addView(Ui.divider(context))
        
        // Active Model Dropdown
        root.addView(Ui.sectionLabel(context, "Active Agent Model"))
        val modelDropdownCard = Ui.glassCard(context, Ui.BORDER_GLOW)
        val modelDropdownInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8))
        }
        val modelSpinner = Spinner(context)
        modelDropdownInner.addView(modelSpinner)
        modelDropdownCard.addView(modelDropdownInner)
        root.addView(modelDropdownCard)
        
        // Populate Spinner based on configured APIs
        val configuredApis = manager.list().filter { it.active }.map { it.name }
        val availableModels = mutableListOf<String>()
        // Always add a fallback
        availableModels.add("openai/gpt-oss-20b") 
        for (provider in ProviderRegistry.providers.keys) {
            if (configuredApis.contains(provider)) {
                availableModels.addAll(ProviderRegistry.providers[provider] ?: emptyList())
            }
        }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, availableModels.distinct())
        modelSpinner.adapter = adapter
        
        // Set selection
        val savedModel = uiStore.loadSettings().preferredLlmModel
        val savedIndex = availableModels.distinct().indexOf(savedModel)
        if (savedIndex >= 0) modelSpinner.setSelection(savedIndex)
        
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isFirst = true
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isFirst) { isFirst = false; return }
                val selectedModel = adapter.getItem(position) ?: return
                uiStore.saveSettings(uiStore.loadSettings().copy(preferredLlmModel = selectedModel))
                onChanged?.invoke()
            }
        }


        root.addView(Ui.divider(context))

        // Wake Engine Configuration
        root.addView(Ui.sectionLabel(context, "Wake Word Engine (TFLite KWS)"))
        val wakeCard = Ui.glassCard(context, Ui.BORDER_GLOW)
        val wakeInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
        }
        
        // Wake Word Selector
        val initialSettings = uiStore.loadSettings()
        val wakeWordOptions = listOf("Jarvis", "Computer", "Alice", "Echo", "Friday")
        val wakeWordSpinner = Spinner(context)
        val wakeWordAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, wakeWordOptions)
        wakeWordSpinner.adapter = wakeWordAdapter
        val selectedWakeWordIndex = wakeWordOptions.indexOfFirst { it.equals(initialSettings.wakeWord, ignoreCase = true) }.coerceAtLeast(0)
        wakeWordSpinner.setSelection(selectedWakeWordIndex)
        wakeWordSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isFirst = true
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, id: Long) {
                if (isFirst) { isFirst = false; return }
                val newWord = wakeWordOptions[position]
                uiStore.saveSettings(uiStore.loadSettings().copy(wakeWord = newWord))
                voiceEngine?.wakeEngine?.setActiveWakeWord(newWord)
                onChanged?.invoke()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        val wakeWordRow = Ui.row(context)
        wakeWordRow.addView(TextView(context).apply { text = "Detection Phrase"; setTextColor(Ui.TEXT); textSize = 12f }, LinearLayout.LayoutParams(0, -2, 1f))
        wakeWordRow.addView(wakeWordSpinner)
        wakeInner.addView(wakeWordRow)

        // Wake Acknowledgment
        val ackOptions = listOf("CHIME", "VOICE", "BOTH", "SILENT")
        val wakeAckSpinner = Spinner(context)
        wakeAckSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ackOptions)
        val selectedAck = ackOptions.indexOf(initialSettings.wakeAcknowledgment.uppercase()).coerceAtLeast(0)
        wakeAckSpinner.setSelection(selectedAck)
        wakeAckSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isFirst = true
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, id: Long) {
                if (isFirst) { isFirst = false; return }
                uiStore.saveSettings(uiStore.loadSettings().copy(wakeAcknowledgment = ackOptions[position]))
                onChanged?.invoke()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        val ackRow = Ui.row(context)
        ackRow.addView(TextView(context).apply { text = "Response Style"; setTextColor(Ui.TEXT); textSize = 12f }, LinearLayout.LayoutParams(0, -2, 1f))
        ackRow.addView(wakeAckSpinner)
        wakeInner.addView(ackRow)

        // Voice Enrollment Profile
        val profileRow = Ui.row(context).apply { setPadding(0, Ui.dp(context, 8), 0, 0) }
        val enrollBtn = Ui.primaryButton(context, "ENROLL VOICE PROFILE") {
            VoiceEnrollmentDialog(context, voiceEngine = voiceEngine) { newProfile ->
                uiStore.saveVoiceProfile(newProfile)
                voiceEngine?.applyVoiceProfile(newProfile)
                Toast.makeText(context, "Voice profile updated!", Toast.LENGTH_SHORT).show()
            }.show()
        }
        profileRow.addView(enrollBtn)
        wakeInner.addView(profileRow)

        wakeCard.addView(wakeInner)
        root.addView(wakeCard)

        root.addView(Ui.divider(context))

        // LLM Provider Presets Card
        root.addView(Ui.sectionLabel(context, "Providers (1-Click Setup)"))
        val modelCard = Ui.glassCard(context, Ui.BORDER)
        val modelInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
        }
        val modelScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val modelRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        modelRow.addView(Ui.techChip(context, "Groq", "⚡") {
            showSimpleEditor("Groq")
        })
        modelRow.addView(Ui.techChip(context, "Gemini", "✨") {
            showSimpleEditor("Gemini")
        })
        modelRow.addView(Ui.techChip(context, "OpenRouter", "🎭") {
            showSimpleEditor("OpenRouter")
        })
        modelRow.addView(Ui.techChip(context, "Ollama", "🖥️") {
            showSimpleEditor("Ollama")
        })
        modelScroll.addView(modelRow)
        modelInner.addView(modelScroll)
        modelCard.addView(modelInner)
        root.addView(modelCard)

        // Actions
        val actionRow = Ui.row(context).apply {
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8))
        }
        actionRow.addView(TextView(context).apply {
            text = "CONFIGURED PROVIDERS"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actionRow.addView(Ui.primaryButton(context, "+ CUSTOM API") { showAdvancedEditor(null) })
        root.addView(actionRow)

        list.orientation = LinearLayout.VERTICAL
        root.addView(list)
        refresh()
    }

    fun refresh() {
        list.removeAllViews()
        val apis = manager.list()
        if (apis.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "No providers configured. Click a provider above to set up."
                textSize = 12f
                setTextColor(Ui.MUTED)
                setPadding(0, Ui.dp(context, 8), 0, 0)
            })
            return
        }
        apis.forEach { config ->
            val card = Ui.glassCard(context, if (config.active) Ui.CYAN_DIM else Ui.BORDER)
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            }
            val header = Ui.row(context)
            header.addView(TextView(context).apply {
                text = config.name
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (config.active) Ui.CYAN else Ui.TEXT)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val active = MaterialSwitch(context).apply {
                isChecked = config.active
                text = if (config.active) "Active" else "Inactive"
                setTextColor(if (config.active) Ui.SUCCESS else Ui.MUTED)
            }
            header.addView(active)
            inner.addView(header)

            inner.addView(TextView(context).apply {
                text = "${config.baseUrl}\n${config.authMethod.name} • ${config.timeoutMs} ms"
                textSize = 11.5f
                setTextColor(Ui.MUTED)
                setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8))
            })

            val actions = Ui.row(context)
            actions.addView(Ui.primaryButton(context, "TEST PING") { runTest(config) })
            actions.addView(Ui.button(context, "EDIT") { showAdvancedEditor(config) })
            actions.addView(Ui.dangerButton(context, "DELETE") {
                manager.delete(config.id)
                refresh()
                onChanged?.invoke()
            })
            inner.addView(actions)
            active.setOnCheckedChangeListener { _, checked ->
                manager.save(config.copy(active = checked, updatedAt = System.currentTimeMillis()), null)
                refresh()
                onChanged?.invoke()
            }
            card.addView(inner)
            list.addView(card)
        }
    }

    private fun runTest(config: ApiConfig) {
        testJob?.cancel()
        Toast.makeText(context, "Testing ${config.name}…", Toast.LENGTH_SHORT).show()
        testJob = owner.lifecycleScope.launch {
            val result = manager.testConnection(config, 3)
            result.fold(
                onSuccess = { code -> Toast.makeText(context, "Connection OK (HTTP $code)", Toast.LENGTH_LONG).show() },
                onFailure = { e ->
                    Log.e("ApiManager", "${config.name} test failed", e)
                    val cached = manager.lastKnownStatus(config.id)
                    val fallback = cached?.let { " Last known good: $it." } ?: " No cached success available."
                    Toast.makeText(context, "Check Logs.$fallback", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun showSimpleEditor(providerName: String) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context,18), Ui.dp(context,8), Ui.dp(context,18), Ui.dp(context,8)) }
        val secret = Ui.input(context, if (providerName == "Ollama") "Base URL (e.g. http://10.0.2.2:11434)" else "API Key for $providerName")
        container.addView(secret.first)
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Setup $providerName")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val cfg = ProviderRegistry.getConfigFor(providerName)
                try {
                    val secretText = secret.second.text?.toString()?.trim()
                    if (providerName == "Ollama" && !secretText.isNullOrBlank()) {
                        manager.save(cfg.copy(baseUrl = secretText), null)
                    } else {
                        manager.save(cfg, secretText?.takeIf { it.isNotEmpty() })
                    }
                    refresh()
                    onChanged?.invoke()
                } catch (e: Throwable) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showAdvancedEditor(existing: ApiConfig?) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context,18), Ui.dp(context,8), Ui.dp(context,18), Ui.dp(context,8)) }
        val name = Ui.input(context, "API name"); val url = Ui.input(context, "Endpoint URL"); val keyHeader = Ui.input(context, "API-key header (default Authorization)"); val secret = Ui.input(context, secretHint(existing?.authMethod ?: AuthMethod.API_KEY))
        container.addView(name.first); container.addView(url.first)
        val auth = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, AuthMethod.entries.map { it.name }) }
        container.addView(TextView(context).apply { text = "Authentication"; setTextColor(Color.WHITE) }); container.addView(auth)
        container.addView(keyHeader.first); container.addView(secret.first)
        val active = MaterialSwitch(context).apply { text = "Active"; isChecked = existing?.active ?: true }
        container.addView(active)
        val timeout = Ui.input(context, "Timeout (ms)"); container.addView(timeout.first)

        name.second.setText(existing?.name ?: "")
        url.second.setText(existing?.baseUrl ?: "")
        keyHeader.second.setText(existing?.apiKeyHeader ?: "Authorization")
        timeout.second.setText((existing?.timeoutMs ?: 30_000L).toString())
        if (existing != null) auth.setSelection(AuthMethod.entries.indexOf(existing.authMethod).coerceAtLeast(0))
        auth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { secret.first.hint = secretHint(AuthMethod.entries[position]) }
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(if (existing == null) "Add Custom API" else "Edit API")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val selected = AuthMethod.entries[auth.selectedItemPosition]
                val base = existing ?: ApiConfig()
                val cfg = base.copy(
                    name = name.second.text?.toString()?.trim().orEmpty().ifBlank { "API" },
                    baseUrl = url.second.text?.toString()?.trim().orEmpty(),
                    authMethod = selected,
                    apiKeyHeader = keyHeader.second.text?.toString()?.trim().orEmpty().ifBlank { "Authorization" },
                    active = active.isChecked,
                    timeoutMs = timeout.second.text?.toString()?.toLongOrNull()?.coerceIn(1_000L, 300_000L) ?: 30_000L,
                    updatedAt = System.currentTimeMillis()
                )
                try {
                    manager.validateUrl(cfg.baseUrl)
                    val secretText = secret.second.text?.toString()
                    manager.save(cfg, secretText?.takeIf { it.isNotEmpty() })
                    refresh()
                    onChanged?.invoke()
                } catch (e: Throwable) {
                    Log.e("ApiManager", "Invalid API configuration", e)
                    Toast.makeText(context, "Not saved: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun secretHint(method: AuthMethod): String = when (method) {
        AuthMethod.API_KEY -> "API key (stored encrypted)"
        AuthMethod.OAUTH2 -> "OAuth 2.0 access token (stored encrypted)"
        AuthMethod.BEARER -> "Bearer token (stored encrypted)"
        AuthMethod.BASIC -> "Basic auth as username:password (stored encrypted)"
    }
}
