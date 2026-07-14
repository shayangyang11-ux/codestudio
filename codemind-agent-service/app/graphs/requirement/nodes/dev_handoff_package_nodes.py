from __future__ import annotations

from app.agents.requirement.dev_handoff_package_agent import DevHandoffPackageAgent
from app.graphs.requirement.nodes.llm_artifact_workflow_nodes import (
    LlmArtifactWorkflowNodes,
)
from app.schemas.dev_handoff_package import (
    DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA,
    DevHandoffPackageArtifact,
    DevHandoffPackageLlmOutput,
)


class DevHandoffPackageWorkflowNodes(LlmArtifactWorkflowNodes):
    """
    DEV_HANDOFF_PACKAGE LangGraph 多节点工作流。

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
            agent=DevHandoffPackageAgent(),
            artifact_type="DEV_HANDOFF_PACKAGE",
            prompt_filename="dev_handoff_package.md",
            output_schema_name="dev_handoff_package_output",
            output_json_schema=DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA,
            llm_output_model=DevHandoffPackageLlmOutput,
            artifact_model=DevHandoffPackageArtifact,
            workflow_name="dev_handoff_package_langgraph_workflow",
        )