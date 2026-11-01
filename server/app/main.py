import asyncio
import logging
from typing import Dict, Any, List
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from app.config import settings
from app.models import ClientMessageType, ServerMessageType, QueryPayload, ActionResult
from app.tools_catalog import CANONICAL_TOOLS

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("JarvisServer")

app = FastAPI(
    title="J.A.R.V.I.S. Central Brain Server",
    version="1.0.0",
    description="High-performance backend offloading LLM reasoning and memory for Android J.A.R.V.I.S."
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}

    async def connect(self, device_id: str, websocket: WebSocket):
        await websocket.accept()
        self.active_connections[device_id] = websocket
        logger.info(f"Device connected: {device_id} (Total: {len(self.active_connections)})")

    def disconnect(self, device_id: str):
        if device_id in self.active_connections:
            del self.active_connections[device_id]
            logger.info(f"Device disconnected: {device_id}")

    async def send_json(self, device_id: str, message: Dict[str, Any]):
        if device_id in self.active_connections:
            await self.active_connections[device_id].send_json(message)

manager = ConnectionManager()

@app.get("/api/health")
async def health_check():
    return {
        "status": "online",
        "version": "1.0",
        "connected_devices": len(manager.active_connections),
        "active_provider": settings.DEFAULT_PROVIDER,
        "default_model": settings.DEFAULT_MODEL
    }

@app.get("/api/tools")
async def get_tools_catalog():
    return CANONICAL_TOOLS

@app.post("/api/chat")
async def chat_endpoint(payload: QueryPayload):
    # Standard REST fallback endpoint
    return {
        "success": True,
        "spoken_response": f"Processed query: {payload.utterance}",
        "session_id": payload.session_id
    }

@app.websocket("/ws/jarvis")
async def websocket_jarvis(
    websocket: WebSocket,
    device_id: str = Query(default="default_android_device"),
    token: str = Query(default="")
):
    if settings.AUTH_TOKEN and token != settings.AUTH_TOKEN:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await manager.connect(device_id, websocket)
    try:
        # Handshake greeting
        await websocket.send_json({
            "type": ServerMessageType.CONNECTED.value,
            "server_version": "1.0",
            "active_model": settings.DEFAULT_MODEL
        })

        while True:
            data = await websocket.receive_json()
            msg_type = data.get("type")

            if msg_type == ClientMessageType.PING.value:
                await websocket.send_json({
                    "type": ServerMessageType.PONG.value,
                    "timestamp": data.get("timestamp", 0)
                })

            elif msg_type == ClientMessageType.QUERY.value:
                utterance = data.get("utterance", "")
                session_id = data.get("session_id", "default")
                device_state = data.get("device_state", {})
                
                logger.info(f"Received query from {device_id}: '{utterance}'")
                
                # Check for fast hardware commands or dispatch
                lower = utterance.lower()
                if "torch" in lower or "flashlight" in lower:
                    state = "ON" if "on" in lower or "chalu" in lower or "jal" in lower else "OFF"
                    await websocket.send_json({
                        "type": ServerMessageType.ACTION_REQUEST.value,
                        "action_id": f"act_{asyncio.get_event_loop().time()}",
                        "action": "FLASHLIGHT",
                        "params": {"state": state},
                        "speech_preamble": f"Turning flashlight {state.lower()}."
                    })
                elif "call" in lower or "phone" in lower or "dial" in lower:
                    target = utterance.split()[-1]
                    await websocket.send_json({
                        "type": ServerMessageType.ACTION_REQUEST.value,
                        "action_id": f"act_{asyncio.get_event_loop().time()}",
                        "action": "TELEPHONY_CONTROL",
                        "params": {"action": "call", "target": target},
                        "speech_preamble": f"Calling {target}."
                    })
                else:
                    # Echo / basic conversational response stream
                    tokens = ["I ", "received ", "your ", "command: ", utterance]
                    for t in tokens:
                        await websocket.send_json({
                            "type": ServerMessageType.TOKEN.value,
                            "token": t
                        })
                        await asyncio.sleep(0.01)
                    
                    await websocket.send_json({
                        "type": ServerMessageType.COMPLETED.value,
                        "spoken_response": f"Understood: {utterance}",
                        "success": True
                    })

            elif msg_type == ClientMessageType.ACTION_RESULT.value:
                action_id = data.get("action_id")
                success = data.get("success", False)
                output = data.get("output", "")
                logger.info(f"Action {action_id} completed: success={success}, output={output}")
                
                await websocket.send_json({
                    "type": ServerMessageType.COMPLETED.value,
                    "spoken_response": output or "Action executed successfully.",
                    "success": success
                })

    except WebSocketDisconnect:
        manager.disconnect(device_id)
    except Exception as e:
        logger.error(f"WebSocket error on {device_id}: {e}")
        manager.disconnect(device_id)
