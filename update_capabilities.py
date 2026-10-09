with open("docs/JARVIS_COMPLETE_CAPABILITIES.md", "a") as f:
    f.write("""

## 11. 💰 Smart Financial Ledger & Tracker
### Features & Functions:
* **Financial Tracker Engine**: Automatically intercepts and parses banking SMS messages to record income, expenses, and current balance.
* **ExpenseReportTool**: Local intelligence tool capable of generating insights from the financial database.
* **On-Device Logging**: All financial records are kept in a local Room database with a visual clearance UI in Settings.

### 5 Examples per Feature:
* "How much did I spend today?"
* "What is my current bank balance?"
* "Did I receive any salary credits this week?"
* "Show my total expenses for this month."
* "Clear my financial transaction history."

## 12. 🔐 Next-Level Security & Passive Memory
### Features & Functions:
* **JarvisSecurityManager**: Intercepts all outgoing LLM requests to mask PII (e.g., SSN, Passwords) and intercepts incoming tools to block destructive commands unless explicitly consented.
* **Passive Memory Engine**: Silently logs passive context (what app you are using, what you are reading) securely in a local background database for future contextual grounding.
* **Persistent Conversational Memory**: Compresses and retains conversation history across restarts.
* **Comprehensive Permissions Hub**: A 100% transparent visual Settings interface to audit every Android OS permission Jarvis requires, including deep links to Special system access.

### 5 Examples per Feature:
* "What article was I just reading 10 minutes ago?"
* "Are my permissions properly configured?"
* "Delete the habit logs you've learned about me."
* "Forget our conversation history."
* "Do I have any critical security alerts?"

## 13. 🕹️ UI Autopilot & Screen Actions
### Features & Functions:
* **Deep Screen Vision Extraction**: `ScreenContextAnalyzer` constructs a hierarchical tree of your screen, bypassing traditional OCR for exact UI understanding.
* **Programmatic Screen Actions**: Can synthesize physical clicks, scrolls, and text input on third-party apps completely autonomously via the Accessibility Service.
* **Proactive Continuous Messaging**: A persistent `ConversationManager` handles multi-turn tasks (e.g., waiting 5 minutes for a reply and following up).

### 5 Examples per Feature:
* "Scroll down this page."
* "Click the send button."
* "Type 'I will be there in 5 mins' into the chat box."
* "Keep texting him until he replies."
* "Navigate through the settings menu."
""")
