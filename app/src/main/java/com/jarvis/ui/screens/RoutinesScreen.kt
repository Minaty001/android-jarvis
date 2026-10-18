package com.jarvis.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.materialswitch.MaterialSwitch
import com.jarvis.routine.RoutineAction
import com.jarvis.routine.RoutineTrigger
import com.jarvis.routine.SmartRoutine
import com.jarvis.routine.SmartRoutineEngine
import com.jarvis.routine.TriggerType
import com.jarvis.ui.components.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutinesScreen(
    context: Context,
    private val routineEngine: SmartRoutineEngine? = SmartRoutineEngine.instance
) : ScrollView(context) {

    private val container = LinearLayout(context)

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
            text = "DAILY TASKS & ROUTINES"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.CYAN)
            letterSpacing = 0.10f
        })
        brand.addView(TextView(context).apply {
            text = "TIME SCHEDULES • AUTOMATED TRIGGERS • CONTEXTUAL MACROS"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.MUTED)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val addBtn = Ui.primaryButton(context, "+ ADD TASK") { showAddTaskDialog() }
        header.addView(addBtn)
        root.addView(header)

        root.addView(Ui.divider(context))

        container.orientation = LinearLayout.VERTICAL
        root.addView(container)

        renderRoutines()
    }

    private fun renderRoutines() {
        container.removeAllViews()
        val engine = routineEngine ?: SmartRoutineEngine.instance
        val routines = engine?.getAllRoutines() ?: emptyList()

        if (routines.isEmpty()) {
            val emptyCard = Ui.glassCard(context, Ui.BORDER)
            val emptyText = TextView(context).apply {
                text = "No daily tasks or automation routines configured.\nTap '+ ADD TASK' to schedule your first daily routine."
                setTextColor(Ui.MUTED)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20))
            }
            emptyCard.addView(emptyText)
            container.addView(emptyCard)
            return
        }

        routines.forEach { routine ->
            val card = Ui.glassCard(context, if (routine.isEnabled) Ui.BORDER_GLOW else Ui.BORDER)
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            }

            // Top Row: Title + Trigger Badge
            val topRow = Ui.row(context)
            val titleText = TextView(context).apply {
                text = routine.name.uppercase()
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.TEXT)
                letterSpacing = 0.06f
            }
            topRow.addView(titleText, LinearLayout.LayoutParams(0, -2, 1f))

            val badgeColor = when (routine.trigger.type) {
                TriggerType.TIME_SCHEDULE -> Ui.CYAN
                TriggerType.BATTERY_LEVEL_DROPS_BELOW, TriggerType.BATTERY_FULL -> Ui.WARNING
                TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED -> Ui.SUCCESS
                else -> Ui.MUTED
            }
            val triggerLabel = when (routine.trigger.type) {
                TriggerType.TIME_SCHEDULE -> "TIME: ${routine.trigger.parameter}"
                TriggerType.BATTERY_LEVEL_DROPS_BELOW -> "BATTERY <= ${routine.trigger.parameter}%"
                TriggerType.BATTERY_FULL -> "BATTERY 100%"
                TriggerType.WIFI_CONNECTED -> "WI-FI: ${routine.trigger.parameter}"
                else -> routine.trigger.type.name
            }
            topRow.addView(Ui.hudBadge(context, triggerLabel, badgeColor))
            inner.addView(topRow)

            // Description
            inner.addView(TextView(context).apply {
                text = routine.description
                textSize = 11f
                setTextColor(Ui.MUTED)
                setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8))
            })
            if (routine.lastResult.isNotBlank()) inner.addView(TextView(context).apply {
                text = "Last result: ${routine.lastResult}"
                textSize = 10f
                setTextColor(if (routine.lastResult.startsWith("Executed successfully")) Ui.SUCCESS else Ui.WARNING)
                setPadding(0, 0, 0, Ui.dp(context, 8))
            })

            // Actions Row: Enable Switch + Run Now + Delete
            val ctrlRow = Ui.row(context)

            val toggle = MaterialSwitch(context).apply {
                text = if (routine.isEnabled) "Active" else "Disabled"
                setTextColor(if (routine.isEnabled) Ui.SUCCESS else Ui.MUTED)
                isChecked = routine.isEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    engine?.enableRoutine(routine.id, isChecked)
                    renderRoutines()
                }
            }
            ctrlRow.addView(toggle, LinearLayout.LayoutParams(0, -2, 1f))

            val runBtn = Ui.button(context, "RUN NOW") {
                CoroutineScope(Dispatchers.Main).launch {
                    val result = engine?.executeRoutine(routine, isManual = true)
                    renderRoutines()
                    Toast.makeText(context, result?.reason ?: "Routine unavailable", Toast.LENGTH_SHORT).show()
                }
            }
            ctrlRow.addView(runBtn)

            if (routine.id.startsWith("routine_custom_")) {
                val deleteBtn = Ui.dangerButton(context, "DELETE") {
                    engine?.deleteRoutine(routine.id)
                    renderRoutines()
                }
                ctrlRow.addView(deleteBtn)
            }

            inner.addView(ctrlRow)
            card.addView(inner)
            container.addView(card)

            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, 10))
            }
            container.addView(spacer)
        }
    }

    private fun showAddTaskDialog() {
        val dlgLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16))
        }

        val nameInput = EditText(context).apply {
            hint = "Task Name (e.g. Drink Water)"
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
        }
        val timeInput = EditText(context).apply {
            hint = "Scheduled Time (e.g. 10:00)"
            setText("08:00")
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
        }
        val speechInput = EditText(context).apply {
            hint = "Speech Announcement"
            setText("Time for your daily task!")
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
        }

        dlgLayout.addView(TextView(context).apply { text = "Task Name"; setTextColor(Ui.TEXT); textSize = 12f })
        dlgLayout.addView(nameInput)
        dlgLayout.addView(TextView(context).apply { text = "Scheduled Time (HH:MM)"; setTextColor(Ui.TEXT); textSize = 12f; setPadding(0, Ui.dp(context, 8), 0, 0) })
        dlgLayout.addView(timeInput)
        dlgLayout.addView(TextView(context).apply { text = "Voice Alert Text"; setTextColor(Ui.TEXT); textSize = 12f; setPadding(0, Ui.dp(context, 8), 0, 0) })
        dlgLayout.addView(speechInput)

        android.app.AlertDialog.Builder(context)
            .setTitle("Schedule New Daily Task")
            .setView(dlgLayout)
            .setPositiveButton("SAVE") { _, _ ->
                val taskName = nameInput.text.toString().trim().ifBlank { "Daily Routine" }
                val timeStr = timeInput.text.toString().trim().ifBlank { "08:00" }
                val alertText = speechInput.text.toString().trim().ifBlank { "Task reminder: $taskName" }

                val newRoutine = SmartRoutine(
                    id = "routine_custom_${System.currentTimeMillis()}",
                    name = taskName,
                    description = "Custom scheduled task at $timeStr",
                    trigger = RoutineTrigger(TriggerType.TIME_SCHEDULE, parameter = timeStr),
                    actions = listOf(
                        RoutineAction("SPEAK", mapOf("text" to alertText), "Speak task announcement")
                    ),
                    isEnabled = true,
                    cooldownMinutes = 1
                )
                (routineEngine ?: SmartRoutineEngine.instance)?.addCustomRoutine(newRoutine)
                renderRoutines()
                Toast.makeText(context, "Added scheduled task '$taskName'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
