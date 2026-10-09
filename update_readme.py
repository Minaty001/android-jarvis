import re

with open("README.md", "r") as f:
    content = f.read()

new_sections = """
<details>
<summary><b>6. 🤖 UI Autopilot & Deep Screen Vision</b></summary>

- **ScreenContextAnalyzer**: Reads hierarchical Accessibility Node data to infer the current user activity (e.g., "Booking a flight").
- **UI Autopilot Engine**: Maps high-level LLM intents into physical UI interactions (clicks, scrolling, typing) completely autonomously.
</details>

<details>
<summary><b>7. 💰 Smart Financial Ledger & Security</b></summary>

- **Financial Tracker Engine**: Automatically tracks SMS bank receipts to categorize spending without needing bank API access.
- **JarvisSecurityManager**: On-device toxicity and PII masking to ensure sensitive data (passwords, SSNs) never leaves the device.
- **Comprehensive Permissions Hub**: Centralized Settings UI for managing advanced access and permissions.
</details>
"""

# Insert right before the closing --- before Bilingual Command Reference
content = re.sub(r'(\n---\n\n## 🗣️ Bilingual Command Reference)', r'\n' + new_sections + r'\1', content)

# Update the voice commands table
table_additions = """| **UI Autopilot**| *"Click the send button"*, *"Scroll down"*, *"Type hello"* | *"Send button dabao"*, *"Niche scroll karo"*, *"Hello type karo"* |
| **Financial Ledger**| *"How much did I spend today"*, *"Clear my finances"* | *"Aaj kitna kharcha hua"*, *"Finance logs clear karo"* |"""

content = re.sub(r'(\| \*\*Autopilot Modes\*\*\|.*?\n)', r'\1' + table_additions + '\n', content)

with open("README.md", "w") as f:
    f.write(content)

