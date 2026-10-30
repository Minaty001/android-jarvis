package com.jarvis.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jarvis.R

class ResponsiveNav(context: Context, private val onSelect: (String) -> Unit) : LinearLayout(context) {

    data class NavItem(val id: String, val label: String, val iconRes: Int)

    private val navButtons = mutableMapOf<String, Triple<View, ImageView, TextView>>()
    private var selectedRoute = "Dashboard"

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Ui.SURFACE)
        setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 6))
        elevation = Ui.dp(context, 8).toFloat()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 62))

        val items = listOf(
            NavItem("Dashboard", "Chat", R.drawable.ic_nav_chat),
            NavItem("Skills", "Skills", R.drawable.ic_nav_skills),
            NavItem("Routines", "Routines", R.drawable.ic_nav_routines),
            NavItem("Settings", "Settings", R.drawable.ic_nav_settings)
        )

        items.forEach { item ->
            val tab = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = Ui.rounded(context, Color.TRANSPARENT, Color.TRANSPARENT, 8)
                contentDescription = "Open ${item.label}"
            }
            val indicator = View(context).apply {
                background = Ui.rounded(context, Ui.PRIMARY, Color.TRANSPARENT, 2)
                layoutParams = LayoutParams(Ui.dp(context, 24), Ui.dp(context, 2)).apply { bottomMargin = Ui.dp(context, 4) }
                visibility = if (item.id == selectedRoute) View.VISIBLE else View.INVISIBLE
            }
            val icon = ImageView(context).apply {
                setImageResource(item.iconRes)
                val iconSize = Ui.dp(context, 22)
                layoutParams = LayoutParams(iconSize, iconSize)
                val color = if (item.id == selectedRoute) Ui.PRIMARY else Ui.MUTED
                imageTintList = ColorStateList.valueOf(color)
            }
            val label = TextView(context).apply {
                text = item.label
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                letterSpacing = 0.02f
                setTextColor(if (item.id == selectedRoute) Ui.TEXT else Ui.MUTED)
                setPadding(0, Ui.dp(context, 2), 0, 0)
            }
            tab.addView(indicator)
            tab.addView(icon)
            tab.addView(label)
            tab.setOnClickListener { setRoute(item.id); onSelect(item.id) }
            navButtons[item.id] = Triple(indicator, icon, label)
            addView(tab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    fun setRoute(route: String) {
        selectedRoute = route
        navButtons.forEach { (id, views) ->
            val selected = id == route
            views.first.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            val color = if (selected) Ui.PRIMARY else Ui.MUTED
            views.second.imageTintList = ColorStateList.valueOf(color)
            views.third.setTextColor(if (selected) Ui.TEXT else Ui.MUTED)
        }
    }
}
