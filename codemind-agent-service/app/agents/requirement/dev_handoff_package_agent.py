
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.graphs.requirement.context.prompt_context_builder import RequirementPromptContextBuilder
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer
from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.dev_handoff_package import (
    DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA,
    DevHandoffPackageArtifact,
    DevHandoffPackageLlmOutput,
)
from app.services.llm_service import LlmService


class DevHandoffPackageAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_context_builder = RequirementPromptContextBuilder()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> DevHandoffPackageArtifact:
        try:
            system_prompt = self.prompt_template_renderer.resolve_system_prompt(
                request=request,
                fallback_system_prompt=self._load_prompt("dev_handoff_package.md"),
            )

            user_prompt = self.prompt_template_renderer.render_user_prompt(
                request=request,
                fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
            )

            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="dev_handoff_package_output",
                output_json_schema=DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA,
            )

            parsed = DevHandoffPackageLlmOutput.model_validate_json(llm_result.text)

        except Exception as exc:
            parsed = self._build_fallback_output(request, exc)

        markdown = self._build_markdown(parsed)
        payload = parsed.model_dump()
        payload["markdown"] = markdown

        return DevHandoffPackageArtifact.model_validate(payload)

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        sections = [
            "请为当前指定节点生成企业级 DEV_HANDOFF_PACKAGE v2 开发交接包资产。",
            "",
            "【强制限制】",
            "1. 必须输出完整可解析 JSON。",
            "2. 不要输出 markdown 字段。",
            "3. 不要输出 JSON 代码块。",
            "4. 不要输出解释性文字。",
            "5. 内容面向后续 Dev Agent、前端生成器、后端生成器、数据生成器、测试生成器和人工开发人员。",
            "6. 必须保持语言无关，不得把 Node.js、Express、Vue、React、Spring Boot、FastAPI、Prisma、SQLite、Jest、Playwright 等写成实现要求。",
            "7. 内容要精炼，禁止把上游资产全文拼接进 JSON。",
            "",
        ]

        sections.extend(
            self.prompt_context_builder.build_common_sections(
                request=request,
                artifact_order=[
                    "MODULE_TREE",
                    "MODULE_PRD",
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "UI_CONTRACT",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                ],
                artifact_title_map={
                    "MODULE_TREE": "模块树摘要",
                    "MODULE_PRD": "需求说明摘要",
                    "BUSINESS_FLOW": "业务流程摘要",
                    "PAGE_SPEC": "页面规范摘要",
                    "UI_CONTRACT": "UI 契约摘要",
                    "DATA_MODEL": "数据模型摘要",
                    "API_CONTRACT": "接口契约摘要",
                    "TEST_SPEC": "测试规格摘要",
                },
                max_artifact_chars=2600,
            )
        )

        sections.extend(
            [
                "【生成要求】",
                "1. dev_blueprint 是主输出，必须让后续任意技术栈生成器都能消费。",
                "2. 只提炼页面、功能、接口、数据、权限、验收和来源引用，不写具体代码实现。",
                "3. PAGE_SPEC / UI_CONTRACT 共同决定页面与交互；API_CONTRACT 决定接口能力；DATA_MODEL 决定数据语义；TEST_SPEC 决定验收。",
                "4. source_reference_matrix 必须说明每类来源资产用于什么决策，避免 Dev Agent 重新读取大段全文。",
                "5. readiness 要清楚说明是否可进入 Dev Agent；缺失必需资产时列 blockers，待人工确认项列 warnings。",
                "6. supported_stack_modes 可以列 NODEJS_FULLSTACK、JAVA_SPRING_BOOT、PYTHON_FASTAPI 等候选模式，但不得指定当前必须使用某个技术栈。",
                "7. 若信息不足，生成 conservative blueprint，并把缺口放入 unresolved_questions / blockers / warnings。",
                "",
            ]
        )

        sections.extend(self.prompt_context_builder.build_json_output_rules())

        return "\n".join(sections)

    def _build_fallback_output(
        self,
        request: AgentTaskRequest,
        error: Exception,
    ) -> DevHandoffPackageLlmOutput:
        context_package = request.context_package or {}
        input_payload = request.input or {}
        current_module = self._extract_current_module(context_package, input_payload)

        module_key = str(
            current_module.get("module_key")
            or current_module.get("moduleKey")
            or current_module.get("key")
            or "current_node"
        )
        module_name = str(
            current_module.get("module_name")
            or current_module.get("moduleName")
            or current_module.get("name")
            or "当前节点"
        )
        node_type = str(
            current_module.get("module_type")
            or current_module.get("moduleType")
            or current_module.get("node_type")
            or current_module.get("nodeType")
            or "MODULE"
        ).upper()

        artifacts = self._extract_artifacts(context_package)
        source_reference_matrix = self._build_source_references(artifacts)
        required_sources = self._required_sources_for_node(node_type)
        missing_required = [item for item in required_sources if item not in artifacts]

        ready = len(missing_required) == 0
        readiness_level = "READY" if ready else "WARNING"

        fallback_payload = {
            "basic_info": {
                "package_key": f"{module_key}_dev_handoff_package",
                "package_name": f"{module_name}开发交接包",
                "related_module_key": module_key,
                "related_module_name": module_name,
                "related_node_type": node_type,
                "package_goal": "为后续 Dev Agent 提供语言无关、可转换为多技术栈代码的开发蓝图。",
                "target_agents": [
                    "dev_agent",
                    "frontend_generator",
                    "backend_generator",
                    "data_generator",
                    "test_generator",
                ],
                "implementation_scope": "覆盖当前节点直接相关的页面、功能、接口、数据、权限、验收和开发边界。",
                "blueprint_version": "2.0",
                "language_neutral": True,
            },
            "dev_blueprint": {
                "blueprint_key": f"{module_key}_dev_blueprint",
                "blueprint_name": f"{module_name}语言无关开发蓝图",
                "scope_type": node_type,
                "target_node_key": module_key,
                "target_node_name": module_name,
                "target_node_type": node_type,
                "business_goal": "基于当前需求资产实现该节点的核心业务能力，并保持后续可转换为不同技术栈实现。",
                "stack_policy": "LANGUAGE_NEUTRAL_REQUIREMENT_BLUEPRINT",
                "supported_stack_modes": [
                    "NODEJS_FULLSTACK",
                    "JAVA_SPRING_BOOT",
                    "PYTHON_FASTAPI",
                ],
                "generation_boundaries": [
                    "不得擅自改变 PAGE_SPEC 与 UI_CONTRACT 定义的页面能力和交互契约。",
                    "不得把预览场景数据作为最终业务逻辑。",
                    "具体框架、目录结构、数据库方言和测试框架由 Dev Agent 的 targetStack 决定。",
                ],
                "pages": [
                    {
                        "page_key": f"{module_key}_main_page",
                        "page_name": f"{module_name}主页面",
                        "page_goal": "承载当前节点核心用户任务。",
                        "route_semantics": "当前节点主入口",
                        "ui_contract_refs": ["UI_CONTRACT"] if "UI_CONTRACT" in artifacts else [],
                        "page_spec_refs": ["PAGE_SPEC"] if "PAGE_SPEC" in artifacts else [],
                        "interactions": ["查看", "录入", "提交", "反馈"],
                        "state_rules": ["加载中", "空状态", "校验失败", "提交成功", "提交失败"],
                        "api_dependencies": ["API_CONTRACT"] if "API_CONTRACT" in artifacts else [],
                        "validation_points": ["必填校验", "格式校验", "权限校验"],
                    }
                ] if node_type in {"PAGE", "FEATURE", "MODULE", "SUBMODULE"} else [],
                "features": [
                    {
                        "feature_key": f"{module_key}_core_feature",
                        "feature_name": f"{module_name}核心功能",
                        "parent_page_key": f"{module_key}_main_page",
                        "trigger": "用户在当前节点相关页面执行核心操作。",
                        "input_summary": "当前节点核心业务输入。",
                        "output_summary": "返回处理结果、状态变化和用户反馈。",
                        "business_rules": ["执行前校验输入和权限。", "处理失败时返回可理解的错误信息。"],
                        "api_dependencies": ["API_CONTRACT"] if "API_CONTRACT" in artifacts else [],
                        "acceptance_refs": ["TEST_SPEC"] if "TEST_SPEC" in artifacts else [],
                    }
                ],
                "apis": [
                    {
                        "api_key": f"{module_key}_core_api",
                        "capability": "当前节点核心业务处理能力。",
                        "method": "POST",
                        "path": f"/api/{module_key.replace('_', '-')}/core-action",
                        "request_summary": "提交核心业务输入。",
                        "response_summary": "返回处理结果、业务状态和错误信息。",
                        "data_entities": ["core_entity"],
                        "permission_keys": [f"{module_key}_operate"],
                        "error_scenarios": ["参数错误", "权限不足", "业务状态不允许"],
                        "idempotency": "写操作如涉及重复提交，需要在目标技术栈中实现幂等保护。",
                        "test_refs": ["TEST_SPEC"] if "TEST_SPEC" in artifacts else [],
                    }
                ] if "API_CONTRACT" in artifacts else [],
                "data_entities": [
                    {
                        "entity_key": "core_entity",
                        "entity_name": f"{module_name}核心实体",
                        "business_meaning": "承载当前节点核心业务数据。",
                        "fields": ["id：唯一标识", "name：业务名称", "status：业务状态", "createdAt：创建时间"],
                        "relationships": [],
                        "constraints": ["业务名称不能为空", "状态值必须合法"],
                        "lifecycle_states": ["草稿", "有效", "停用"],
                        "security_notes": ["敏感字段需要按业务要求脱敏或限制访问"],
                    }
                ] if "DATA_MODEL" in artifacts else [],
                "ui_contracts": [
                    {
                        "ui_key": f"{module_key}_ui_contract",
                        "page_key": f"{module_key}_main_page",
                        "layout_summary": "遵循 UI_CONTRACT 的页面布局、控件层级和交互反馈。",
                        "key_regions": ["标题区", "操作区", "内容区", "反馈区"],
                        "controls": ["输入控件", "操作按钮", "状态提示"],
                        "interaction_rules": ["校验失败需要就近提示", "提交中需要展示处理中状态"],
                        "visual_consistency_rules": ["不得擅自改变 UI_CONTRACT 已确认的布局和关键控件位置"],
                        "preview_data_policy": "可使用预览场景数据验证页面状态，但不得作为最终业务实现。",
                    }
                ] if "UI_CONTRACT" in artifacts else [],
                "permission_rules": [
                    {
                        "rule_key": f"{module_key}_permission",
                        "rule_type": "PERMISSION",
                        "description": "当前节点写操作需要对应业务权限。",
                        "applies_to": [f"{module_key}_core_api", f"{module_key}_core_feature"],
                        "confirmation_needed": True,
                    }
                ],
                "acceptance_criteria": [
                    {
                        "acceptance_key": f"{module_key}_main_flow_acceptance",
                        "title": "主流程可完成",
                        "scenario_type": "MAIN_FLOW",
                        "preconditions": ["用户具备必要权限", "测试数据已准备"],
                        "steps": ["打开页面", "输入有效数据", "提交操作", "查看成功反馈"],
                        "expected_result": "核心业务操作成功，页面状态和后端数据保持一致。",
                        "source_refs": ["TEST_SPEC"] if "TEST_SPEC" in artifacts else [],
                    }
                ],
                "unresolved_questions": missing_required,
            },
            "source_reference_matrix": source_reference_matrix,
            "development_objectives": [
                {
                    "objective_key": "obj_language_neutral_blueprint",
                    "title": "形成语言无关开发蓝图",
                    "description": "把需求资产整理成可被多技术栈 Dev Agent 转换的稳定输入。",
                    "priority": "P0",
                    "source_refs": list(artifacts.keys())[:6],
                    "acceptance_reference": ["Dev Agent 可读取并转换为目标技术栈开发计划"],
                }
            ],
            "module_boundaries": [
                {
                    "boundary_key": "boundary_current_node",
                    "responsibility": "当前节点只负责自身业务能力、页面/接口/数据依赖和验收边界。",
                    "included_scope": ["核心功能", "必要页面", "必要接口", "必要数据语义", "核心验收"],
                    "excluded_scope": ["无关模块实现", "具体技术栈框架选择", "部署环境细节"],
                    "dependency_nodes": [],
                    "dependency_modules": [],
                }
            ],
            "development_sequence": [
                {
                    "step_key": "step_1_confirm_scope",
                    "step_order": 1,
                    "title": "确认开发范围和来源资产",
                    "description": "确认交接包来源资产版本、审批状态和当前节点边界。",
                    "deliverables": ["来源资产确认", "范围确认"],
                    "dependencies": [],
                },
                {
                    "step_key": "step_2_translate_stack",
                    "step_order": 2,
                    "title": "按目标技术栈转换蓝图",
                    "description": "Dev Agent 根据 targetStack 把语言无关蓝图转换为具体目录、框架、数据库和测试实现。",
                    "deliverables": ["技术栈开发计划", "文件生成清单"],
                    "dependencies": ["范围确认"],
                },
                {
                    "step_key": "step_3_generate_and_verify",
                    "step_order": 3,
                    "title": "生成代码并验收",
                    "description": "生成代码后根据 TEST_SPEC 和 UI_CONTRACT 验证功能、接口、数据和页面一致性。",
                    "deliverables": ["代码提案", "验证结果"],
                    "dependencies": ["技术栈开发计划"],
                },
            ],
            "readiness": {
                "ready_for_dev_agent": ready,
                "readiness_level": readiness_level,
                "blockers": [],
                "warnings": [f"缺少建议来源资产：{item}" for item in missing_required],
                "required_source_artifacts": required_sources,
                "recommended_source_artifacts": ["UI_CONTRACT", "TEST_SPEC", "BUSINESS_FLOW"],
            },
            "risks_and_attention": [
                {
                    "risk_key": "risk_fallback_blueprint",
                    "title": "当前交接包由兜底策略生成",
                    "risk_level": "MEDIUM",
                    "description": "LLM 生成失败或上下文不足时使用兜底蓝图，部分页面、接口、字段需要基于真实资产进一步确认。",
                    "mitigation": "补齐 PAGE_SPEC、UI_CONTRACT、API_CONTRACT、DATA_MODEL、TEST_SPEC 后重新生成交接包。",
                }
            ],
            "downstream_hints": {
                "dev_agent_hints": [
                    "先读取 dev_blueprint，再按 targetStack 选择具体生成器。",
                    "不要把预览场景数据当成最终业务实现。",
                ],
                "frontend_generator_hints": ["页面实现必须遵循 PAGE_SPEC 和 UI_CONTRACT。"],
                "backend_generator_hints": ["接口实现必须遵循 API_CONTRACT，业务规则来自 PAGE_SPEC / BUSINESS_FLOW。"],
                "data_generator_hints": ["数据模型转换应以 DATA_MODEL 的业务语义为准。"],
                "test_generator_hints": ["验收用例以 TEST_SPEC 和 acceptance_criteria 为准。"],
                "integration_hints": ["前后端联调前确认接口路径、字段命名和权限规则。"],
                "backend_agent_hints": ["兼容历史字段：后端生成器读取 backend_generator_hints。"],
                "frontend_agent_hints": ["兼容历史字段：前端生成器读取 frontend_generator_hints。"],
                "test_agent_hints": ["兼容历史字段：测试生成器读取 test_generator_hints。"],
            },
            "analysis_summary": f"已生成 {module_name} 的语言无关 DEV_HANDOFF_PACKAGE v2。LLM 原始生成失败或未通过校验，已启用兜底策略：{type(error).__name__}。",
        }

        return DevHandoffPackageLlmOutput.model_validate(fallback_payload)

    def _build_source_references(self, artifacts: dict[str, Any]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        used_for_map = {
            "MODULE_PRD": ["业务目标", "功能范围", "业务规则"],
            "BUSINESS_FLOW": ["流程顺序", "分支异常", "状态变化"],
            "PAGE_SPEC": ["页面能力", "字段操作", "页面状态"],
            "UI_CONTRACT": ["页面布局", "控件层级", "交互反馈"],
            "API_CONTRACT": ["接口能力", "请求响应", "错误场景"],
            "DATA_MODEL": ["实体语义", "字段约束", "数据关系"],
            "TEST_SPEC": ["验收场景", "边界条件", "测试数据"],
        }

        for artifact_type, artifact in artifacts.items():
            title = artifact_type
            version_no: Any = None
            status: Any = None

            if isinstance(artifact, dict):
                title = str(artifact.get("title") or artifact.get("artifact_title") or artifact_type)
                version_no = artifact.get("versionNo") or artifact.get("version_no")
                status = artifact.get("status")

            result.append(
                {
                    "source_key": f"src_{str(artifact_type).lower()}",
                    "artifact_type": str(artifact_type),
                    "artifact_title": title,
                    "version_no": None if version_no is None else str(version_no),
                    "status": None if status is None else str(status),
                    "used_for": used_for_map.get(str(artifact_type), ["开发输入"]),
                    "confidence": "HIGH" if artifact_type in {"PAGE_SPEC", "UI_CONTRACT", "API_CONTRACT", "DATA_MODEL", "TEST_SPEC"} else "MEDIUM",
                    "notes": "来源资产已纳入交接包引用矩阵。",
                }
            )

        return result[:12]

    def _required_sources_for_node(self, node_type: str) -> list[str]:
        node_type = str(node_type or "MODULE").upper()
        if node_type == "PAGE":
            return ["PAGE_SPEC", "UI_CONTRACT", "API_CONTRACT"]
        if node_type == "FEATURE":
            return ["MODULE_PRD", "PAGE_SPEC", "TEST_SPEC"]
        if node_type in {"SUBMODULE", "MODULE"}:
            return ["MODULE_PRD", "BUSINESS_FLOW"]
        if node_type == "DOMAIN":
            return ["MODULE_PRD", "BUSINESS_FLOW"]
        return ["MODULE_PRD"]

    def _extract_current_module(
        self,
        context_package: dict[str, Any],
        input_payload: dict[str, Any],
    ) -> dict[str, Any]:
        if isinstance(input_payload.get("current_module"), dict):
            return input_payload["current_module"]

        if isinstance(context_package.get("current_module"), dict):
            return context_package["current_module"]

        if isinstance(context_package.get("module"), dict):
            return context_package["module"]

        return {
            "module_id": None,
            "module_key": "",
            "module_name": "",
            "module_type": "",
            "module_path": "",
        }

    def _extract_artifacts(
        self,
        context_package: dict[str, Any],
    ) -> dict[str, Any]:
        artifacts = context_package.get("artifacts")

        if isinstance(artifacts, dict):
            return artifacts

        if isinstance(artifacts, list):
            result: dict[str, Any] = {}

            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                artifact_type = artifact.get("artifact_type") or artifact.get("artifactType") or artifact.get("type")
                if not artifact_type:
                    continue

                if isinstance(artifact.get("content"), dict):
                    result[str(artifact_type)] = artifact["content"]
                else:
                    result[str(artifact_type)] = artifact

            return result

        return {}

    def _build_markdown(self, package: DevHandoffPackageLlmOutput) -> str:
        info = package.basic_info
        blueprint = package.dev_blueprint

        lines: list[str] = [
            f"# {info.related_module_name} 开发交接包",
            "",
            "> 本交接包是语言无关 Dev Blueprint，用于后续 Node.js、Java、Python 等不同技术栈生成器消费。它不是具体代码方案。",
            "",
            "## 1. 交接概览",
            f"- **交接包**：{info.package_name}",
            f"- **关联节点**：{info.related_module_name}（{info.related_module_key}）",
            f"- **节点层级**：{info.related_node_type or blueprint.target_node_type}",
            f"- **蓝图版本**：{info.blueprint_version}",
            f"- **语言无关**：{'是' if info.language_neutral else '否'}",
            f"- **交接目标**：{info.package_goal}",
            f"- **实现范围**：{info.implementation_scope}",
            "",
            "## 2. Dev Blueprint",
            f"- **蓝图名称**：{blueprint.blueprint_name}",
            f"- **开发范围**：{blueprint.scope_type}",
            f"- **业务目标**：{blueprint.business_goal}",
            f"- **技术栈策略**：{blueprint.stack_policy}",
            f"- **可转换目标栈**：{self._join_list(blueprint.supported_stack_modes)}",
            f"- **生成边界**：{self._join_list(blueprint.generation_boundaries)}",
            "",
        ]

        lines.extend(["## 3. 来源资产引用矩阵"])
        if package.source_reference_matrix:
            lines.extend([
                "",
                "| 来源资产 | 版本 | 状态 | 用途 | 可信度 |",
                "|---|---:|---|---|---|",
            ])
            for item in package.source_reference_matrix:
                lines.append(
                    f"| {item.artifact_title}（`{item.artifact_type}`） | "
                    f"{item.version_no or '-'} | {item.status or '-'} | "
                    f"{self._join_list(item.used_for)} | {item.confidence} |"
                )
        else:
            lines.append("- 暂无来源资产引用。")

        lines.extend(["", "## 4. 页面蓝图"])
        if blueprint.pages:
            for item in blueprint.pages:
                lines.extend(
                    [
                        f"### {item.page_name}（{item.page_key}）",
                        f"- **页面目标**：{item.page_goal}",
                        f"- **路由语义**：{item.route_semantics or '无'}",
                        f"- **交互**：{self._join_list(item.interactions)}",
                        f"- **状态规则**：{self._join_list(item.state_rules)}",
                        f"- **接口依赖**：{self._join_list(item.api_dependencies)}",
                        f"- **校验点**：{self._join_list(item.validation_points)}",
                        "",
                    ]
                )
        else:
            lines.append("- 当前节点无独立页面蓝图。")

        lines.extend(["", "## 5. 功能蓝图"])
        if blueprint.features:
            for item in blueprint.features:
                lines.extend(
                    [
                        f"### {item.feature_name}（{item.feature_key}）",
                        f"- **触发条件**：{item.trigger}",
                        f"- **输入**：{item.input_summary or '无'}",
                        f"- **输出**：{item.output_summary or '无'}",
                        f"- **业务规则**：{self._join_list(item.business_rules)}",
                        f"- **接口依赖**：{self._join_list(item.api_dependencies)}",
                        f"- **验收引用**：{self._join_list(item.acceptance_refs)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无功能蓝图。")

        lines.extend(["", "## 6. 接口蓝图"])
        if blueprint.apis:
            for item in blueprint.apis:
                lines.extend(
                    [
                        f"### {item.api_key}",
                        f"- **能力**：{item.capability}",
                        f"- **接口**：`{item.method} {item.path}`",
                        f"- **请求**：{item.request_summary}",
                        f"- **响应**：{item.response_summary}",
                        f"- **关联实体**：{self._join_list(item.data_entities)}",
                        f"- **权限**：{self._join_list(item.permission_keys)}",
                        f"- **错误场景**：{self._join_list(item.error_scenarios)}",
                        f"- **幂等要求**：{item.idempotency or '无'}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无接口蓝图。")

        lines.extend(["", "## 7. 数据蓝图"])
        if blueprint.data_entities:
            for item in blueprint.data_entities:
                lines.extend(
                    [
                        f"### {item.entity_name}（{item.entity_key}）",
                        f"- **业务含义**：{item.business_meaning}",
                        f"- **核心字段**：{self._join_list(item.fields)}",
                        f"- **关系**：{self._join_list(item.relationships)}",
                        f"- **约束**：{self._join_list(item.constraints)}",
                        f"- **生命周期**：{self._join_list(item.lifecycle_states)}",
                        f"- **安全说明**：{self._join_list(item.security_notes)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无数据蓝图。")

        lines.extend(["", "## 8. UI 契约引用"])
        if blueprint.ui_contracts:
            for item in blueprint.ui_contracts:
                lines.extend(
                    [
                        f"### {item.ui_key}",
                        f"- **页面**：{item.page_key}",
                        f"- **布局摘要**：{item.layout_summary}",
                        f"- **关键区域**：{self._join_list(item.key_regions)}",
                        f"- **控件**：{self._join_list(item.controls)}",
                        f"- **交互规则**：{self._join_list(item.interaction_rules)}",
                        f"- **一致性约束**：{self._join_list(item.visual_consistency_rules)}",
                        f"- **预览场景数据边界**：{item.preview_data_policy}",
                        "",
                    ]
                )
        else:
            lines.append("- 当前节点暂无 UI_CONTRACT 引用。")

        lines.extend(["", "## 9. 权限与验收"])
        if blueprint.permission_rules:
            lines.append("### 9.1 权限规则")
            for item in blueprint.permission_rules:
                lines.extend(
                    [
                        f"- **{item.rule_type}**（{item.rule_key}）：{item.description}",
                        f"  - 适用范围：{self._join_list(item.applies_to)}",
                        f"  - 需人工确认：{'是' if item.confirmation_needed else '否'}",
                    ]
                )
        else:
            lines.append("### 9.1 权限规则\n- 暂无权限规则。")

        if blueprint.acceptance_criteria:
            lines.append("\n### 9.2 验收标准")
            for item in blueprint.acceptance_criteria:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.scenario_type} / {item.acceptance_key}）",
                        f"  - 前置条件：{self._join_list(item.preconditions)}",
                        f"  - 步骤：{self._join_list(item.steps)}",
                        f"  - 期望结果：{item.expected_result}",
                    ]
                )
        else:
            lines.append("\n### 9.2 验收标准\n- 暂无验收标准。")

        lines.extend(["", "## 10. Dev Agent 就绪度"])
        readiness = package.readiness
        lines.extend(
            [
                f"- **是否可进入 Dev Agent**：{'是' if readiness.ready_for_dev_agent else '否'}",
                f"- **就绪等级**：{readiness.readiness_level}",
                f"- **阻断项**：{self._join_list(readiness.blockers)}",
                f"- **提醒项**：{self._join_list(readiness.warnings)}",
                f"- **必需来源资产**：{self._join_list(readiness.required_source_artifacts)}",
                f"- **建议来源资产**：{self._join_list(readiness.recommended_source_artifacts)}",
            ]
        )

        lines.extend(["", "## 11. 开发顺序"])
        if package.development_sequence:
            for item in sorted(package.development_sequence, key=lambda step: step.step_order):
                lines.extend(
                    [
                        f"### {item.step_order}. {item.title}",
                        f"- **说明**：{item.description}",
                        f"- **交付物**：{self._join_list(item.deliverables)}",
                        f"- **依赖项**：{self._join_list(item.dependencies)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无开发顺序。")

        lines.extend(["", "## 12. 风险与待确认"])
        if package.risks_and_attention:
            for item in package.risks_and_attention:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.risk_key} / {item.risk_level}）",
                        f"  - 风险说明：{item.description}",
                        f"  - 规避措施：{item.mitigation}",
                    ]
                )
        else:
            lines.append("- 暂无风险与待确认。")

        hints = package.downstream_hints
        lines.extend(
            [
                "",
                "## 13. 下游生成器提示",
                "### 13.1 Dev Agent",
                self._bullet_list(hints.dev_agent_hints),
                "",
                "### 13.2 前端生成器",
                self._bullet_list(hints.frontend_generator_hints or hints.frontend_agent_hints),
                "",
                "### 13.3 后端生成器",
                self._bullet_list(hints.backend_generator_hints or hints.backend_agent_hints),
                "",
                "### 13.4 数据生成器",
                self._bullet_list(hints.data_generator_hints),
                "",
                "### 13.5 测试生成器",
                self._bullet_list(hints.test_generator_hints or hints.test_agent_hints),
                "",
                "### 13.6 集成提示",
                self._bullet_list(hints.integration_hints),
                "",
                "## 14. 分析摘要",
                package.analysis_summary,
            ]
        )

        return "\n".join(lines)

    def _join_list(self, values: list[str]) -> str:
        if not values:
            return "无"
        return "；".join([str(value) for value in values])

    def _bullet_list(self, values: list[str]) -> str:
        if not values:
            return "- 无"
        return "\n".join([f"- {value}" for value in values])

    def _load_prompt(self, filename: str) -> str:
        prompt_path = (
            Path(__file__).resolve().parents[2]
            / "prompts"
            / "requirement"
            / filename
        )

        return prompt_path.read_text(encoding="utf-8")
