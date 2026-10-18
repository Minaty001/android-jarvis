package com.jarvis.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller for rendering high-contrast numbered badges over interactive
 * on-screen UI elements (buttons, inputs, tabs) for hands-free Voice Access.
 */
object VoiceAccessOverlayController {
    private const val TAG = "VoiceAccessOverlay"
    private const val AUTO_DISMISS_DELAY_MS = 10000L

    private val badgeMap = ConcurrentHashMap<Int, Rect>()
    private var overlayView: VoiceAccessOverlayView? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val autoDismissRunnable = Runnable {
        hideBadges()
    }

    val isShowing: Boolean
        get() = overlayView != null

    val currentBadgeCount: Int
        get() = badgeMap.size

    @Synchronized
    fun showBadges(context: Context): Int {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "JarvisAccessibilityService not enabled; cannot dump UI tree for Voice Access badges")
            return 0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted; cannot display Voice Access badges")
            return 0
        }

        val interactiveNodes = service.dumpInteractiveTree()
            .filter { (it.isClickable || it.isEditable) && it.bounds.width() > 10 && it.bounds.height() > 10 }
            .take(60)

        if (interactiveNodes.isEmpty()) {
            Log.d(TAG, "No interactive nodes visible to badge")
            return 0
        }

        badgeMap.clear()
        interactiveNodes.forEachIndexed { index, node ->
            badgeMap[index + 1] = node.bounds
        }

        mainHandler.post {
            renderOverlay(context.applicationContext, badgeMap)
        }

        // Schedule automatic dismissal
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS)

        Log.i(TAG, "Displayed ${badgeMap.size} Voice Access numbered badges")
        return badgeMap.size
    }

    @Synchronized
    fun hideBadges() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        badgeMap.clear()
        mainHandler.post {
            val view = overlayView
            val wm = windowManager
            if (view != null && wm != null) {
                try {
                    wm.removeView(view)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing Voice Access overlay view: ${e.message}")
                }
            }
            overlayView = null
        }
    }

    suspend fun clickBadge(number: Int): Boolean {
        val bounds = badgeMap[number] ?: return false
        hideBadges()

        val service = JarvisAccessibilityService.instance ?: return false
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()
        Log.i(TAG, "Clicking badge #$number at coordinates ($x, $y)")
        return service.clickCoordinatesAsync(x, y)
    }

    @SuppressLint("RtlHardcoded")
    private fun renderOverlay(appContext: Context, badges: Map<Int, Rect>) {
        try {
            if (windowManager == null) {
                windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            }
            val wm = windowManager ?: return

            // If already showing, update the view
            if (overlayView != null) {
                overlayView?.updateBadges(badges)
                overlayView?.invalidate()
                return
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            val view = VoiceAccessOverlayView(appContext, badges)
            wm.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render Voice Access overlay: ${e.message}", e)
        }
    }

    /**
     * Transparent overlay view that draws high-contrast numbered badges.
     */
    private class VoiceAccessOverlayView(
        context: Context,
        private var badges: Map<Int, Rect>
    ) : View(context) {

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E600E5FF") // High-contrast cyan
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000") // Dark border for readability
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        fun updateBadges(newBadges: Map<Int, Rect>) {
            badges = newBadges
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val badgeRadius = 24f

            for ((number, bounds) in badges) {
                // Place badge pill near top-left of element with bounds safety
                val cx = bounds.left.toFloat() + badgeRadius
                val cy = bounds.top.toFloat() + badgeRadius

                // Draw badge background
                canvas.drawCircle(cx, cy, badgeRadius, backgroundPaint)
                canvas.drawCircle(cx, cy, badgeRadius, borderPaint)

                // Vertically center text
                val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText(number.toString(), cx, textY, textPaint)
            }
        }
    }
}
