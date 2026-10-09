import sys

with open("app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt", "r") as f:
    content = f.read()

old_schema = """                    com.jarvis.foundation.ParameterSchema("sender", "string", "Filter messages by sender name", required = false),
                    com.jarvis.foundation.ParameterSchema("limit", "integer", "Maximum messages to read (1-10)", required = false)"""

new_schema = """                    com.jarvis.foundation.ParameterSchema("sender", "string", "Filter messages by sender name", required = false),
                    com.jarvis.foundation.ParameterSchema("limit", "integer", "Maximum messages to read (1-10)", required = false),
                    com.jarvis.foundation.ParameterSchema("visual", "boolean", "If true, visually read chat from screen", required = false)"""

content = content.replace(old_schema, new_schema)

with open("app/src/main/java/com/jarvis/runtime/AssistantRuntime.kt", "w") as f:
    f.write(content)
