from __future__ import annotations

from fastapi import APIRouter

from app.agents.style.style_assistant_agent import StyleAssistantAgent
from app.schemas.style_assistant import StyleAssistantAgentRequest, StyleAssistantAgentResponse

router = APIRouter(prefix="/agent/style", tags=["Style Assistant"])
_agent = StyleAssistantAgent()


@router.post("/assistant/analyze", response_model=StyleAssistantAgentResponse)
async def analyze_style_assistant(
    request: StyleAssistantAgentRequest,
) -> StyleAssistantAgentResponse:
    return await _agent.run(request)
