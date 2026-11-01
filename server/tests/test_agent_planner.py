import pytest
import asyncio
from app.models import DeviceState, ActionResult
from app.memory_vault import MemoryVault
from app.agent_planner import AgentPlanner

@pytest.mark.asyncio
async def test_memory_vault_store_and_retrieve(tmp_path):
    db_path = str(tmp_path / "test_memory.db")
    vault = MemoryVault(db_path=db_path)
    
    vault.remember_turn("sess_1", "user", "Remember my car is at B2")
    vault.remember_turn("sess_1", "assistant", "I will remember that your car is parked at level B2.")
    
    history = vault.get_history("sess_1")
    assert len(history) == 2
    assert history[0]["content"] == "Remember my car is at B2"
    assert history[1]["role"] == "assistant"
    
    # Store explicit fact
    vault.set_fact("car_parking", "Level B2")
    assert vault.get_fact("car_parking") == "Level B2"

@pytest.mark.asyncio
async def test_agent_planner_direct_action_decision():
    planner = AgentPlanner()
    device_state = DeviceState(battery=80, is_charging=False)
    
    # Query: flashlight on
    decision = await planner.decide_next_step(
        utterance="Turn on the flashlight",
        history=[],
        device_state=device_state
    )
    assert decision.action_request is not None
    assert decision.action_request.action == "FLASHLIGHT"
    assert decision.action_request.params.get("state") == "ON"

@pytest.mark.asyncio
async def test_agent_planner_telephony_action_decision():
    planner = AgentPlanner()
    device_state = DeviceState(battery=90)
    
    # Query: Call Rahul
    decision = await planner.decide_next_step(
        utterance="Rahul ko phone lagao",
        history=[],
        device_state=device_state
    )
    assert decision.action_request is not None
    assert decision.action_request.action == "TELEPHONY_CONTROL"
    assert decision.action_request.params.get("action") == "call"
    assert "Rahul" in decision.action_request.params.get("target", "")

@pytest.mark.asyncio
async def test_agent_planner_conclude_after_action():
    planner = AgentPlanner()
    action_result = ActionResult(action_id="act_1", success=True, output="Flashlight turned ON")
    
    response = await planner.finalize_action(
        original_goal="Turn on flashlight",
        action_name="FLASHLIGHT",
        action_result=action_result
    )
    assert "Flashlight turned on" in response or "Flashlight turned ON" in response
