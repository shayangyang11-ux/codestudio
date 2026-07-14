
from fastapi import APIRouter

from app.graphs.requirement_graph import RequirementGraph
from app.schemas.agent_protocol import AgentTaskRequest, AgentTaskResponse
from app.agents.requirement.module_tree_patch_agent import ModuleTreePatchAgent
from app.agents.requirement.assistant_intent_agent import AssistantIntentAgent
from app.agents.requirement.artifact_review_agent import AssistantArtifactReviewAgent

router = APIRouter(prefix="/agent/tasks", tags=["Agent Tasks"])

requirement_graph = RequirementGraph()
module_tree_patch_agent = ModuleTreePatchAgent()
assistant_intent_agent = AssistantIntentAgent()
assistant_artifact_review_agent = AssistantArtifactReviewAgent()


PROMPT_DEBUG_SUPPORTED_TASK_TYPES = {
    "GENERATE_DATA_MODEL",
    "GENERATE_API_CONTRACT",
    "GENERATE_TEST_SPEC",
    "GENERATE_DEV_HANDOFF_PACKAGE",
    "GENERATE_UI_CONTRACT",
}


@router.post("/execute", response_model=AgentTaskResponse)
async def execute_agent_task(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)

@router.post("/propose-module-tree-patch", response_model=AgentTaskResponse)
async def propose_module_tree_patch(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    try:
        artifact = await module_tree_patch_agent.run(request)

        content = artifact.model_dump()
        content.pop("artifact_type", None)
        content.pop("markdown", None)

        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="MODULE_TREE_PATCH_PROPOSAL",
            content=content,
            content_markdown=artifact.markdown,
            status="SUCCESS",
            metadata={
                "agent": "ModuleTreePatchAgent",
                "proposal_only": True,
                "requires_user_confirmation": True,
            },
        )

    except Exception as exc:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="MODULE_TREE_PATCH_PROPOSAL",
            content={},
            content_markdown=None,
            status="FAILED",
            error_code="MODULE_TREE_PATCH_PROPOSAL_FAILED",
            error_message=str(exc),
            metadata={
                "agent": "ModuleTreePatchAgent",
                "proposal_only": True,
            },
        )



@router.post("/classify-assistant-intent", response_model=AgentTaskResponse)
async def classify_assistant_intent(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    try:
        artifact = await assistant_intent_agent.run(request)

        content = artifact.model_dump()
        content.pop("artifact_type", None)

        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="ASSISTANT_INTENT_CLASSIFICATION",
            content=content,
            content_markdown=None,
            status="SUCCESS",
            metadata={
                "agent": "AssistantIntentAgent",
                "classification_only": True,
            },
        )

    except Exception as exc:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="ASSISTANT_INTENT_CLASSIFICATION",
            content={
                "intent": "GENERAL_QA",
                "confidence": 0.0,
                "source": "PYTHON_ENDPOINT_FAILED",
                "reason": str(exc),
                "normalizedMessage": request.requirement_text or "",
                "suggestedReply": "AI Assistant 意图识别失败，请稍后重试，或使用更明确的指令。",
                "requiresUserConfirmation": False,
                "artifactType": None,
                "taskType": None,
                "fullChain": False,
                "coreOnly": True,
                "riskNotes": [str(exc)],
            },
            content_markdown=None,
            status="FAILED",
            error_code="ASSISTANT_INTENT_CLASSIFICATION_FAILED",
            error_message=str(exc),
            metadata={
                "agent": "AssistantIntentAgent",
                "classification_only": True,
            },
        )



@router.post("/review-artifact", response_model=AgentTaskResponse)
async def review_assistant_artifact(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    try:
        artifact = await assistant_artifact_review_agent.run(request)
        content = artifact.model_dump()
        content.pop("artifact_type", None)
        markdown = content.pop("markdown", None)

        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="ASSISTANT_ARTIFACT_REVIEW",
            content=content,
            content_markdown=markdown,
            status="SUCCESS",
            metadata={
                "agent": "AssistantArtifactReviewAgent",
                "review_only": True,
            },
        )

    except Exception as exc:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="ASSISTANT_ARTIFACT_REVIEW",
            content={
                "summary": "AI 资产审查失败。",
                "riskLevel": "MEDIUM",
                "conclusion": str(exc),
                "issues": [],
                "consistencyChecks": [],
                "suggestions": [],
                "nextActions": ["稍后重试", "检查 Python Agent 日志"],
            },
            content_markdown=None,
            status="FAILED",
            error_code="ASSISTANT_ARTIFACT_REVIEW_FAILED",
            error_message=str(exc),
            metadata={
                "agent": "AssistantArtifactReviewAgent",
                "review_only": True,
            },
        )


@router.post("/debug/requirement-prompt", response_model=AgentTaskResponse)
async def debug_requirement_prompt(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    """
    需求 Agent Prompt Debug 接口。

    用途：
    1. 验证 compact_context 是否进入 prompt。
    2. 查看 prompt 长度、章节、依赖状态、上下文质量。
    3. 不调用 LLM，不消耗 token。
    4. 仅支持已经迁移到通用 LangGraph 工作流的重量级资产。
    """

    if request.task_type not in PROMPT_DEBUG_SUPPORTED_TASK_TYPES:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="PROMPT_DEBUG",
            content={
                "supported_task_types": sorted(PROMPT_DEBUG_SUPPORTED_TASK_TYPES),
                "received_task_type": request.task_type,
            },
            status="FAILED",
            error_code="PROMPT_DEBUG_UNSUPPORTED_TASK_TYPE",
            error_message=(
                "Prompt debug only supports DATA_MODEL, API_CONTRACT, "
                "TEST_SPEC and DEV_HANDOFF_PACKAGE workflows."
            ),
            metadata={
                "debug_endpoint": "/agent/tasks/debug/requirement-prompt",
            },
        )

    debug_input = dict(request.input or {})
    debug_input["prompt_debug_only"] = True

    debug_request = request.model_copy(
        update={
            "input": debug_input,
        }
    )

    return await requirement_graph.run(debug_request)


@router.post("/generate-module-tree", response_model=AgentTaskResponse)
async def generate_module_tree(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-module-prd", response_model=AgentTaskResponse)
async def generate_module_prd(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-business-flow", response_model=AgentTaskResponse)
async def generate_business_flow(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-page-spec", response_model=AgentTaskResponse)
async def generate_page_spec(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-data-model", response_model=AgentTaskResponse)
async def generate_data_model(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-api-contract", response_model=AgentTaskResponse)
async def generate_api_contract(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-test-spec", response_model=AgentTaskResponse)
async def generate_test_spec(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)


@router.post("/generate-dev-handoff-package", response_model=AgentTaskResponse)
async def generate_dev_handoff_package(
    request: AgentTaskRequest,
) -> AgentTaskResponse:
    return await requirement_graph.run(request)

