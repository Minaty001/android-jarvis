import asyncio
import logging
from typing import Dict, Any, List
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from app.config import settings
from app.models import ClientMessageType, ServerMessageType, QueryPayload, ActionResult, DeviceState
from app.tools_catalog import CANONICAL_TOOLS
from app.memory_vault import MemoryVault
from app.agent_planner import AgentPlanner

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
memory_vault = MemoryVault()
agent_planner = AgentPlanner()

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
    decision = await agent_planner.decide_next_step(
        utterance=payload.utterance,
        history=memory_vault.get_history(payload.session_id),
        device_state=payload.device_state
    )
    memory_vault.remember_turn(payload.session_id, "user", payload.utterance)
    
    if decision.action_request:
        return {
            "success": True,
            "action_request": decision.action_request.model_dump(),
            "spoken_response": decision.action_request.speech_preamble,
            "session_id": payload.session_id
        }
    else:
        resp = decision.spoken_response or "Processed successfully."
        memory_vault.remember_turn(payload.session_id, "assistant", resp)
        return {
            "success": True,
            "spoken_response": resp,
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
    active_session_id = f"sess_{device_id}"
    
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
                session_id = data.get("session_id", active_session_id)
                raw_state = data.get("device_state") or {}
                device_state = DeviceState(**raw_state) if isinstance(raw_state, dict) and raw_state else None
                
                logger.info(f"Query from {device_id}: '{utterance}'")
                memory_vault.remember_turn(session_id, "user", utterance)
                
                decision = await agent_planner.decide_next_step(
                    utterance=utterance,
                    history=memory_vault.get_history(session_id),
                    device_state=device_state
                )
                
                if decision.action_request:
                    # Device action execution needed on Android
                    await websocket.send_json({
                        "type": ServerMessageType.ACTION_REQUEST.value,
                        **decision.action_request.model_dump()
                    })
                else:
                    # Conversational response stream (real-time LLM or counter-question)
                    if decision.decision_type == "CLARIFY" or not (llm_router.groq_key or llm_router.gemini_key or llm_router.openrouter_key):
                        tokens = (decision.spoken_response or "").split(" ")
                        for t in tokens:
                            await websocket.send_json({
                                "type": ServerMessageType.TOKEN.value,
                                "token": t + " "
                            })
                            await asyncio.sleep(0.01)
                        full_resp = decision.spoken_response or "Understood."
                    else:
                        # Full conversational banter & dynamic cognitive response from LLM
                        system_prompt = agent_planner.build_conversational_system_prompt(device_state=device_state)
                        messages = [{"role": "system", "content": system_prompt}]
                        messages.extend(memory_vault.get_history(session_id))
                        full_chunks = []
                        async for token in llm_router.chat_stream(messages):
                            full_chunks.append(token)
                            await websocket.send_json({
                                "type": ServerMessageType.TOKEN.value,
                                "token": token
                            })
                        full_resp = "".join(full_chunks).strip() or "Understood."
                    memory_vault.remember_turn(session_id, "assistant", full_resp)
                    await websocket.send_json({
                        "type": ServerMessageType.COMPLETED.value,
                        "spoken_response": full_resp,
                        "success": True
                    })

            elif msg_type == ClientMessageType.ACTION_RESULT.value:
                action_id = data.get("action_id", "")
                success = data.get("success", False)
                output = data.get("output", "")
                error_msg = data.get("error_message")
                
                logger.info(f"Action {action_id} result: success={success}, output={output}")
                action_res = ActionResult(
                    action_id=action_id,
                    success=success,
                    output=output,
                    error_message=error_msg
                )
                
                spoken_final = await agent_planner.finalize_action(
                    original_goal="",
                    action_name="Action",
                    action_result=action_res
                )
                memory_vault.remember_turn(active_session_id, "assistant", spoken_final)
                
                await websocket.send_json({
                    "type": ServerMessageType.COMPLETED.value,
                    "spoken_response": spoken_final,
                    "success": success
                })

    except WebSocketDisconnect:
        manager.disconnect(device_id)
    except Exception as e:
        logger.error(f"WebSocket error on {device_id}: {e}")
        manager.disconnect(device_id)
