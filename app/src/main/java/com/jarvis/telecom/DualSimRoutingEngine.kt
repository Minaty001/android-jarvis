package com.jarvis.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log

/**
 * Dual-SIM Smart Call Routing Engine.
 * Queries active SIM subscriptions and recommends or auto-routes calls through
 * the appropriate carrier card based on work/personal context or contact rules.
 */
class DualSimRoutingEngine(private val context: Context? = null) {

    private val TAG = "DualSimRoutingEngine"

    data class SimCardInfo(
        val subscriptionId: Int,
        val simSlotIndex: Int,
        val displayName: String,
        val carrierName: String
    )

    data class RoutingDecision(
        val recommendedSlot: Int,
        val simDisplayName: String,
        val rationale: String
    )

    @SuppressLint("MissingPermission")
    fun getActiveSimCards(): List<SimCardInfo> {
        val ctx = context ?: return emptyList()
        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        return try {
            val list = sm.activeSubscriptionInfoList ?: emptyList()
            list.map { sub ->
                SimCardInfo(
                    subscriptionId = sub.subscriptionId,
                    simSlotIndex = sub.simSlotIndex,
                    displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                    carrierName = sub.carrierName?.toString() ?: "Unknown"
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SIM cards: ${e.message}")
            emptyList()
        }
    }

    fun getRecommendedSim(contactOrNumber: String, isWorkContext: Boolean = false): RoutingDecision {
        val sims = getActiveSimCards()
        if (sims.isEmpty()) {
            return RoutingDecision(0, "Default SIM", "No multi-SIM hardware detected or permission pending; using default voice slot.")
        }
        if (sims.size == 1) {
            return RoutingDecision(sims[0].simSlotIndex, sims[0].displayName, "Single SIM active (${sims[0].displayName}).")
        }

        // Multi-SIM logic
        val chosen = if (isWorkContext) {
            sims.find { it.displayName.contains("work", ignoreCase = true) || it.simSlotIndex == 1 } ?: sims[0]
        } else {
            sims.find { it.displayName.contains("personal", ignoreCase = true) || it.simSlotIndex == 0 } ?: sims[0]
        }

        val rationale = if (isWorkContext) {
            "Routed via ${chosen.displayName} (Work context profile)."
        } else {
            "Routed via ${chosen.displayName} (Personal profile)."
        }

        return RoutingDecision(chosen.simSlotIndex, chosen.displayName, rationale)
    }
}
