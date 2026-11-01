import pytest
from app.models import DeviceState, ActionResult
from app.agent_planner import AgentPlanner

@pytest.mark.asyncio
async def test_agent_planner_counter_question_for_ambiguous_target():
    planner = AgentPlanner()
    decision = await planner.decide_next_step(
        utterance="Call karo",
        history=[]
    )
    # Ambiguous command should generate a counter question rather than a broken action
    assert decision.decision_type in ["ANSWER", "CLARIFY"]
    assert any(w in decision.spoken_response.lower() for w in ["kisko", "whom", "who", "kise", "naam"])

@pytest.mark.asyncio
async def test_agent_planner_proactive_battery_warning():
    planner = AgentPlanner()
    low_battery_state = DeviceState(battery=14, is_charging=False)
    
    decision = await planner.decide_next_step(
        utterance="YouTube par gaana chalao",
        history=[],
        device_state=low_battery_state
    )
    # Action should still be requested, but speech preamble should note dependency/battery advice
    assert decision.action_request is not None
    assert decision.action_request.action in ["APP_OPEN", "MEDIA_PLAY"]
    assert "battery" in decision.action_request.speech_preamble.lower() or "14%" in decision.action_request.speech_preamble

@pytest.mark.asyncio
async def test_agent_planner_conversational_banter_prompt():
    planner = AgentPlanner()
    system_prompt = planner.build_conversational_system_prompt(
        device_state=DeviceState(battery=75, is_charging=True)
    )
    assert "JARVIS" in system_prompt
    assert "Hinglish" in system_prompt or "Hindi" in system_prompt
    assert "wit" in system_prompt.lower() or "humor" in system_prompt.lower()
