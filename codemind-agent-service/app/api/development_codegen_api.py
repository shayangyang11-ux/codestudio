from __future__ import annotations

from fastapi import APIRouter

from app.schemas.development_codegen import (
    DevelopmentNodejsCodegenRequest,
    DevelopmentNodejsCodegenResponse,
    DevelopmentNodejsRepairRequest,
)
from app.services.development_code_generation_service import DevelopmentCodeGenerationService

router = APIRouter(
    prefix="/agent/development/codegen",
    tags=["development-codegen"],
)


@router.post("/nodejs/proposal", response_model=DevelopmentNodejsCodegenResponse)
async def generate_nodejs_code_proposal(
    request: DevelopmentNodejsCodegenRequest,
) -> DevelopmentNodejsCodegenResponse:
    service = DevelopmentCodeGenerationService()
    return await service.generate_nodejs_proposal(request)




@router.post("/nodejs/repair", response_model=DevelopmentNodejsCodegenResponse)
async def repair_nodejs_code_from_acceptance(
    request: DevelopmentNodejsRepairRequest,
) -> DevelopmentNodejsCodegenResponse:
    service = DevelopmentCodeGenerationService()
    return await service.repair_nodejs_from_acceptance(request)
