from __future__ import annotations

from app.agents.requirement.api_contract_agent import ApiContractAgent
from app.graphs.requirement.nodes.llm_artifact_workflow_nodes import (
    LlmArtifactWorkflowNodes,
)
from app.schemas.api_contract import (
    API_CONTRACT_LLM_JSON_SCHEMA,
    ApiContractArtifact,
    ApiContractLlmOutput,
)


class ApiContractWorkflowNodes(LlmArtifactWorkflowNodes):
    """
    API_CONTRACT LangGraph 多节点工作流。

    使用通用 LLM 资产生成骨架：
    prepare_context
    -> build_prompt
    -> llm_generate
    -> schema_validate
    -> fallback
    -> build_artifact
    """

    def __init__(self) -> None:
        super().__init__(
            agent=ApiContractAgent(),
            artifact_type="API_CONTRACT",
            prompt_filename="api_contract.md",
            output_schema_name="api_contract_output",
            output_json_schema=API_CONTRACT_LLM_JSON_SCHEMA,
            llm_output_model=ApiContractLlmOutput,
            artifact_model=ApiContractArtifact,
            workflow_name="api_contract_langgraph_workflow",
        )