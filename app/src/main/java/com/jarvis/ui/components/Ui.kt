package com.jarvis.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Modern, Human-Crafted Material Design System Tokens & View Factories.
 * Dynamically supports both sleek Dark Slate and crisp, high-contrast Light themes.
 */
object Ui {
    @JvmStatic
    var isDark: Boolean = true

    @JvmStatic
    fun updateTheme(context: Context, themePreference: String? = null) {
        isDark = when (themePreference) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    // Professional Zinc & Slate Theme Tokens (Dynamic for Light and Dark)
    @JvmStatic
    val BG: Int get() = if (isDark) 0xFF0A0D14.toInt() else 0xFFF8FAFC.toInt()
    @JvmStatic
    val SURFACE: Int get() = if (isDark) 0xFF121722.toInt() else 0xFFFFFFFF.toInt()
    @JvmStatic
    val SURFACE_2: Int get() = if (isDark) 0xFF181F2E.toInt() else 0xFFF1F5F9.toInt()
    @JvmStatic
    val SURFACE_CONTAINER: Int get() = if (isDark) 0xFF1E2638.toInt() else 0xFFE2E8F0.toInt()
    @JvmStatic
    val SURFACE_CONTAINER_HIGH: Int get() = if (isDark) 0xFF263045.toInt() else 0xFFCBD5E1.toInt()

    @JvmStatic
    val BORDER: Int get() = if (isDark) 0xFF242E42.toInt() else 0xFFE2E8F0.toInt()
    @JvmStatic
    val BORDER_SUBTLE: Int get() = if (isDark) 0xFF1B2332.toInt() else 0xFFF1F5F9.toInt()
    @JvmStatic
    val BORDER_GLOW: Int get() = if (isDark) 0xFF334155.toInt() else 0xFF94A3B8.toInt()

    @JvmStatic
    val PRIMARY: Int get() = if (isDark) 0xFF3B82F6.toInt() else 0xFF2563EB.toInt() // Electric Cobalt Blue / Royal Blue
    @JvmStatic
    val PRIMARY_ACCENT: Int get() = if (isDark) 0xFF3B82F6.toInt() else 0xFF2563EB.toInt()
    @JvmStatic
    val BLUE_PRIMARY: Int get() = if (isDark) 0xFF3B82F6.toInt() else 0xFF2563EB.toInt()

    @JvmStatic
    val CYAN: Int get() = if (isDark) 0xFF06B6D4.toInt() else 0xFF0284C7.toInt() // Refined Teal for voice/audio state
    @JvmStatic
    val CYAN_DIM: Int get() = if (isDark) 0xFF0891B2.toInt() else 0xFF0369A1.toInt()

    @JvmStatic
    val TEXT: Int get() = if (isDark) 0xFFF1F5F9.toInt() else 0xFF0F172A.toInt() // Slate 100 in Dark, Slate 900 in Light
    @JvmStatic
    val TEXT_SECONDARY: Int get() = if (isDark) 0xFFCBD5E1.toInt() else 0xFF334155.toInt() // Slate 300 in Dark, Slate 700 in Light
    @JvmStatic
    val MUTED: Int get() = if (isDark) 0xFF94A3B8.toInt() else 0xFF64748B.toInt() // Slate 400 in Dark, Slate 500 in Light

    @JvmStatic
    val SUCCESS: Int get() = if (isDark) 0xFF10B981.toInt() else 0xFF059669.toInt() // Emerald
    @JvmStatic
    val WARNING: Int get() = if (isDark) 0xFFF59E0B.toInt() else 0xFFD97706.toInt() // Amber
    @JvmStatic
    val ERROR: Int get() = if (isDark) 0xFFEF4444.toInt() else 0xFFDC2626.toInt() // Crimson

    @JvmStatic
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    @JvmStatic
    fun column(context: Context): LinearLayout = column(context, 16)

    @JvmStatic
    fun column(context: Context, pad: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, pad), dp(context, pad), dp(context, pad), dp(context, pad))
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }

    @JvmStatic
    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    @JvmStatic
    fun title(context: Context, text: String): TextView = title(context, text, 20f)

    @JvmStatic
    fun title(context: Context, text: String, size: Float): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(TEXT)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = 0.01f
        setPadding(0, 0, 0, dp(context, 4))
    }

    @JvmStatic
    fun subtitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(MUTED)
        setLineSpacing(0f, 1.2f)
        setPadding(0, 0, 0, dp(context, 8))
    }

    @JvmStatic
    fun sectionLabel(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text.uppercase()
        textSize = 11f
        setTextColor(TEXT_SECONDARY)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.06f
        setPadding(0, dp(context, 10), 0, dp(context, 8))
    }

    @JvmStatic
    fun card(context: Context): MaterialCardView = card(context, BORDER)

    @JvmStatic
    fun card(context: Context, strokeColor: Int): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(context, 12).toFloat()
        setCardBackgroundColor(SURFACE)
        this.strokeColor = strokeColor
        strokeWidth = dp(context, 1)
        cardElevation = 0f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) }
    }

    @JvmStatic
    fun glassCard(context: Context, strokeColor: Int = BORDER): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(context, 14).toFloat()
        setCardBackgroundColor(SURFACE)
        this.strokeColor = strokeColor
        strokeWidth = dp(context, 1)
        cardElevation = 0f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) }
    }

    @JvmStatic
    fun badge(context: Context, text: String): TextView = badge(context, text, PRIMARY)

    @JvmStatic
    fun badge(context: Context, text: String, accent: Int): TextView = TextView(context).apply {
        this.text = text
        textSize = 11f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setTextColor(accent)
        background = rounded(context, 0x1A000000 or (accent and 0x00FFFFFF), accent, 999)
        setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
    }

    @JvmStatic
    fun hudBadge(context: Context, text: String, accent: Int = PRIMARY): TextView = TextView(context).apply {
        val cleanText = if (text.startsWith("● ")) text else "● $text"
        this.text = cleanText
        textSize = 10.5f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(accent)
        background = rounded(context, 0x18000000 or (accent and 0x00FFFFFF), 0x40000000 or (accent and 0x00FFFFFF), 6)
        setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    @JvmStatic
    fun statusPill(context: Context, label: String, color: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(context, SURFACE_2, BORDER, 999)
        setPadding(dp(context, 10), dp(context, 5), dp(context, 12), dp(context, 5))

        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 8), dp(context, 8)).apply {
                marginEnd = dp(context, 6)
            }
        }
        val text = TextView(context).apply {
            this.text = label
            textSize = 11.5f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        addView(dot)
        addView(text)
    }

    @JvmStatic
    fun techChip(context: Context, label: String, icon: String, onClick: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(context, SURFACE_2, BORDER, 8)
        setPadding(dp(context, 12), dp(context, 8), dp(context, 14), dp(context, 8))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        val iconView = TextView(context).apply {
            text = icon
            textSize = 13f
            setTextColor(PRIMARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, dp(context, 6), 0)
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(TEXT)
        }
        addView(iconView)
        addView(labelView)
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(context, 8) }
    }

    @JvmStatic
    fun vectorChip(context: Context, label: String, iconResId: Int, onClick: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(context, SURFACE_2, BORDER, 999)
        setPadding(dp(context, 12), dp(context, 8), dp(context, 14), dp(context, 8))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        val iconView = ImageView(context).apply {
            setImageResource(iconResId)
            setColorFilter(PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)).apply {
                marginEnd = dp(context, 6)
            }
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(TEXT)
        }
        addView(iconView)
        addView(labelView)
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(context, 8) }
    }

    @JvmStatic
    fun button(context: Context, text: String, onClick: () -> Unit): MaterialButton = MaterialButton(context).apply {
        this.text = text
        setTextColor(TEXT)
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(context, 42)
        minWidth = 0
        cornerRadius = dp(context, 10)
        strokeWidth = dp(context, 1)
        strokeColor = ColorStateList.valueOf(BORDER)
        backgroundTintList = ColorStateList.valueOf(SURFACE_2)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-2, dp(context, 42)).apply { marginEnd = dp(context, 8) }
    }

    @JvmStatic
    fun primaryButton(context: Context, text: String, onClick: () -> Unit): MaterialButton = button(context, text, onClick).apply {
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        strokeColor = ColorStateList.valueOf(PRIMARY)
        backgroundTintList = ColorStateList.valueOf(PRIMARY)
    }

    @JvmStatic
    fun secondaryButton(context: Context, text: String, onClick: () -> Unit): MaterialButton = button(context, text, onClick).apply {
        setTextColor(TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        strokeColor = ColorStateList.valueOf(BORDER)
        backgroundTintList = ColorStateList.valueOf(SURFACE_2)
    }

    @JvmStatic
    fun dangerButton(context: Context, text: String, onClick: () -> Unit): MaterialButton = button(context, text, onClick).apply {
        setTextColor(ERROR)
        strokeColor = ColorStateList.valueOf(0x40EF4444.toInt())
        backgroundTintList = ColorStateList.valueOf(0x20EF4444.toInt())
    }

    @JvmStatic
    fun input(context: Context, hint: String): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(context).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = PRIMARY
            setHintTextColor(ColorStateList.valueOf(MUTED))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) }
        }
        val edit = TextInputEditText(context).apply {
            setSingleLine(true)
            setTextColor(TEXT)
            setHintTextColor(MUTED)
        }
        til.addView(edit)
        return til to edit
    }

    @JvmStatic
    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(BORDER)
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1)).apply {
            topMargin = dp(context, 10)
            bottomMargin = dp(context, 10)
        }
    }

    @JvmStatic
    fun valuePair(context: Context, label: String, value: String): LinearLayout = row(context).apply {
        val l = TextView(context).apply { text = label; textSize = 12f; setTextColor(MUTED) }
        val v = TextView(context).apply { text = value; textSize = 12.5f; setTextColor(TEXT); gravity = Gravity.END }
        addView(l, LinearLayout.LayoutParams(0, -2, 1f))
        addView(v)
    }

    @JvmStatic
    fun rounded(context: Context, fill: Int, stroke: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), stroke)
    }

    @JvmStatic
    fun progress(context: Context): CircularProgressIndicator = CircularProgressIndicator(context).apply {
        isIndeterminate = true
        visibility = View.GONE
    }
}
