from __future__ import annotations

from fastapi import APIRouter

from app.runtime.llm_runtime_manager import get_llm_runtime_manager

router = APIRouter(
    prefix="/agent/runtime",
    tags=["agent-runtime"],
)


@router.get("/llm/status")
async def get_llm_runtime_status() -> dict:
    runtime_manager = get_llm_runtime_manager()
    return runtime_manager.status()