package com.jarvis.messaging

/**
 * Result type returned by WhatsAppMessagingEngine methods in the test suite.
 */
data class WhatsAppMessageResult(
    val success: Boolean,
    val message: String
)
