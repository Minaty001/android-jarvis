import sys

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "r") as f:
    content = f.read()

old_prompt = 'val prompt = "You are JARVIS, an intelligent assistant. Read the following WhatsApp notification dump. Summarize the messages naturally in 1-2 sentences. If any message seems URGENT, explicitly mention it. Finally, suggest 2-3 short context-aware auto-reply options. Dump:\\\\n${result.message}"'
new_prompt = 'val prompt = "You are JARVIS. Read this notification dump. Summarize naturally. If URGENT, say so. Suggest 2-3 auto-replies. Extract concrete action items, dates, or tasks from these messages and format them as `<ACTION: task details>`. Dump:\\\\n${result.message}"'

content = content.replace(old_prompt, new_prompt)

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "w") as f:
    f.write(content)

