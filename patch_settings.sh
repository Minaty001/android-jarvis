#!/bin/bash

# Insert new section in init block (before line "// 5. Diagnostics")
awk '
/^\s*\/\/ 5. Diagnostics \& Event Logs Section/ {
    print "        // Data & Permissions Hub"
    print "        root.addView(Ui.sectionLabel(context, \"JARVIS DATA & PERMISSIONS\"))"
    print "        val dataActions = Ui.row(context).apply {"
    print "            setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 14))"
    print "        }"
    print "        dataActions.addView(Ui.button(context, \"Permissions\") { openPermissionsDialog() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.dp(context, 4) })"
    print "        dataActions.addView(Ui.button(context, \"Habits\") { openHabitsDialog() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.dp(context, 4) })"
    print "        dataActions.addView(Ui.button(context, \"Finance Logs\") { openFinanceDialog() }, LinearLayout.LayoutParams(0, -2, 1f))"
    print "        root.addView(dataActions)"
    print ""
    print $0
    next
}
{ print }
' app/src/main/java/com/jarvis/ui/screens/SettingsScreen.kt > SettingsScreen.tmp && mv SettingsScreen.tmp app/src/main/java/com/jarvis/ui/screens/SettingsScreen.kt

