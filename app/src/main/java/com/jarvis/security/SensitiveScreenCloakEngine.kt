package com.jarvis.security

import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.accessibility.JarvisAccessibilityService

/**
 * Sensitive Screen Cloaking Engine.
 * Inspects foreground UI nodes via Accessibility Service. Detects password fields,
 * PIN entry forms, and confidential banking/authentication contexts to cloak
 * them from automatic vision summarization and screenshot logging.
 */
class SensitiveScreenCloakEngine {

    private val sensitivePackagePrefixes = listOf(
        "com.google.android.apps.authenticator2",
        "org.thoughtcrime.securesms",
        "com.authenticator",
        "com.bank",
        "com.citi",
        "com.chase",
        "com.wellsfargo",
        "com.paypal"
    )

    fun isScreenSensitive(): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val currentPkg = service.rootInActiveWindow?.packageName?.toString() ?: ""

        if (sensitivePackagePrefixes.any { currentPkg.contains(it, ignoreCase = true) }) {
            return true
        }

        val root = service.rootInActiveWindow ?: return false
        return checkNodeRecursive(root)
    }

    private fun checkNodeRecursive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text.contains("enter password") ||
            text.contains("enter pin") ||
            text.contains("cvv") ||
            contentDesc.contains("password") ||
            contentDesc.contains("pin")
        ) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && checkNodeRecursive(child)) {
                return true
            }
        }

        return false
    }
}
