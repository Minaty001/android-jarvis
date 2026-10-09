package com.jarvis.finance

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.jarvis.data.JarvisDatabase
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus
import kotlinx.coroutines.*

class FinancialTrackerEngine(private val context: Context) {
    private val TAG = "FinancialTracker"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var job: Job? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Starting FinancialTrackerEngine...")

        job = scope.launch {
            JarvisEventBus.shared.events.collect { event ->
                if (event is JarvisEvent.SmsReceived) {
                    processSmsForTransactions(event.sender, event.body)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        Log.i(TAG, "FinancialTrackerEngine stopped.")
    }

    private fun processSmsForTransactions(sender: String, body: String) {
        val lowerBody = body.lowercase()
        // Broad check to save CPU
        if (!lowerBody.contains("debited") && !lowerBody.contains("credited") && !lowerBody.contains("spent")) {
            return
        }

        // Amount Regex: matches Rs. 100, INR 50.50, Rs 500, etc.
        val amountRegex = Regex("(?i)(?:rs\\.?|inr)\\s*([\\d,]+\\.?\\d*)")
        val amountMatch = amountRegex.find(body)
        
        if (amountMatch != null) {
            val amountStr = amountMatch.groupValues[1].replace(",", "")
            val amount = amountStr.toDoubleOrNull() ?: return

            val type = if (lowerBody.contains("debited") || lowerBody.contains("spent")) "DEBIT" else "CREDIT"
            
            // Very naive vendor extraction for demo/plan purposes.
            // In a real scenario, this would use a more robust regex or LLM extraction.
            val vendorRegex = Regex("(?i)(?:to|at|by)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:on|avl|available|ref|upi|\\.)|$)")
            val vendorMatch = vendorRegex.find(body)
            val vendor = vendorMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

            // Naive account tail extraction
            val accRegex = Regex("(?i)(?:a/c|acct|account)\\s*(?:no\\.?\\s*)?[Xx*.-]*(\\d{3,4})")
            val accMatch = accRegex.find(body)
            val accountTail = accMatch?.groupValues?.get(1) ?: ""

            Log.i(TAG, "Parsed Transaction: $type of $amount at $vendor (Account: $accountTail)")
            
            saveTransaction(type, amount, vendor, accountTail)
        }
    }

    private fun saveTransaction(type: String, amount: Double, vendor: String, accountTail: String) {
        try {
            val db = JarvisDatabase.getInstance(context).writableDatabase
            val values = ContentValues().apply {
                put("transaction_type", type)
                put("amount", amount)
                put("currency", "INR")
                put("vendor", vendor)
                put("account_tail", accountTail)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert("financial_transactions", null, values)
            Log.d(TAG, "Transaction saved to database.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transaction", e)
        }
    }

    fun getTransactions(limit: Int = 50): List<FinancialTransaction> {
        val list = mutableListOf<FinancialTransaction>()
        try {
            val db = JarvisDatabase.getInstance(context).readableDatabase
            val cursor = db.query("financial_transactions", null, null, null, null, null, "timestamp DESC", limit.toString())
            while (cursor.moveToNext()) {
                list.add(FinancialTransaction(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("transaction_type")),
                    amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
                    vendor = cursor.getString(cursor.getColumnIndexOrThrow("vendor")),
                    accountTail = cursor.getString(cursor.getColumnIndexOrThrow("account_tail")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                ))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get transactions", e)
        }
        return list
    }

    fun clearTransactions() {
        try {
            val db = JarvisDatabase.getInstance(context).writableDatabase
            db.delete("financial_transactions", null, null)
            Log.d(TAG, "All financial transactions cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear transactions", e)
        }
    }
}

data class FinancialTransaction(
    val id: Int,
    val type: String,
    val amount: Double,
    val vendor: String,
    val accountTail: String,
    val timestamp: Long
)
