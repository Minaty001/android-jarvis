import re
import uuid
import logging
from typing import List, Dict, Any, Optional
from pydantic import BaseModel
from app.models import ActionRequest, ActionResult, DeviceState
from app.llm_router import LlmRouter
from app.tools_catalog import CANONICAL_TOOLS

logger = logging.getLogger("AgentPlanner")

class PlannerDecision(BaseModel):
    decision_type: str = "ACT"  # "ACT", "ANSWER", or "CLARIFY"
    action_request: Optional[ActionRequest] = None
    spoken_response: Optional[str] = None

class AgentPlanner:
    def __init__(self, llm_router: Optional[LlmRouter] = None):
        self.router = llm_router or LlmRouter()

    def build_conversational_system_prompt(self, device_state: Optional[DeviceState] = None) -> str:
        state_info = "Device state normal."
        if device_state:
            state_info = (
                f"Battery: {device_state.battery}% (Charging: {device_state.is_charging}), "
                f"Wi-Fi: {'Connected' if device_state.wifi_connected else 'Disconnected'}."
            )

        return (
            "You are J.A.R.V.I.S., an advanced, witty, and highly capable AI companion inspired by Iron Man's JARVIS. "
            "You converse naturally with the user in fluid, code-switching Hinglish (seamlessly matching Hindi, English, or mixed Hinglish). "
            "Your personality combines sharp intelligence with natural humor, subtle wit, and friendly banter like a trusted partner. "
            f"Ambient context: {state_info}\n"
            "Rules for voice-to-voice conversation:\n"
            "1. Keep responses punchy, concise, and conversational (1 to 3 short sentences max).\n"
            "2. If a request is vague, ask a crisp counter-question to clarify intent.\n"
            "3. If the user is casual, joke back with playful banter.\n"
            "4. Advise on dependencies proactively when relevant."
        )

    async def decide_next_step(
        self,
        utterance: str,
        history: List[Dict[str, str]],
        device_state: Optional[DeviceState] = None
    ) -> PlannerDecision:
        lower = utterance.lower().strip()

        # Helper for proactive dependency advice
        def get_dependency_advice(tag: str = "") -> str:
            if not device_state:
                return ""
            warnings = []
            if device_state.battery <= 20 and not device_state.is_charging:
                warnings.append(f"Par sir battery sirf {device_state.battery}% hai.")
            if not device_state.wifi_connected and tag in ["media", "app"]:
                warnings.append("Wi-Fi connected nahi hai.")
            return (" " + " ".join(warnings)) if warnings else ""

        # 1. Flashlight / Torch Pattern Matching (Bilingual EN/HI)
        if any(w in lower for w in ["torch", "flashlight", "light"]):
            state = "ON" if any(w in lower for w in ["on", "chalu", "jal", "start"]) else "OFF"
            preamble = f"Turning flashlight {state.lower()}." + get_dependency_advice()
            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="FLASHLIGHT",
                    params={"state": state, "mode": state.lower()},
                    speech_preamble=preamble.strip()
                )
            )

        # 2. Telephony / Call Pattern Matching
        if any(w in lower for w in ["call", "phone", "dial"]):
            # Filter auxiliary words to check if a real target exists
            words = [w for w in lower.split() if w not in ["karo", "lagao", "karna", "please", "zara", "kar", "phone", "call", "dial"]]
            target = ""
            parts = lower.split()
            if "ko" in parts:
                idx = parts.index("ko")
                if idx > 0 and parts[idx - 1] not in ["call", "phone"]:
                    target = parts[idx - 1].capitalize()
            elif words:
                target = words[-1].capitalize()

            # Ambiguous call command without recipient
            if not target:
                return PlannerDecision(
                    decision_type="CLARIFY",
                    spoken_response="Kisko call lagau sir? Kripya naam ya number batayein."
                )

            preamble = f"Calling {target} now." + get_dependency_advice()
            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="TELEPHONY_CONTROL",
                    params={"action": "call", "target": target},
                    speech_preamble=preamble.strip()
                )
            )

        # 3. WhatsApp Pattern Matching
        if "whatsapp" in lower or "message" in lower:
            recipient = ""
            parts = lower.split()
            if "ko" in parts:
                idx = parts.index("ko")
                if idx > 0 and parts[idx - 1] not in ["whatsapp", "message"]:
                    recipient = parts[idx - 1].capitalize()
            
            # Ambiguous message command without recipient
            if not recipient and any(w in lower for w in ["bhejo", "send", "karo", "likho"]):
                return PlannerDecision(
                    decision_type="CLARIFY",
                    spoken_response="Kisko message bhejna hai sir, aur kya message hai?"
                )

            message = "I am on my way"
            if "saying" in lower:
                message = lower.split("saying")[-1].strip()
            elif "bol" in lower or "bhejo" in lower:
                message = utterance.strip()

            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="WHATSAPP",
                    params={"recipient": recipient or "contact", "message": message},
                    speech_preamble=f"Sending WhatsApp message to {recipient or 'contact'}."
                )
            )

        # 4. App Launch Pattern Matching
        if any(w in lower for w in ["open", "launch", "kholo", "chalao"]):
            app_name = "YouTube"
            for word in ["youtube", "spotify", "chrome", "settings", "camera", "whatsapp", "instagram"]:
                if word in lower:
                    app_name = word.capitalize()
                    break

            preamble = f"Opening {app_name}." + get_dependency_advice("app")
            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="APP_OPEN",
                    params={"app": app_name},
                    speech_preamble=preamble.strip()
                )
            )

        # 5. Media Playback Controls
        if any(w in lower for w in ["play", "bajao", "gaana", "song"]):
            query = utterance.replace("play", "").replace("bajao", "").replace("gaana", "").replace("song", "").strip()
            if not query:
                query = "top hits"
            preamble = f"Playing {query}." + get_dependency_advice("media")
            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="MEDIA_PLAY",
                    params={"query": query},
                    speech_preamble=preamble.strip()
                )
            )

        # Default conversational Q&A / Banter
        return PlannerDecision(
            decision_type="ANSWER",
            spoken_response=f"I have received your request: {utterance}"
        )

    async def finalize_action(
        self,
        original_goal: str,
        action_name: str,
        action_result: ActionResult
    ) -> str:
        if action_result.success:
            if action_result.output:
                return f"{action_result.output}"
            return f"{action_name} executed successfully."
        else:
            return f"Failed to execute {action_name}: {action_result.error_message or 'Unknown error'}"
