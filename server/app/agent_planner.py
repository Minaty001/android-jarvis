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
    decision_type: str = "ACT"  # "ACT" or "ANSWER"
    action_request: Optional[ActionRequest] = None
    spoken_response: Optional[str] = None

class AgentPlanner:
    def __init__(self, llm_router: Optional[LlmRouter] = None):
        self.router = llm_router or LlmRouter()

    async def decide_next_step(
        self,
        utterance: str,
        history: List[Dict[str, str]],
        device_state: Optional[DeviceState] = None
    ) -> PlannerDecision:
        lower = utterance.lower().strip()

        # 1. Flashlight / Torch Pattern Matching (Bilingual EN/HI)
        if any(w in lower for w in ["torch", "flashlight", "light"]):
            state = "ON" if any(w in lower for w in ["on", "chalu", "jal", "start"]) else "OFF"
            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="FLASHLIGHT",
                    params={"state": state},
                    speech_preamble=f"Turning flashlight {state.lower()}."
                )
            )

        # 2. Telephony / Call Pattern Matching
        if any(w in lower for w in ["call", "phone", "dial"]):
            # Extract target name/number
            target = "contact"
            parts = lower.split()
            if "ko" in parts:
                idx = parts.index("ko")
                if idx > 0:
                    target = parts[idx - 1].capitalize()
            elif len(parts) >= 2:
                target = parts[-1].capitalize()

            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="TELEPHONY_CONTROL",
                    params={"action": "call", "target": target},
                    speech_preamble=f"Calling {target} now."
                )
            )

        # 3. WhatsApp Pattern Matching
        if "whatsapp" in lower:
            recipient = "contact"
            parts = lower.split()
            if "ko" in parts:
                idx = parts.index("ko")
                if idx > 0:
                    recipient = parts[idx - 1].capitalize()
            
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
                    params={"recipient": recipient, "message": message},
                    speech_preamble=f"Sending WhatsApp message to {recipient}."
                )
            )

        # 4. App Launch Pattern Matching
        if any(w in lower for w in ["open", "launch", "kholo"]):
            app_name = "YouTube"
            for word in ["youtube", "spotify", "chrome", "settings", "camera", "whatsapp", "instagram"]:
                if word in lower:
                    app_name = word.capitalize()
                    break

            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="APP_OPEN",
                    params={"app": app_name},
                    speech_preamble=f"Opening {app_name}."
                )
            )

        # 5. Media Playback Controls
        if any(w in lower for w in ["play", "bajao", "gaana", "song"]):
            query = utterance.replace("play", "").replace("bajao", "").replace("gaana", "").strip()
            if not query:
                query = "top hits"
            return PlannerDecision(
                decision_type="ACT",
                action_request=ActionRequest(
                    action_id=f"act_{uuid.uuid4().hex[:8]}",
                    action="MEDIA_PLAY",
                    params={"query": query},
                    speech_preamble=f"Playing {query}."
                )
            )

        # Default conversational Q&A
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
