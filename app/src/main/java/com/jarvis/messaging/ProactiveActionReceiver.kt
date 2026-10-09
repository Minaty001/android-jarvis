package com.jarvis.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class ProactiveActionReceiver : BroadcastReceiver() {
    private val TAG = "ProactiveActionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_SEND_PROACTIVE_SMS") {
            val contact = intent.getStringExtra("contact") ?: return
            val message = intent.getStringExtra("message") ?: return

            Log.i(TAG, "User approved proactive SMS to $contact: $message")
            
            // Dismiss notification (cancel all proactive notifications for simplicity)
            NotificationManagerCompat.from(context).cancelAll()

            // In a full implementation, we'd look up the contact's actual phone number.
            // For now, let's assume we fetch it or it's a known placeholder.
            // For safety in this project, we'll try to find a number or fallback to Toast.
            
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val number = prefs.getString("primary_contact_number", "") ?: ""

            if (number.isNotBlank()) {
                try {
                    val smsManager = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                    smsManager.sendTextMessage(number, null, message, null, null)
                    Log.i(TAG, "Proactive SMS sent successfully to $number")
                    Toast.makeText(context, "Jarvis: Message sent to $contact", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send proactive SMS", e)
                    Toast.makeText(context, "Jarvis: Failed to send SMS", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "No phone number found for primary contact. Simulating send.")
                Toast.makeText(context, "Jarvis (Simulated): Sent '$message' to $contact", Toast.LENGTH_LONG).show()
            }
        }
    }
}
