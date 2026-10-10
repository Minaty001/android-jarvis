package com.jarvis.finance

import android.content.Context
import com.jarvis.data.JarvisDatabase
import com.jarvis.execution.VerificationResult
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.tools.Tool
import com.jarvis.foundation.ToolMetadata
import com.jarvis.tools.ToolPolicy
import com.jarvis.tools.ToolResult
import java.util.Calendar

class ExpenseReportTool(private val context: Context) : Tool {

    override val name: String = "EXPENSE_REPORT"
    override val description: String =
        "Analyzes recent financial transactions (expenses/income). Actions: get_recent, get_summary. Parameters: action, timeframe (week, month)."
    override val policy: ToolPolicy = ToolPolicy(idempotent = true, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("action", "string", "Action: get_recent, get_summary", required = true),
            ParameterSchema("timeframe", "string", "Timeframe for summary (e.g., 'week', 'month')", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "get_summary"
        val timeframe = params["timeframe"]?.trim()?.lowercase() ?: "week"

        return try {
            val db = JarvisDatabase.getInstance(context).readableDatabase
            
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            if (timeframe == "month") {
                cal.add(Calendar.MONTH, -1)
            } else {
                cal.add(Calendar.WEEK_OF_YEAR, -1)
            }
            val startTime = cal.timeInMillis

            when (action) {
                "get_summary" -> {
                    val cursor = db.rawQuery(
                        "SELECT SUM(amount) FROM financial_transactions WHERE transaction_type = 'DEBIT' AND timestamp >= ?",
                        arrayOf(startTime.toString())
                    )
                    var totalSpent = 0.0
                    if (cursor.moveToFirst()) {
                        totalSpent = cursor.getDouble(0)
                    }
                    cursor.close()

                    ToolResult.Success(
                        message = "You have spent a total of Rs. $totalSpent in the past $timeframe.",
                        data = mapOf("totalSpent" to totalSpent, "currency" to "INR", "timeframe" to timeframe)
                    )
                }

                "get_recent" -> {
                    val cursor = db.query(
                        "financial_transactions",
                        arrayOf("transaction_type", "amount", "vendor", "timestamp"),
                        null,
                        null,
                        null,
                        null,
                        "timestamp DESC",
                        "5"
                    )
                    
                    val transactions = mutableListOf<String>()
                    while (cursor.moveToNext()) {
                        val type = cursor.getString(0)
                        val amt = cursor.getDouble(1)
                        val vendor = cursor.getString(2)
                        transactions.add("- $type of Rs.$amt at $vendor")
                    }
                    cursor.close()

                    if (transactions.isEmpty()) {
                        ToolResult.Success("No recent financial transactions found.")
                    } else {
                        ToolResult.Success(
                            message = "Your 5 most recent transactions are:\n" + transactions.joinToString("\n"),
                            data = mapOf("count" to transactions.size)
                        )
                    }
                }

                "split_bill", "split", "split_expense" -> {
                    val amount = params["amount"]?.toDoubleOrNull() ?: params["total"]?.toDoubleOrNull() ?: 100.0
                    val peopleStr = params["people"] ?: params["participants"] ?: "Me, Partner"
                    val people = peopleStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val tip = params["tip"]?.toDoubleOrNull() ?: 0.0

                    val splitter = com.jarvis.finance.ExpenseSplitterEngine()
                    val result = splitter.splitBill(amount, people, tip)

                    ToolResult.Success(
                        message = result.summaryText,
                        data = mapOf(
                            "total" to result.grandTotal,
                            "per_person" to result.perPersonRounded,
                            "participant_count" to people.size
                        )
                    )
                }

                else -> ToolResult.Failed("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.Failed("Failed to fetch expense report: ${e.message}")
        }
    }

    override fun verify(params: Map<String, String>, result: ToolResult): VerificationResult {
        return VerificationResult.success("Expense report retrieved")
    }
}
