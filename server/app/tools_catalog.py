from typing import List, Dict, Any

CANONICAL_TOOLS: List[Dict[str, Any]] = [
    {
        "name": "FLASHLIGHT",
        "description": "Toggles device torch/flashlight on or off.",
        "parameters": {
            "state": {"type": "string", "enum": ["ON", "OFF", "TOGGLE"], "required": True}
        }
    },
    {
        "name": "WHATSAPP",
        "description": "Sends a WhatsApp text message to a contact.",
        "parameters": {
            "recipient": {"type": "string", "required": True, "description": "Contact name or phone number"},
            "message": {"type": "string", "required": True, "description": "Text message content"}
        }
    },
    {
        "name": "TELEPHONY_CONTROL",
        "description": "Places or answers phone calls, dials numbers, or reads call logs.",
        "parameters": {
            "action": {"type": "string", "enum": ["call", "dial", "answer", "reject"], "required": True},
            "target": {"type": "string", "description": "Phone number or contact name"}
        }
    },
    {
        "name": "SMS_CONTROL",
        "description": "Sends or reads text messages (SMS) and OTPs.",
        "parameters": {
            "action": {"type": "string", "enum": ["send", "read_latest", "extract_otp"], "required": True},
            "recipient": {"type": "string"},
            "message": {"type": "string"}
        }
    },
    {
        "name": "APP_OPEN",
        "description": "Launches an application installed on the Android device.",
        "parameters": {
            "app": {"type": "string", "required": True, "description": "Application name (e.g. YouTube, Spotify, Chrome)"}
        }
    },
    {
        "name": "APP_CLOSE",
        "description": "Closes a foreground application or returns to home screen.",
        "parameters": {
            "app": {"type": "string", "description": "Optional package name or 'all'"}
        }
    },
    {
        "name": "MEDIA_PLAY",
        "description": "Searches and plays music or podcasts on Spotify/YouTube/MediaSession.",
        "parameters": {
            "query": {"type": "string", "required": True, "description": "Song title, artist, or playlist"},
            "platform": {"type": "string", "default": "spotify"}
        }
    },
    {
        "name": "MEDIA_CONTROL",
        "description": "Controls active media playback (play, pause, next, previous).",
        "parameters": {
            "command": {"type": "string", "enum": ["play", "pause", "next", "previous", "stop"], "required": True}
        }
    },
    {
        "name": "DEVICE_SETTINGS",
        "description": "Adjusts hardware device settings such as volume, brightness, Wi-Fi, and Bluetooth.",
        "parameters": {
            "setting": {"type": "string", "enum": ["volume", "brightness", "wifi", "bluetooth", "airplane_mode"], "required": True},
            "value": {"type": "string", "required": True, "description": "e.g. '80%', 'on', 'off'"}
        }
    },
    {
        "name": "BATTERY_CHECK",
        "description": "Checks current battery level and charging state.",
        "parameters": {}
    },
    {
        "name": "CAMERA_PERCEPTION",
        "description": "Captures a camera snapshot and describes objects, OCR text, or scene.",
        "parameters": {
            "prompt": {"type": "string", "description": "Specific question about what the camera sees"}
        }
    },
    {
        "name": "CALENDAR_MANAGE",
        "description": "Reads upcoming events or schedules meetings on device calendar.",
        "parameters": {
            "action": {"type": "string", "enum": ["list", "add", "delete"], "required": True},
            "title": {"type": "string"},
            "time": {"type": "string"}
        }
    },
    {
        "name": "SCREEN_VISION",
        "description": "Inspects on-screen content and active application context.",
        "parameters": {
            "query": {"type": "string", "description": "What to look for on screen"}
        }
    },
    {
        "name": "SEARCH_WEB",
        "description": "Searches the web for latest real-time information.",
        "parameters": {
            "query": {"type": "string", "required": True}
        }
    }
]
