import re

with open("app/src/main/java/com/jarvis/ai/IntentModel.kt", "r") as f:
    content = f.read()

replacement = """            val mode = when {
                text.contains("off") || text.contains("stop") || text.contains("band") -> "off"
                text.contains("driving") || text.contains("drive") -> "driving"
                text.contains("meeting") || text.contains("meet") -> "meeting"
                text.contains("ai") || text.contains("smart") -> "ai"
                text.contains("busy") -> "busy"
                else -> "driving"
            }"""

content = re.sub(r"            val mode = when \{.*?\n            \}", replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/jarvis/ai/IntentModel.kt", "w") as f:
    f.write(content)

