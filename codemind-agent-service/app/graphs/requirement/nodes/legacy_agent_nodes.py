from __future__ import annotations

from typing import Any

from app.agents.requirement.api_contract_agent import ApiContractAgent
from app.agents.requirement.business_flow_agent import BusinessFlowAgent
from app.agents.requirement.data_model_agent import DataModelAgent
from app.agents.requirement.dev_handoff_package_agent import DevHandoffPackageAgent
from app.agents.requirement.module_prd_agent import ModulePrdAgent
from app.agents.requirement.module_tree_agent import ModuleTreeAgent
from app.agents.requirement.page_spec_agent import PageSpecAgent
from app.graphs.requirement.state import RequirementGraphState
from app.schemas.agent_protocol import AgentTaskRequest, AgentTaskResponse


class LegacyRequirementAgentNodes:
    """
    兼容现有 Agent 的节点集合。

    当前 MODULE_TREE、MODULE_PRD、BUSINESS_FLOW、PAGE_SPEC、
    DATA_MODEL、API_CONTRACT、DEV_HANDOFF_PACKAGE 仍然走旧 Agent.run()。
    后续会逐步替换成正式 LangGraph 多节点工作流。
    """

    def __init__(self) -> None:
        self.module_tree_agent = ModuleTreeAgent()
        self.module_prd_agent = ModulePrdAgent()
        self.business_flow_agent = BusinessFlowAgent()
        self.page_spec_agent = PageSpecAgent()
        self.data_model_agent = DataModelAgent()
        self.api_contract_agent = ApiContractAgent()
        self.dev_handoff_package_agent = DevHandoffPackageAgent()

    async def generate_module_tree(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.module_tree_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_module_tree",
        )
        return state

    async def generate_module_prd(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.module_prd_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_module_prd",
        )
        return state

    async def generate_business_flow(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.business_flow_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_business_flow",
        )
        return state

    async def generate_page_spec(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.page_spec_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_page_spec",
        )
        return state

    async def generate_data_model(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.data_model_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_data_model",
        )
        return state

    async def generate_api_contract(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.api_contract_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_api_contract",
        )
        return state

    async def generate_dev_handoff_package(
        self,
        state: RequirementGraphState,
    ) -> RequirementGraphState:
        request = state["request"]
        artifact = await self.dev_handoff_package_agent.run(request)

        state["response"] = self._build_success_response(
            request=request,
            artifact=artifact,
            node_name="generate_dev_handoff_package",
        )
        return state

    def _build_success_response(
        self,
        request: AgentTaskRequest,
        artifact: Any,
        node_name: str,
    ) -> AgentTaskResponse:
        return AgentTaskResponse(
            task_id=request.task_id,
            project_id=request.project_id,
            project_code=request.project_code,
            module_id=request.module_id,
            task_type=request.task_type,
            artifact_type=artifact.artifact_type,
            content=artifact.model_dump(),
            content_markdown=artifact.markdown,
            status="SUCCESS",
            metadata={
                "graph_name": "requirement_graph",
                "node_name": node_name,
                "workflow": "legacy_agent_workflow",
            },
        )