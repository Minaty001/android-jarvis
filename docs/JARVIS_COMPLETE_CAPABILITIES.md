# 🤖 J.A.R.V.I.S. — Complete Capabilities, Features, & Action Guide (With 5 Examples Each)

> **Implementation note:** The examples are illustrative, not a guarantee that every action, provider fallback, response time, or tool name is supported by the current build. Check `docs/JARVIS_LOCAL_FEATURES_AND_COMMANDS.md` and the registered tools in source. The main memory store uses SQLite FTS4. A separate document RAG pipeline combines BM25 with lexical and optional hashed character n-gram similarity; it is not a semantic LLM embedding store.

This document provides a comprehensive, easy-to-understand guide to all **features, functions, systems, tasks, and actions** supported by J.A.R.V.I.S., complete with **5 real-world voice commands / usage examples for every single feature**.

---

## 📌 Quick Summary of Core Systems

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            J.A.R.V.I.S. BRAIN                               │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ 🎙️ Voice & Acoustic Engine           │ 🧠 Multi-Provider AI & SQLite Memory │
│  • Continuous Wake-Word ("Jarvis")   │  • Groq / Gemini / OpenRouter / Ollama│
│  • Wake-Word Dataset & KWS Diagnostics│  • FTS4 Retrieval & Conversation Memory│
│  • Full-Duplex Barge-In Interrupt    │  • ReAct Multi-Step Task Planner     │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ 📱 OS & Hardware Automation          │ 👁️ Vision & Accessibility Automator │
│  • Flashlight, Wi-Fi, Bluetooth, Vol │  • Camera2 Object & OCR Perception   │
│  • Lock-Screen Overlay HUD           │  • Autonomous UI Screen Navigator    │
│  • Hands-Free Telephony & SMS OTP    │  • 3-Tier WhatsApp Auto-Sender       │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

---

## 1. 🎙️ Voice Recognition & Acoustic Engine

### Features & Functions:
* **Continuous Wake-Word Detection**: A single continuous microphone path feeds openWakeWord shared frontend feature models (`melspectrogram.tflite`, `embedding_model.tflite`) and classifier heads (`Hey Jarvis` upstream and custom `Jarvis`) verified via `openwakeword_models.json`.
* **Lock-Screen HUD Overlay**: Displays cyber-slate HUD on lock screen without unlocking.
* **Personal Dataset Enrollment**: Saves quality-checked positive WAV samples and exports them for offline model training. Recording samples does not change model weights or the model threshold.
* **Optional Owner Voice Verification**: A separate identity check, disabled by default. If explicitly required, unavailable model assets fail closed.
* **Full-Duplex Acoustic Barge-In**: Silence Jarvis mid-speech by speaking over it.
* **Bluetooth Earbud SCO Routing**: Bidirectional earbud audio routing and headset hook button activation.

### 5 Examples per Feature:

#### A. Wake-Word Activation:
1. *"Jarvis, good morning"*
2. *"Jarvis, volume set to 70%"*
3. *"Jarvis, what is the weather today?"*
4. *"Jarvis, turn on flashlight"*
5. *"Jarvis, call Mom"*

#### B. Lock-Screen HUD Overlay:
1. With a trained model installed, speak *"Jarvis"* while phone is locked $\to$ Arc Reactor HUD turns screen on and listens.
2. Speak *"What time is it?"* from across the room while phone is on desk locked.
3. With a trained model installed, speak *"Jarvis, turn on driving mode"* while phone is locked in car mount.
4. With a trained model installed, speak *"Jarvis, play music"* without touching the lock screen code.
5. With a trained model installed, speak *"Jarvis, read latest SMS"* when screen is off.

#### C. Personal Wake-Word Dataset (`CONFIG` screen):
1. Open `PERSONAL WAKE WORD DATASET` and record the default 50 `Jarvis` samples, varying volume and distance.
2. The app quality-checks each recording and stores it as 16 kHz mono PCM16 WAV. If a real model is installed, the same runtime detector can score the saved sample; otherwise the UI says `MODEL UNAVAILABLE`.
3. Export the dataset ZIP. Add real `unknown` and `background` examples on the development machine.
4. Run `scripts/train_kws.py` and `scripts/validate_kws_model.py`; install the generated INT8 asset and metadata to activate the newly trained model.

#### D. Full-Duplex Acoustic Barge-In (Interrupting Jarvis):
1. While Jarvis reads a long briefing, say *"Jarvis, stop"* $\to$ Requests TTS interruption (latency depends on device and audio route).
2. While Jarvis explains weather, say *"Skip"* $\to$ Halts playback immediately.
3. While Jarvis speaks news, say *"Quiet"* $\to$ Mutes and waits for next input.
4. While Jarvis reads SMS, say *"Jarvis"* $\to$ Intercepts audio and opens mic.
5. While Jarvis answers math, say *"Chup ho jao"* $\to$ Silences TTS instantly.

#### E. Bluetooth Earbud SCO & Button Interception:
1. Click earbud physical button once while idle $\to$ Triggers active voice listening.
2. Click earbud button while Jarvis is speaking $\to$ Physical barge-in mutes speech.
3. Whisper command softly into earbud mic while phone is in pocket.
4. Answer incoming call hands-free through Galaxy Buds / AirPods mic.
5. Disconnect Bluetooth earbud $\to$ Audio seamlessly routes back to phone speaker.

---

## 2. 🧠 AI Reasoning & Local Memory

### Features & Functions:
* **Conversation context**: Combines the current request with recent persisted conversation and available memory context.
* **SQLite FTS4 memory**: Persists facts and task artifacts and ranks keyword candidates by lexical overlap, importance, recency, and usage. Document RAG is a separate indexed pipeline.
* **Session summaries**: Generated asynchronously only when an LLM client is available; empty or `NONE` output is ignored.
* **Multi-Provider LLM Router**: Automatic fallback between Groq, Gemini, OpenRouter, and Ollama.
* **Fast-Path Intent Routing**: Some common actions can be handled locally without an LLM request; latency depends on the action and device.

### 5 Examples per Feature:

#### A. Conversation Context (illustrative examples):
1. User: *"My name is Vikram"* $\to$ Jarvis: *"Got it, Vikram."*
2. User: *"What is my name?"* $\to$ Jarvis: *"Your name is Vikram."*
3. User: *"I am going to Delhi tomorrow"* $\to$ Jarvis remembers location in turn history.
4. User: *"Where am I going tomorrow?"* $\to$ Jarvis: *"You are going to Delhi."*
5. User: *"Summarize our conversation so far"* $\to$ Uses available recent conversation context.

#### B. Persistent SQLite Memory (keyword retrieval):
1. Save a note: *"Office Wi-Fi details are in my notes"* $\to$ Stored locally if accepted by memory tooling.
2. Later ask: *"What are my office Wi-Fi details?"* $\to$ Retrieval depends on keyword overlap and saved content.
3. Ingest note: *"Doctor appointment is on Friday at 4 PM at Max Hospital"*.
4. Query: *"Where is my doctor appointment?"* $\to$ Returns *"Max Hospital"*.
5. Ingest: *"My blood group is O positive"*. Query: *"What is my blood group?"* $\to$ Returns *"O positive"*.

#### C. `REMEMBER` Voice Tool Ingestion:
1. *"Jarvis, remember my gate code is 4921"*
2. *"Jarvis, remember that my car is parked on Level B2 Slot 14"*
3. *"Jarvis, remember my passport renewal date is August 2027"*
4. *"Jarvis, remember Rahul's birthday is on 15th October"*
5. *"Jarvis, yaad rakhna mera locker code 8832 hai"*

#### D. Multi-Provider LLM Fallback:
1. Primary Groq API active $\to$ Processes prompt via `openai/gpt-oss-20b` in 300ms.
2. Groq key limit exceeded $\to$ Automatically routes to Gemini `gemini-2.5-flash`.
3. Offline mode $\to$ Routes query to local Ollama LLM (`http://10.0.2.2:11434`).
4. Complex reasoning goal $\to$ Routes to `Groq-Strong` (`openai/gpt-oss-120b`).
5. Multimodal vision query $\to$ Routes to `qwen/qwen3.6-27b`.

#### E. Fast-Path Intent Routing (illustrative; no timing benchmark):
1. *"Turn on flashlight"* $\to$ May use a local hardware tool without an LLM call.
2. *"Set volume to 50%"* $\to$ May use a local device settings tool.
3. *"Check battery percentage"* $\to$ May use a local battery status tool.
4. *"Pause music"* $\to$ May use Android media controls.
5. *"What time is it?"* $\to$ May be handled by a local clock tool.

---

## 3. 📞 Hands-Free Telephony & SMS Agent

### 5 Examples per Feature:

#### A. Incoming Caller Announcement & Answering:
1. Incoming call rings $\to$ Jarvis announces: *"Incoming call from Rahul. Say answer or reject."*
2. Voice: *"Answer"* $\to$ Answers call hands-free.
3. Voice: *"Phone uthao"* $\to$ Answers call in Hinglish.
4. Voice: *"Reject call"* $\to$ Declines incoming call.
5. Voice: *"Call kaat do"* $\to$ Declines incoming call in Hinglish.

#### B. Voice Dialing & Contact Resolution:
1. *"Call Mom"* $\to$ Resolves "Mom" in contacts and dials number.
2. *"Phone lagao Priya ko"* $\to$ Dials contact "Priya".
3. *"Dial 9876543210"* $\to$ Directly opens dialer with raw digits.
4. *"Call Dr. Sharma"* $\to$ Finds matching contact in address book.
5. *"Redial last number"* $\to$ Calls most recent call log entry.

#### C. SMS Reading & OTP Extraction:
1. Bank SMS arrives $\to$ *"Jarvis, what is my OTP?"* $\to$ *"Your OTP is 482910"*.
2. *"OTP batao"* $\to$ Extracts latest 6-digit code from SMS.
3. *"Read latest SMS"* $\to$ Reads content of most recent text message out loud.
4. *"SMS padho"* $\to$ Reads unread SMS in Hinglish.
5. *"Check 2FA code"* $\to$ Extracts authentication PIN from Google/Amazon SMS.

#### D. Sending SMS via Voice:
1. *"Send SMS to Rahul saying I am on my way"*
2. *"Send SMS to Mom saying I reached safely"*
3. *"Papa ko SMS bhejo main 10 minute me pahunch raha hoon"*
4. *"Send text to 9876543210 saying Meeting confirmed"*
5. *"Send SMS to Alex saying Please send the report"*

---

## 4. 💬 Autonomous WhatsApp Subsystem

### 5 Examples per Feature:

#### A. Tier-1 Notification Quick-Reply:
1. Incoming WhatsApp message from Alex $\to$ Jarvis replies in background without opening app screen.
2. Driving mode active $\to$ Automatically quick-replies: *"I am driving right now"*.
3. Meeting mode active $\to$ Automatically quick-replies: *"In a meeting until 4 PM"*.
4. *"Reply to Alex saying I will call you later"* $\to$ Sends background notification reply.
5. Auto-reply toggle off $\to$ Notifications logged quietly.

#### B. Tier-2 Accessibility Auto-Send:
1. *"Send WhatsApp to Priya I am on my way"* $\to$ Opens chat link & auto-clicks Send.
2. *"Priya ko WhatsApp karo 5 minute me aa raha hoon"* $\to$ Auto-dispatches chat in Hindi selector.
3. *"Send WhatsApp to Work Group Meeting started"* $\to$ Types & auto-taps Send button.
4. Auto-returns to home screen (`GLOBAL_ACTION_HOME`) immediately after dispatching message.
5. Logs execution status if button is obscured.

#### C. In-App WhatsApp Search Fallback:
1. Contact not in recent chats $\to$ Jarvis opens WhatsApp, taps Search icon, types contact name.
2. Selects contact from search result list.
3. Pastes message into chat box.
4. Taps Send button via accessibility node tree.
5. Returns to Jarvis dashboard.

#### D. Voice Commands for WhatsApp:
1. *"Read my WhatsApp messages"*
2. *"WhatsApp padho"*
3. *"Turn on WhatsApp auto reply driving"*
4. *"Turn off WhatsApp auto reply"*
5. *"Check unread WhatsApp messages"*

---

## 5. 🎵 Music & Universal Media Controls

### 5 Examples per Feature:

#### A. Transport Controls:
1. *"Play music"* $\to$ Resumes last active media session.
2. *"Pause music"* / *"Gaana roko"* $\to$ Pauses playback.
3. *"Next track"* / *"Agla gaana"* $\to$ Skips to next song.
4. *"Previous track"* $\to$ Rewinds to previous song.
5. *"Stop music"* / *"Song band karo"* $\to$ Halts media playback.

#### B. Genre Preset Chips (`SKILLS` Screen):
1. Tap `Synthwave` chip $\to$ Launches Spotify Synthwave playlist.
2. Tap `Lo-Fi Chill` chip $\to$ Launches Lo-Fi chill beats radio.
3. Tap `Deep Focus` chip $\to$ Starts ambient focus music stream.
4. Tap `Top Hits` chip $\to$ Launches current chart toppers.
5. Tap `Workout Mix` chip $\to$ Plays high-energy gym mix.

#### C. Automatic Background Media Volume Ducking:
1. Music playing at 100% volume $\to$ Say *"Jarvis"* $\to$ Music volume smoothly ducks to 25%.
2. Jarvis speaks TTS response $\to$ Music stays ducked so voice is crystal clear.
3. Jarvis finishes speaking $\to$ Music volume smoothly restores to 100%.
4. Configure ducking level in `CONFIG` screen (Select 10%, 20%, 30%, or 50%).
5. Toggle Media Ducking ON/OFF in settings.

---

## 6. 👁️ Camera Vision & On-Screen Intelligence

### 5 Examples per Feature:

#### A. Multimodal Camera Perception:
1. Point camera at object $\to$ *"What am I looking at?"* $\to$ *"That is a Sony WH-1000XM4 wireless headset."*
2. Point camera at plant $\to$ *"Ye kya hai"* $\to$ Identifies plant species.
3. Point camera at book page $\to$ *"Read this text"* $\to$ Extracts text via OCR and reads it aloud.
4. Point camera at food dish $\to$ *"Identify this food"* $\to$ Identifies dish name and estimated calories.
5. *"Selfie scan"* $\to$ Switches to front camera and describes selfie view.

#### B. Autonomous UI Screen Navigator (`AutonomousUiNavigator`):
1. *"Screen per dekho kya najar aa raha hai"* $\to$ Captures active screen & describes UI elements.
2. *"Click on Subscribe button"* $\to$ Finds Subscribe node coordinates & taps it.
3. *"Scroll down"* $\to$ Executes swipe gesture down screen.
4. *"Open YouTube and search for Lo-Fi"* $\to$ Taps search bar, types text, presses enter.
5. *"Go home"* $\to$ Executes `GLOBAL_ACTION_HOME`.

#### C. Foreground App Sessions:
- Jarvis refreshes its active-app context from Android's accessibility window events and current active window for WhatsApp, Instagram, Chrome, Telegram, and YouTube.
- Follow-ups like *"scroll down"* and *"search for hiking boots"* use the foreground app's accessibility UI. *"Read the screen"* captures the currently displayed screen.
- Enable **Jarvis Accessibility Service** in Android Accessibility settings for app detection and shared UI controls. YouTube playback commands continue to use the dedicated playback action.

---

## 7. ⚡ Hardware & OS System Controls (5 Examples Each)

#### A. Flashlight:
1. *"Turn on flashlight"*
2. *"Flashlight band karo"*
3. *"Torch on"*
4. *"Turn off torch"*
5. *"Flashlight chalu karo"*

#### B. Wi-Fi:
1. *"Turn on Wi-Fi"*
2. *"Wi-Fi band karo"*
3. *"Turn off Wi-Fi"*
4. *"Wi-Fi chalu karo"*
5. *"Check Wi-Fi connection status"*

#### C. Bluetooth:
1. *"Turn on Bluetooth"*
2. *"Bluetooth band karo"*
3. *"Turn off Bluetooth"*
4. *"Bluetooth chalu karo"*
5. *"Connect to Bluetooth earbud"*

#### D. Volume & Mute:
1. *"Set volume to 80%"*
2. *"Awaaz tez karo"*
3. *"Mute phone"*
4. *"Set volume to max"*
5. *"Awaaz kam karo"*

#### E. Battery Diagnostics:
1. *"Check battery level"*
2. *"Battery kitni hai"*
3. *"Is the phone charging?"*
4. *"Show battery status"*
5. *"How much battery is left?"*

#### F. System Actions & Screenshots:
1. *"Take screenshot"*
2. *"Screenshot lo"*
3. *"Close all apps"*
4. *"Open Settings"*
5. *"Open YouTube"*

#### G. Natural Language Calculator (`NL_CALC`):
1. *"What is 18 percent of 5400?"* $\to$ *"972"*
2. *"Calculate 144 divided by 12"* $\to$ *"12"*
3. *"What is 250 multiplied by 4?"* $\to$ *"1000"*
4. *"5400 ka 18 percent kitna hoga"* $\to$ *"972"*
5. *"What is 15% tip on 80 dollars?"* $\to$ *"12 dollars"*

---

## 8. 🌅 Proactive Intelligence & Executive Briefings

### 5 Examples per Feature:

#### A. Executive Daily Morning Briefing:
1. Say *"Good morning"* $\to$ Jarvis speaks full briefing (meetings, weather, battery, reminders).
2. Say *"Daily briefing sunao"* $\to$ Speaks morning briefing in Hinglish.
3. Schedule alarm for `07:30 AM` in `CONFIG` $\to$ Jarvis wakes system & speaks briefing automatically at 7:30 AM.
4. Say *"Aaj ka schedule kya hai"* $\to$ Reads today's agenda.
5. Say *"Briefing cancel"* $\to$ Aborts ongoing briefing.

#### B. Proactive Calendar & Battery Alerts:
1. Meeting starts at 4:00 PM $\to$ At 3:45 PM Jarvis speaks: *"Boss, Team Sync starts in 15 minutes."*
2. Battery drops to 15% $\to$ Jarvis speaks: *"Boss, battery level is critically low at 15%. Please connect charger."*
3. Battery reaches 100% $\to$ Jarvis speaks: *"Battery is fully charged at 100%."*
4. Adjust calendar briefing window to 5m, 10m, 15m, or 30m in `CONFIG`.
5. Duplicate alert suppression prevents repeating meeting warnings.

---

## 9. 🛡️ Autopilot & Situational Modes

### 5 Examples per Feature:

#### A. Driving Mode:
1. Voice: *"Driving mode on"* $\to$ Enables driving profile.
2. Auto-reply set to: *"I am driving right now, will call you back later."*
3. Hands-free call announcement prioritized through Bluetooth earbuds.
4. Non-essential app notifications silenced.
5. Voice: *"Turn off driving mode"* $\to$ Restores normal profile.

#### B. Meeting Mode:
1. Voice: *"Meeting mode on"* $\to$ Silences volume & activates meeting profile.
2. Auto-reply set to: *"In a meeting until 3 PM."*
3. Audio ducked to minimum.
4. Calls logged silently.
5. Voice: *"Meeting mode off"* $\to$ Restores normal volume.

---

## 10. 🛠️ Complete Registered Tools List (5 Examples Each for All 19 Tools)

1. **`REMEMBER`**:
   - Ingest: *"REMEMBER(fact='Gate code is 4921')"*
   - Ingest: *"REMEMBER(fact='Car parked at slot B2')"*
   - Ingest: *"REMEMBER(fact='Locker PIN is 8832')"*
   - Ingest: *"REMEMBER(fact='Passport expires in 2027')"*
   - Ingest: *"REMEMBER(fact='Doctor appointment on Friday 4pm')"*

2. **`SPEAK`**:
   - Execute: *"SPEAK(text='Good morning sir, all systems nominal')"*
   - Execute: *"SPEAK(text='Flashlight turned on')"*
   - Execute: *"SPEAK(text='Your OTP is 482910')"*
   - Execute: *"SPEAK(text='Battery level is 85%')"*
   - Execute: *"SPEAK(text='Calling Rahul now')"*

3. **`NOTE`**:
   - Save note: *"NOTE(content='Buy milk and eggs on way home')"*
   - Save note: *"NOTE(content='Project deadline extended to Monday')"*
   - Save note: *"NOTE(content='Idea: Build Android AI Assistant')"*
   - Save note: *"NOTE(content='Meeting notes: Q3 goals approved')"*
   - Save note: *"NOTE(content='Car service scheduled for Saturday')"*

4. **`SEARCH_MEMORY`**:
   - Search: *"SEARCH_MEMORY(query='gate code')"*
   - Search: *"SEARCH_MEMORY(query='car parking')"*
   - Search: *"SEARCH_MEMORY(query='passport')"*
   - Search: *"SEARCH_MEMORY(query='Wi-Fi password')"*
   - Search: *"SEARCH_MEMORY(query='doctor appointment')"*

5. **`WHATSAPP`**:
   - Send: *"WHATSAPP(action='send_message', recipient='Priya', message='On my way')"*
   - Read: *"WHATSAPP(action='read_messages')"*
   - Auto-reply: *"WHATSAPP(action='auto_reply', mode='driving')"*
   - Off: *"WHATSAPP(action='auto_reply', mode='off')"*
   - Open: *"WHATSAPP(action='open')"*

6. **`TELEPHONY_CONTROL`**:
   - Call: *"TELEPHONY_CONTROL(action='call', recipient='Mom')"*
   - Dial: *"TELEPHONY_CONTROL(action='dial', number='9876543210')"*
   - Answer: *"TELEPHONY_CONTROL(action='answer')"*
   - Reject: *"TELEPHONY_CONTROL(action='reject')"*
   - Read SMS: *"TELEPHONY_CONTROL(action='read_sms')"*

7. **`CAMERA_VISION`**:
   - Perception: *"CAMERA_VISION(mode='object_detection')"*
   - OCR: *"CAMERA_VISION(mode='ocr_read_text')"*
   - Selfie: *"CAMERA_VISION(mode='selfie_scan')"*
   - Plant ID: *"CAMERA_VISION(mode='identify_plant')"*
   - Document scan: *"CAMERA_VISION(mode='scan_document')"*

8. **`SCREEN_VISION`**:
   - Describe UI: *"SCREEN_VISION(action='inspect_ui')"*
   - Extract text: *"SCREEN_VISION(action='ocr_screen')"*
   - Find button: *"SCREEN_VISION(action='find_element', label='Subscribe')"*
   - Active app: *"SCREEN_VISION(action='get_active_app')"*
   - Context check: *"SCREEN_VISION(action='verify_screen_state')"*

9. **`RAG_INDEX` / `RAG_RETRIEVE`**:
   - Index: *"RAG_INDEX(document='Company policy details...')"*
   - Query: *"RAG_RETRIEVE(query='leave policy')"*
   - Index: *"RAG_INDEX(document='User manual notes...')"*
   - Query: *"RAG_RETRIEVE(query='reset instructions')"*
   - Query: *"RAG_RETRIEVE(query='warranty duration')"*

10. **`SPOTIFY_PLAY`**:
    - Play track: *"SPOTIFY_PLAY(query='Bohemian Rhapsody')"*
    - Play playlist: *"SPOTIFY_PLAY(playlist='Synthwave Chill')"*
    - Pause: *"SPOTIFY_PLAY(action='pause')"*
    - Next: *"SPOTIFY_PLAY(action='next')"*
    - Genre: *"SPOTIFY_PLAY(genre='Deep Focus')"*

11. **`ROUTINE_MANAGE`**:
    - Create: *"ROUTINE_MANAGE(action='create', name='Bedtime Routine')"*
    - Run: *"ROUTINE_MANAGE(action='execute', name='Morning Briefing')"*
    - List: *"ROUTINE_MANAGE(action='list')"*
    - Delete: *"ROUTINE_MANAGE(action='delete', name='Test Routine')"*
    - Update: *"ROUTINE_MANAGE(action='toggle', name='Driving Mode')"*

12. **`MACRO_WORKFLOW`**:
    - Run macro: *"MACRO_WORKFLOW(macro_id='auto_like_shorts')"*
    - Run macro: *"MACRO_WORKFLOW(macro_id='clear_notifications')"*
    - Run macro: *"MACRO_WORKFLOW(macro_id='open_spotify_and_play')"*
    - Run macro: *"MACRO_WORKFLOW(macro_id='mute_and_dark_mode')"*
    - List macros: *"MACRO_WORKFLOW(action='list')"*

13. **`APP_AUTOPILOT`**:
    - Open app: *"APP_AUTOPILOT(app='YouTube', goal='Search Lo-Fi Music')"*
    - Open app: *"APP_AUTOPILOT(app='Chrome', goal='Navigate to news site')"*
    - Open app: *"APP_AUTOPILOT(app='WhatsApp', goal='Send message to Alex')"*
    - Open app: *"APP_AUTOPILOT(app='Settings', goal='Open Display settings')"*
    - Open app: *"APP_AUTOPILOT(app='Maps', goal='Find nearest petrol pump')"*

14. **`FILE_MANAGER`**:
    - Inspect disk: *"FILE_MANAGER(action='disk_usage')"*
    - Search file: *"FILE_MANAGER(action='search', query='report.pdf')"*
    - Cleanup: *"FILE_MANAGER(action='suggest_cleanup')"*
    - Locate downloads: *"FILE_MANAGER(action='list_downloads')"*
    - File size check: *"FILE_MANAGER(action='check_apk_sizes')"*

15. **`SECURITY_AUDITOR`**:
    - Audit permissions: *"SECURITY_AUDITOR(action='audit_permissions')"*
    - Check keystore: *"SECURITY_AUDITOR(action='verify_keystore')"*
    - Scan risk: *"SECURITY_AUDITOR(action='scan_risk_levels')"*
    - Encryption test: *"SECURITY_AUDITOR(action='test_aes_gcm')"*
    - Security posture report: *"SECURITY_AUDITOR(action='generate_report')"*

16. **`CALENDAR`**:
    - Query agenda: *"CALENDAR(action='get_agenda', date='today')"*
    - Add event: *"CALENDAR(action='add_event', title='Doctor Appointment', time='16:00')"*
    - Check tomorrow: *"CALENDAR(action='get_agenda', date='tomorrow')"*
    - Delete event: *"CALENDAR(action='delete_event', id='102')"*
    - Next meeting: *"CALENDAR(action='next_meeting')"*

17. **`REMINDER_SCHEDULER`**:
    - Schedule alarm: *"REMINDER_SCHEDULER(time='07:30', title='Wake Up Alarm')"*
    - Schedule reminder: *"REMINDER_SCHEDULER(time='14:00', title='Water plants')"*
    - List reminders: *"REMINDER_SCHEDULER(action='list')"*
    - Cancel reminder: *"REMINDER_SCHEDULER(action='cancel', id='12')"*
    - Quick timer: *"REMINDER_SCHEDULER(duration='10m', title='Tea timer')"*

18. **`LOCATION`**:
    - Get GPS: *"LOCATION(action='get_current_location')"*
    - Nearby places: *"LOCATION(action='search_nearby', query='petrol pump')"*
    - Nearby food: *"LOCATION(action='search_nearby', query='restaurants')"*
    - Geocode: *"LOCATION(action='resolve_address', query='India Gate')"*
    - Distance: *"LOCATION(action='distance_to', destination='Airport')"*

19. **`SYSTEM_SWITCHBOARD`**:
    - Toggle Flashlight: *"SYSTEM_SWITCHBOARD(target='flashlight', state='on')"*
    - Toggle Wi-Fi: *"SYSTEM_SWITCHBOARD(target='wifi', state='off')"*
    - Toggle Bluetooth: *"SYSTEM_SWITCHBOARD(target='bluetooth', state='on')"*
    - Mute: *"SYSTEM_SWITCHBOARD(target='volume', level='0')"*
    - Set volume: *"SYSTEM_SWITCHBOARD(target='volume', level='80')"*


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

## 14. 🎭 Emotional Intelligence & Affective Voice
### Features & Functions:
* **Deterministic Sentiment Evaluation**: Analyzes user tone, valence, and urgency locally without cloud latency.
* **Pure Emotional State Transition**: Adapts assistant persona across 7 modes (`CALM`, `FOCUSED`, `EMPATHETIC`, `ALERT`, `CONFUSED`, `WITTY`, `CELEBRATORY`).
* **Holographic Arc Reactor Adaptation**: Dynamically alters `JarvisOrbView` core color, pulse frequency, and particle glow to reflect mood, respecting user reduced-motion settings.
* **Affective Voice Profile Modulation**: Tailors TTS pitch, rate, and prosody to conversational context.

### 5 Examples per Feature:
* "How are you feeling right now, Jarvis?"
* "I had an exhausting day, cheer me up."
* "Calm down, everything is under control."
* "Tell me a witty observation about today's agenda."
* "Why are you pulsing red?"

## 15. 🛡️ Jarvis Cognitive Core (JCI) & Multi-Step Task Execution
### Features & Functions:
* **Sub-Millisecond Direct Dispatch**: Executes deterministic single-step hardware and system toggles in <1.5ms with 0 tokens.
* **Multi-Step Dependency Planning**: Decomposes complex natural language goals into sequential/parallel dependency graphs with automatic rollback.
* **Strict Honesty Verification**: Enforces 4-tier verification (`L1_LOCAL_READ`, `L2_OS_CONFIRMED`, `L3_SYNTHETIC_OBSERVED`, `L4_DISPATCHED_ONLY`), prohibiting false "done" claims.
* **Contextual Quiet Hours**: Automatically suppresses non-critical proactive alerts between 22:00 and 08:00 while allowing life-critical emergency triggers.

### 5 Examples per Feature:
* "Execute task: check battery, turn off Wi-Fi, and dim the screen."
* "Status of all active cognitive capabilities."
* "Wipe all user data" (Triggers unconditional safe refusal: *"I cannot comply with destructive commands, sir."*)
* "Did you actually send that message or just submit it?"
* "What is your confidence in this decision?"
