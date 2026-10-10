package com.jarvis.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class InvisibleWebBrowserEngine(private val context: Context) {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun initialize() = withContext(Dispatchers.Main) {
        if (webView == null) {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                layout(0, 0, 1080, 2400) // Dummy layout bounds to ensure rendering
            }
        }
    }

    suspend fun loadUrl(url: String) = withContext(Dispatchers.Main) {
        webView?.loadUrl(url)
    }

    suspend fun evaluateJavascript(js: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            webView?.evaluateJavascript(js) { result ->
                if (continuation.isActive) continuation.resume(result)
            } ?: run { if (continuation.isActive) continuation.resume(null) }
        }
    }

    fun destroy() {
        webView?.post {
            webView?.destroy()
            webView = null
        }
    }
}
