from __future__ import annotations

from app.agents.requirement.data_model_agent import DataModelAgent
from app.graphs.requirement.nodes.llm_artifact_workflow_nodes import (
    LlmArtifactWorkflowNodes,
)
from app.schemas.data_model import (
    DATA_MODEL_LLM_JSON_SCHEMA,
    DataModelArtifact,
    DataModelLlmOutput,
)


class DataModelWorkflowNodes(LlmArtifactWorkflowNodes):
    """
    DATA_MODEL LangGraph 多节点工作流。

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
            agent=DataModelAgent(),
            artifact_type="DATA_MODEL",
            prompt_filename="data_model.md",
            output_schema_name="data_model_output",
            output_json_schema=DATA_MODEL_LLM_JSON_SCHEMA,
            llm_output_model=DataModelLlmOutput,
            artifact_model=DataModelArtifact,
            workflow_name="data_model_langgraph_workflow",
        )