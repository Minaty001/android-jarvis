package com.jarvis.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class ClipboardOverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: LinearLayout? = null

    fun showContextActions(text: String, actions: List<String>, onActionSelected: (String) -> Unit) {
        if (overlayView != null) hide()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 40
        params.y = 200

        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(32, 32, 32, 32)

            val title = TextView(context).apply {
                this.text = "Jarvis Actions"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 0, 0, 16)
            }
            addView(title)

            actions.forEach { action ->
                val btn = TextView(context).apply {
                    this.text = "> \$action"
                    setTextColor(Color.CYAN)
                    textSize = 16f
                    setPadding(0, 8, 0, 8)
                    isClickable = true
                    setOnClickListener {
                        hide()
                        onActionSelected(action)
                    }
                }
                addView(btn)
            }
            
            val closeBtn = TextView(context).apply {
                this.text = "[ Close ]"
                setTextColor(Color.GRAY)
                textSize = 12f
                setPadding(0, 16, 0, 0)
                isClickable = true
                setOnClickListener { hide() }
            }
            addView(closeBtn)
        }

        try {
            windowManager.addView(overlayView, params)
            Log.i("ClipboardOverlay", "Showing overlay for actions: \$actions")
        } catch (e: Exception) {
            Log.e("ClipboardOverlay", "Failed to add overlay: \${e.message}")
        }
    }

    fun hide() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        overlayView = null
    }
}
