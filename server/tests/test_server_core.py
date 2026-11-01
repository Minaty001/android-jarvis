import pytest
from fastapi.testclient import TestClient

def test_health_check_returns_ok():
    from app.main import app
    client = TestClient(app)
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "online"
    assert "version" in data
    assert "active_provider" in data

def test_tools_catalog_returns_tools():
    from app.main import app
    client = TestClient(app)
    response = client.get("/api/tools")
    assert response.status_code == 200
    tools = response.json()
    assert isinstance(tools, list)
    assert len(tools) > 0
    # Spot-check essential Android tools are present in catalog
    tool_names = [t["name"] for t in tools]
    assert "FLASHLIGHT" in tool_names
    assert "WHATSAPP" in tool_names
    assert "TELEPHONY_CONTROL" in tool_names

def test_websocket_ping_pong():
    from app.main import app
    client = TestClient(app)
    with client.websocket_connect("/ws/jarvis?device_id=test_dev") as websocket:
        # First message on connect should be CONNECTED handshake
        greeting = websocket.receive_json()
        assert greeting["type"] == "connected"
        assert greeting["server_version"] == "1.0"
        
        # Send PING
        websocket.send_json({"type": "ping", "timestamp": 123456789})
        response = websocket.receive_json()
        assert response["type"] == "pong"
