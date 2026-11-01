#!/usr/bin/env python3
"""
End-to-End Integration Test for J.A.R.V.I.S. Backend Server & Client Protocol.
Simulates an Android client connecting, receiving actions, and streaming tokens.
"""

import sys
import json
import asyncio
import httpx
import websockets

SERVER_URL = "http://127.0.0.1:8000"
WS_URL = "ws://127.0.0.1:8000/ws/jarvis?device_id=e2e_test_device"

async def test_backend_e2e():
    print("=== 1. Testing REST Health Endpoint ===")
    async with httpx.AsyncClient() as client:
        res = await client.get(f"{SERVER_URL}/api/health")
        assert res.status_code == 200, f"Health check failed: {res.status_code}"
        data = res.json()
        print(f"Health OK: status={data['status']}, provider={data['active_provider']}")

    print("\n=== 2. Testing WebSocket Handshake & Fast Action Protocol ===")
    async with websockets.connect(WS_URL) as ws:
        # Handshake
        greeting = json.loads(await ws.recv())
        assert greeting["type"] == "connected"
        print(f"Handshake OK: version={greeting['server_version']}, model={greeting['active_model']}")

        # Test Ping/Pong
        await ws.send(json.dumps({"type": "ping", "timestamp": 12345}))
        pong = json.loads(await ws.recv())
        assert pong["type"] == "pong"
        print("Ping/Pong OK")

        # Test Hardware Tool Action Request
        query = {
            "type": "query",
            "session_id": "e2e_session",
            "utterance": "Turn on flashlight",
            "device_state": {"battery": 90, "is_charging": False, "wifi_connected": True}
        }
        await ws.send(json.dumps(query))
        action_req = json.loads(await ws.recv())
        assert action_req["type"] == "action_request", f"Expected action_request, got {action_req}"
        assert action_req["action"] == "FLASHLIGHT"
        assert action_req["params"]["state"] == "ON"
        print(f"Action Request OK: action={action_req['action']}, params={action_req['params']}")

        # Simulate Phone Tool Execution and Return Action Result
        action_result = {
            "type": "action_result",
            "action_id": action_req["action_id"],
            "success": True,
            "output": "Flashlight turned on successfully"
        }
        await ws.send(json.dumps(action_result))
        completed = json.loads(await ws.recv())
        assert completed["type"] == "completed"
        print(f"Action Completed OK: spoken_response='{completed['spoken_response']}'")

        # Test Conversational Token Streaming
        query_conv = {
            "type": "query",
            "session_id": "e2e_session",
            "utterance": "Tell me a joke",
            "device_state": {"battery": 90}
        }
        await ws.send(json.dumps(query_conv))
        
        tokens_received = []
        while True:
            msg = json.loads(await ws.recv())
            if msg["type"] == "token":
                tokens_received.append(msg["token"])
            elif msg["type"] == "completed":
                print(f"Stream Completed: received {len(tokens_received)} token chunks")
                print(f"Spoken response: {msg['spoken_response']}")
                break

    print("\n✅ All End-to-End Integration Tests Passed Successfully!")

if __name__ == "__main__":
    asyncio.run(test_backend_e2e())
