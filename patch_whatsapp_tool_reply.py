import sys

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "r") as f:
    content = f.read()

old_prompt = 'val prompt = "You are JARVIS. Read this notification dump. Summarize naturally. If URGENT, say so. Suggest 2-3 auto-replies. Extract concrete action items, dates, or tasks from these messages and format them as `<ACTION: task details>`. Dump:\\\\n${result.message}"'
new_prompt = 'val prompt = "You are JARVIS. Read this notification dump. Summarize naturally. If URGENT, say so. Extract concrete action items, dates, or tasks and format them as `<ACTION: task details>`. Suggest 2 short auto-replies. YOU MUST explicitly end your response by asking: \\"Would you like to reply?\\". Dump:\\\\n${result.message}"'

content = content.replace(old_prompt, new_prompt)

with open("app/src/main/java/com/jarvis/tools/WhatsAppTool.kt", "w") as f:
    f.write(content)

