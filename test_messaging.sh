#!/bin/bash
adb logcat -c
adb shell am broadcast -a com.jarvis.action.EXECUTE_COMMAND -n com.jarvis/.service.CommandReceiver -e command "'text John I will be late'"
sleep 3
adb logcat -d > debug_sms.log
cat debug_sms.log | grep -i "intent"
