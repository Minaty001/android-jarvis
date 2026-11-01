from enum import Enum
from typing import Dict, Any, Optional, List
from pydantic import BaseModel, Field

class ClientMessageType(str, Enum):
    QUERY = "query"
    ACTION_RESULT = "action_result"
    PING = "ping"
    DEVICE_STATE = "device_state"
    REGISTER_DEVICE = "register_device"

class ServerMessageType(str, Enum):
    CONNECTED = "connected"
    TOKEN = "token"
    ACTION_REQUEST = "action_request"
    COMPLETED = "completed"
    ERROR = "error"
    PONG = "pong"

class DeviceState(BaseModel):
    battery: int = 100
    is_charging: bool = False
    foreground_app: Optional[str] = None
    wifi_connected: bool = True
    active_network: str = "WIFI"

class ActionRequest(BaseModel):
    action_id: str
    action: str
    params: Dict[str, Any] = Field(default_factory=dict)
    speech_preamble: Optional[str] = None

class ActionResult(BaseModel):
    action_id: str
    success: bool
    output: str = ""
    error_message: Optional[str] = None

class QueryPayload(BaseModel):
    session_id: str = "default_session"
    utterance: str
    device_state: Optional[DeviceState] = None
