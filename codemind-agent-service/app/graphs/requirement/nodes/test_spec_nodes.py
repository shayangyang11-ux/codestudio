from __future__ import annotations

from app.agents.requirement.test_spec_agent import TestSpecAgent
from app.graphs.requirement.nodes.llm_artifact_workflow_nodes import (
    LlmArtifactWorkflowNodes,
)
from app.schemas.test_spec import (
    TEST_SPEC_LLM_JSON_SCHEMA,
    TestSpecArtifact,
    TestSpecLlmOutput,
)


class TestSpecWorkflowNodes(LlmArtifactWorkflowNodes):
    """
    TEST_SPEC LangGraph 多节点工作流。

    具体逻辑由 LlmArtifactWorkflowNodes 统一承载；
    本类只负责声明 TEST_SPEC 的 Agent、Schema、Artifact 类型和工作流名称。
    """

    def __init__(self) -> None:
        super().__init__(
            agent=TestSpecAgent(),
            artifact_type="TEST_SPEC",
            prompt_filename="test_spec.md",
            output_schema_name="test_spec_output",
            output_json_schema=TEST_SPEC_LLM_JSON_SCHEMA,
            llm_output_model=TestSpecLlmOutput,
            artifact_model=TestSpecArtifact,
            workflow_name="test_spec_langgraph_workflow",
        )