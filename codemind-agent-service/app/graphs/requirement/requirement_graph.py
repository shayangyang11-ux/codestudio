from __future__ import annotations

from typing import Any

from langgraph.graph import END, StateGraph

from app.agents.requirement.ui_contract_agent import UiContractAgent
from app.graphs.requirement.nodes.api_contract_nodes import ApiContractWorkflowNodes
from app.graphs.requirement.nodes.data_model_nodes import DataModelWorkflowNodes
from app.graphs.requirement.nodes.dev_handoff_package_nodes import (
    DevHandoffPackageWorkflowNodes,
)
from app.graphs.requirement.nodes.legacy_agent_nodes import LegacyRequirementAgentNodes
from app.graphs.requirement.nodes.test_spec_nodes import TestSpecWorkflowNodes
from app.graphs.requirement.state import RequirementGraphState
from app.schemas.agent_protocol import AgentTaskRequest, AgentTaskResponse


class RequirementGraph:
    def __init__(self) -> None:
        self.legacy_nodes = LegacyRequirementAgentNodes()
        self.data_model_nodes = DataModelWorkflowNodes()
        self.api_contract_nodes = ApiContractWorkflowNodes()
        self.test_spec_nodes = TestSpecWorkflowNodes()
        self.dev_handoff_package_nodes = DevHandoffPackageWorkflowNodes()
        self.ui_contract_agent = UiContractAgent()
        self.graph = self._build_graph()

    def _build_graph(self):
        graph = StateGraph(RequirementGraphState)

        graph.add_node("route_task", self._route_task)

        graph.add_node("generate_module_tree", self.legacy_nodes.generate_module_tree)
        graph.add_node("generate_module_prd", self.legacy_nodes.generate_module_prd)
        graph.add_node("generate_business_flow", self.legacy_nodes.generate_business_flow)
        graph.add_node("generate_page_spec", self.legacy_nodes.generate_page_spec)
        graph.add_node("generate_ui_contract", self.generate_ui_contract)

        self._add_llm_artifact_nodes(
            graph=graph,
            prefix="data_model",
            nodes=self.data_model_nodes,
        )

        self._add_llm_artifact_nodes(
            graph=graph,
            prefix="api_contract",
            nodes=self.api_contract_nodes,
        )

        self._add_llm_artifact_nodes(
            graph=graph,
            prefix="test_spec",
            nodes=self.test_spec_nodes,
        )

        self._add_llm_artifact_nodes(
            graph=graph,
            prefix="dev_handoff_package",
            nodes=self.dev_handoff_package_nodes,
        )

        graph.set_entry_point("route_task")

        graph.add_conditional_edges(
            "route_task",
            self._route_after_task_type,
            {
                "generate_module_tree": "generate_module_tree",
                "generate_module_prd": "generate_module_prd",
                "generate_business_flow": "generate_business_flow",
                "generate_page_spec": "generate_page_spec",
                "data_model_prepare_context": "data_model_prepare_context",
                "api_contract_prepare_context": "api_contract_prepare_context",
                "test_spec_prepare_context": "test_spec_prepare_context",
                "generate_ui_contract": "generate_ui_contract",
                "dev_handoff_package_prepare_context": "dev_handoff_package_prepare_context",
                "end": END,
            },
        )

        graph.add_edge("generate_module_tree", END)
        graph.add_edge("generate_module_prd", END)
        graph.add_edge("generate_business_flow", END)
        graph.add_edge("generate_page_spec", END)
        graph.add_edge("generate_ui_contract", END)

        self._add_llm_artifact_edges(
            graph=graph,
            prefix="data_model",
            nodes=self.data_model_nodes,
        )

        self._add_llm_artifact_edges(
            graph=graph,
            prefix="api_contract",
            nodes=self.api_contract_nodes,
        )

        self._add_llm_artifact_edges(
            graph=graph,
            prefix="test_spec",
            nodes=self.test_spec_nodes,
        )

        self._add_llm_artifact_edges(
            graph=graph,
            prefix="dev_handoff_package",
            nodes=self.dev_handoff_package_nodes,
        )

        return graph.compile()

    def _add_llm_artifact_nodes(
        self,
        *,
        graph: StateGraph,
        prefix: str,
        nodes: Any,
    ) -> None:
        graph.add_node(f"{prefix}_prepare_context", nodes.prepare_context)
        graph.add_node(f"{prefix}_compact_context", nodes.compact_context)
        graph.add_node(f"{prefix}_dependency_check", nodes.dependency_check)
        graph.add_node(f"{prefix}_build_prompt", nodes.build_prompt)
        graph.add_node(
            f"{prefix}_prompt_debug_response",
            nodes.build_prompt_debug_response,
        )
        graph.add_node(f"{prefix}_llm_generate", nodes.llm_generate)
        graph.add_node(f"{prefix}_json_parse", nodes.json_parse)
        graph.add_node(f"{prefix}_json_repair", nodes.json_repair)
        graph.add_node(f"{prefix}_schema_validate", nodes.schema_validate)
        graph.add_node(f"{prefix}_quality_check", nodes.quality_check)
        graph.add_node(f"{prefix}_fallback", nodes.fallback)
        graph.add_node(f"{prefix}_build_artifact", nodes.build_artifact)
        graph.add_node(f"{prefix}_finalize", nodes.finalize)

    def _add_llm_artifact_edges(
        self,
        *,
        graph: StateGraph,
        prefix: str,
        nodes: Any,
    ) -> None:
        graph.add_edge(f"{prefix}_prepare_context", f"{prefix}_compact_context")
        graph.add_edge(f"{prefix}_compact_context", f"{prefix}_dependency_check")
        graph.add_edge(f"{prefix}_dependency_check", f"{prefix}_build_prompt")

        graph.add_conditional_edges(
            f"{prefix}_build_prompt",
            nodes.route_after_build_prompt,
            {
                f"{prefix}_llm_generate": f"{prefix}_llm_generate",
                f"{prefix}_prompt_debug_response": f"{prefix}_prompt_debug_response",
                f"{prefix}_fallback": f"{prefix}_fallback",
            },
        )

        graph.add_edge(f"{prefix}_prompt_debug_response", f"{prefix}_finalize")

        graph.add_conditional_edges(
            f"{prefix}_llm_generate",
            nodes.route_after_llm_generate,
            {
                f"{prefix}_json_parse": f"{prefix}_json_parse",
                f"{prefix}_fallback": f"{prefix}_fallback",
            },
        )

        graph.add_conditional_edges(
            f"{prefix}_json_parse",
            nodes.route_after_json_parse,
            {
                f"{prefix}_schema_validate": f"{prefix}_schema_validate",
                f"{prefix}_json_repair": f"{prefix}_json_repair",
            },
        )

        graph.add_conditional_edges(
            f"{prefix}_json_repair",
            nodes.route_after_json_repair,
            {
                f"{prefix}_schema_validate": f"{prefix}_schema_validate",
                f"{prefix}_fallback": f"{prefix}_fallback",
            },
        )

        graph.add_conditional_edges(
            f"{prefix}_schema_validate",
            nodes.route_after_schema_validate,
            {
                f"{prefix}_quality_check": f"{prefix}_quality_check",
                f"{prefix}_fallback": f"{prefix}_fallback",
            },
        )

        graph.add_conditional_edges(
            f"{prefix}_quality_check",
            nodes.route_after_quality_check,
            {
                f"{prefix}_build_artifact": f"{prefix}_build_artifact",
                f"{prefix}_fallback": f"{prefix}_fallback",
            },
        )

        graph.add_edge(f"{prefix}_fallback", f"{prefix}_build_artifact")
        graph.add_edge(f"{prefix}_build_artifact", f"{prefix}_finalize")
        graph.add_edge(f"{prefix}_finalize", END)

    async def run(self, request: AgentTaskRequest) -> AgentTaskResponse:
        result: dict[str, Any] = await self.graph.ainvoke(
            {
                "request": request,
                "task_type": request.task_type,
            }
        )

        response = result.get("response")
        if isinstance(response, AgentTaskResponse):
            return response

        return self._build_failed_response(
            request=request,
            error_code=result.get("error_code", "REQUIREMENT_GRAPH_FAILED"),
            error_message=result.get(
                "error_message",
                "Requirement graph failed.",
            ),
        )

    async def _route_task(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        return state

    def _route_after_task_type(
        self,
        state: RequirementGraphState,
    ) -> str:
        request = state["request"]

        if request.task_type == "GENERATE_MODULE_TREE":
            return "generate_module_tree"

        if request.task_type == "GENERATE_MODULE_PRD":
            return "generate_module_prd"

        if request.task_type == "GENERATE_BUSINESS_FLOW":
            return "generate_business_flow"

        if request.task_type == "GENERATE_PAGE_MAP":


            return "generate_page_spec"


        if request.task_type == "GENERATE_PAGE_SPEC":


            return "generate_page_spec"
            return "generate_page_spec"

        if request.task_type == "GENERATE_DATA_MODEL":
            return "data_model_prepare_context"

        if request.task_type == "GENERATE_API_CONTRACT":
            return "api_contract_prepare_context"

        if request.task_type == "GENERATE_TEST_SPEC":
            return "test_spec_prepare_context"

        if request.task_type == "GENERATE_UI_CONTRACT":
            return "generate_ui_contract"

        if request.task_type == "GENERATE_DEV_HANDOFF_PACKAGE":
            return "dev_handoff_package_prepare_context"

        return "end"

    async def generate_ui_contract(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]

        try:
            artifact = await self.ui_contract_agent.run(request)
            content = self._ui_contract_content(artifact)

            state["response"] = AgentTaskResponse(
                task_id=request.task_id,
                project_id=request.project_id,
                project_code=request.project_code,
                module_id=request.module_id,
                task_type=request.task_type,
                artifact_type="UI_CONTRACT",
                content=content,
                content_markdown=getattr(artifact, "markdown", None),
                status="SUCCESS",
                metadata={
                    "graph_name": "requirement_graph",
                    "node_name": "generate_ui_contract",
                    "generation_mode": "UI_CONTRACT_STRUCTURED_PAGE_CONTRACT",
                    "final_artifact_type": "UI_CONTRACT",
                    "contract_usage": {
                        "mock_preview": True,
                        "prototype_code_source": True,
                        "dev_agent_source": True,
                    },
                },
            )

        except Exception as exc:
            state["response"] = self._build_failed_response(
                request=request,
                error_code="GENERATE_UI_CONTRACT_FAILED",
                error_message=str(exc),
            )

        return state

    def _ui_contract_content(
        self,
        artifact: Any,
    ) -> dict[str, Any]:
        if hasattr(artifact, "model_dump"):
            content = artifact.model_dump()
        elif isinstance(artifact, dict):
            content = dict(artifact)
        else:
            content = {}

        content.pop("artifact_type", None)
        content.pop("markdown", None)

        content["artifactType"] = "UI_CONTRACT"
        content["schemaVersion"] = "1.0"

        content.setdefault("interactions", [])
        content.setdefault("validationRules", [])
        content.setdefault("pageStates", [])
        content.setdefault("mockData", {})
        content.setdefault("mockScenarios", [])
        content.setdefault("apiBindings", [])
        content.setdefault("navigationTargets", [])
        content.setdefault("featureCoverage", [])
        content.setdefault("assumptions", [])
        content.setdefault("developmentRules", {})
        content.setdefault("analysisSummary", "")

        # Old routes/pages/module defaults are intentionally not restored.
        return content

    def _build_failed_response(
        self,
        request: AgentTaskRequest,
        error_code: str,
        error_message: str,
    ) -> AgentTaskResponse:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type="UNKNOWN",
            content={},
            status="FAILED",
            error_code=error_code,
            error_message=error_message,
            metadata={
                "graph_name": "requirement_graph",
                "generation_mode": "FAILED",
            },
        )
