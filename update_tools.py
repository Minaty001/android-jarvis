import re

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "r") as f:
    wt = f.read()

wt = wt.replace("\"custom\" to AutoReplyMode.CUSTOM,", "\"custom\" to AutoReplyMode.CUSTOM,\n                        \"ai\" to AutoReplyMode.AI_SMART_REPLY, \"smart\" to AutoReplyMode.AI_SMART_REPLY,")
wt = wt.replace("engine.setAutoReplyMode(targetMode, template)", "com.jarvis.messaging.AutoReplyPolicyManager.setMode(targetMode, template)")

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "w") as f:
    f.write(wt)

with open("app/src/main/java/com/jarvis/tools/TelephonyTool.kt", "r") as f:
    tt = f.read()

tt = tt.replace("\"custom\" to com.jarvis.messaging.AutoReplyMode.CUSTOM,", "\"custom\" to com.jarvis.messaging.AutoReplyMode.CUSTOM,\n                    \"ai\" to com.jarvis.messaging.AutoReplyMode.AI_SMART_REPLY, \"smart\" to com.jarvis.messaging.AutoReplyMode.AI_SMART_REPLY,")

with open("app/src/main/java/com/jarvis/tools/TelephonyTool.kt", "w") as f:
    f.write(tt)

