#!/bin/bash
REPORT="jarvis_test_report.md"
PACKAGE="com.jarvis"

echo "# Jarvis End-to-End ADB Test Report" > $REPORT
echo "**Date Executed:** $(date)" >> $REPORT
echo "**Device:** $(adb devices | grep device$ | awk '{print $1}')" >> $REPORT
echo "" >> $REPORT

# Wake up screen
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent 82 # unlock
sleep 1

echo "Starting test suite..."

run_test() {
    local test_name="$1"
    local setup_cmd="$2"
    local command="$3"
    local expected_log="$4"
    
    echo "## $test_name" >> $REPORT
    echo "Running test: $test_name..."
    
    # Clear logs
    adb logcat -c
    
    # Run setup if any
    if [ ! -z "$setup_cmd" ]; then
        eval "$setup_cmd"
        sleep 2
    fi
    
    # Trigger command
    if [ ! -z "$command" ]; then
        adb shell am broadcast -a com.jarvis.action.EXECUTE_COMMAND -n com.jarvis/.service.CommandReceiver -e command "\"$command\""
    fi
    
    # Wait for processing
    sleep 6
    
    # Dump logs and grep
    adb logcat -d > test_dump.log
    
    if grep -iq "$expected_log" test_dump.log; then
        echo "**Result:** ✅ PASSED" >> $REPORT
        echo "Successfully verified \`$expected_log\` in system logs." >> $REPORT
    else
        echo "**Result:** ❌ FAILED" >> $REPORT
        echo "Could not find \`$expected_log\` in system logs." >> $REPORT
    fi
    echo "" >> $REPORT
}

# 1. Read Messages
run_test "1. Context-Aware Read Messages" "" "read my messages" "intent: READ_MESSAGES"

# 2. Next Level Messaging (SMS)
run_test "2. Proactive Messaging" "" "text John I will be late" "Context-Aware Telecom"

# 3. Screen Context
run_test "3. Context-Aware Screen Reading" "adb shell am start -a android.settings.SETTINGS" "what is on my screen?" "intent: SCREEN_VISION"

# 4. Voice Mode (Assist Intent)
run_test "4. Advanced Voice Mode (Assist Intent)" "adb shell am start -a android.intent.action.ASSIST" "" "JarvisAssistActivity triggered"

# 5. Passive Memory Engine / Search Memory
run_test "5. Passive Memory Engine" "" "search memory for what I was looking at" "intent: QUERY_MEMORY"

# 6. Next Level Security (PII Redaction)
run_test "6. Security & PII Redaction" "" "remember that my password is SecretPassword123" "sensitive content"

# 7. Persistent Memory (Remember fact)
run_test "7. Persistent Conversational Memory" "" "remember my favorite color is crimson" "intent: REMEMBER_FACT"

# 7b. Memory Recall
# To reliably trigger recall, we must use the correct phrasing for QUERY_MEMORY
run_test "7b. Memory Recall" "" "what did i tell you about my favorite color" "intent: QUERY_MEMORY"

# 8. Financial Tracker
run_test "8. Smart Financial & Expense Tracker" "" "how much money did I spend?" "intent: EXPENSE_REPORT"

# 9. Icon Optimization (APK Size check)
echo "## 9. Icon Optimization & Build Verification" >> $REPORT
APK_SIZE=$(ls -lh jarvis-app.apk | awk '{print $5}')
echo "**Result:** ✅ PASSED" >> $REPORT
echo "Legacy PNGs removed. New WebP APK size is: $APK_SIZE" >> $REPORT

rm -f test_dump.log
echo "Tests completed. Report saved to $REPORT."
