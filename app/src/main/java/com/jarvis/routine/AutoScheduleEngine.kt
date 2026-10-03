package com.jarvis.routine

import android.content.Context
import android.util.Log
import com.jarvis.data.JarvisDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object AutoScheduleEngine {
    private const val TAG = "AutoScheduleEngine"

    fun analyzeAndSchedule(context: Context, routineEngine: SmartRoutineEngine) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Running daily statistics and auto-scheduler...")
                val appContext = context.applicationContext
                val db = JarvisDatabase.getInstance(appContext).readableDatabase

                // Get apps opened >= 3 times in the morning, ordered by frequency
                val cursor = db.rawQuery(
                    """SELECT package_name, COUNT(*) as count FROM activity_moments
                       WHERE time_of_day = 'MORNING'
                       GROUP BY package_name
                       HAVING count >= 3
                       ORDER BY count DESC
                       LIMIT 3""",
                    null
                )

                val frequentApps = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    frequentApps.add(cursor.getString(0))
                }
                cursor.close()

                if (frequentApps.isNotEmpty()) {
                    Log.i(TAG, "Frequent morning apps detected: $frequentApps — updating Auto Morning Routine")
                    val actions = frequentApps.map { pkg ->
                        RoutineAction("OPEN_APP", mapOf("app" to pkg), "Open $pkg")
                    }

                    // Always replace (upsert) the routine so it stays up-to-date with evolving habits
                    val newRoutine = SmartRoutine(
                        id = "routine_auto_morning",
                        name = "Auto Morning Routine",
                        description = "Autonomously updated based on your morning app habits.",
                        trigger = RoutineTrigger(TriggerType.TIME_SCHEDULE, parameter = "08:00"),
                        actions = actions,
                        isEnabled = true
                    )
                    routineEngine.addCustomRoutine(newRoutine)
                    Log.i(TAG, "Morning routine auto-scheduled/updated successfully.")
                } else {
                    Log.i(TAG, "Not enough morning habit data yet for auto-scheduling.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze and schedule routines", e)
            }
        }
    }
}
