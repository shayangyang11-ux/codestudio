from __future__ import annotations
from app.graphs.requirement.context.prompt_context_builder import RequirementPromptContextBuilder
import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.dev_handoff_package import (
    DEV_HANDOFF_PACKAGE_LLM_JSON_SCHEMA,
    DevHandoffPackageArtifact,
    DevHandoffPackageLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer


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

        return DevHandoffPackageArtifact(
            basic_info=parsed.basic_info,
            development_objectives=parsed.development_objectives,
            module_boundaries=parsed.module_boundaries,
            frontend_implementation_items=parsed.frontend_implementation_items,
            backend_implementation_items=parsed.backend_implementation_items,
            api_implementation_items=parsed.api_implementation_items,
            data_implementation_items=parsed.data_implementation_items,
            permission_and_security_items=parsed.permission_and_security_items,
            file_generation_plan=parsed.file_generation_plan,
            development_sequence=parsed.development_sequence,
            test_and_acceptance_handoff=parsed.test_and_acceptance_handoff,
            risks_and_attention=parsed.risks_and_attention,
            downstream_hints=parsed.downstream_hints,
            analysis_summary=parsed.analysis_summary,
            markdown=markdown,
        )

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        sections = [
            "请为当前指定模块生成企业级 DEV_HANDOFF_PACKAGE 开发交接包资产。",
            "",
            "【强制限制】",
            "1. 必须输出完整可解析 JSON。",
            "2. 不要输出 markdown 字段。",
            "3. 不要输出 JSON 代码块。",
            "4. 不要输出解释性文字。",
            "5. 内容要面向后续开发 Agent、测试 Agent 和人工开发人员。",
            "6. 内容保持精炼，避免超长 JSON。",
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
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                ],
                artifact_title_map={
                    "MODULE_TREE": "模块树摘要",
                    "MODULE_PRD": "当前模块 PRD 摘要",
                    "BUSINESS_FLOW": "当前模块业务流程摘要",
                    "PAGE_SPEC": "当前模块页面规范摘要",
                    "DATA_MODEL": "当前模块数据模型摘要",
                    "API_CONTRACT": "当前模块接口契约摘要",
                    "TEST_SPEC": "当前模块测试规格摘要",
                },
                max_artifact_chars=3000,
            )
        )

        sections.extend(
            [
                "【生成要求】",
                "1. 开发交接包必须面向真实开发落地，不能只做概念总结。",
                "2. 前端实现清单要引用 PAGE_SPEC 和 API_CONTRACT。",
                "3. 后端实现清单要引用 DATA_MODEL、API_CONTRACT 和 BUSINESS_FLOW。",
                "4. 测试与验收交接要引用 TEST_SPEC 和 API_CONTRACT。",
                "5. 文件生成计划要给出后端、前端、数据库、测试相关文件建议。",
                "6. 如果上游资产缺失，应生成保守但可执行的最小开发交接包。",
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

        current_module = self._extract_current_module(
            context_package,
            input_payload,
        )

        module_key = (
            current_module.get("module_key")
            or current_module.get("key")
            or "current_module"
        )

        module_name = (
            current_module.get("module_name")
            or current_module.get("name")
            or "当前模块"
        )

        fallback_payload = {
            "basic_info": {
                "package_key": f"{module_key}_dev_handoff_package",
                "package_name": f"{module_name}开发交接包",
                "related_module_key": str(module_key),
                "related_module_name": str(module_name),
                "package_goal": "为当前模块开发 Agent、测试 Agent 和人工开发人员提供稳定的核心开发输入。",
                "target_agents": [
                    "backend_agent",
                    "frontend_agent",
                    "test_agent",
                    "integration_agent",
                ],
                "implementation_scope": "覆盖当前模块核心页面、核心接口、核心数据模型、权限安全、测试验收和开发顺序。",
            },
            "development_objectives": [
                {
                    "objective_key": "obj_core_feature",
                    "title": "实现当前模块核心业务能力",
                    "description": "完成当前模块核心数据创建、查询、编辑、提交和状态处理能力。",
                    "priority": "P0",
                    "acceptance_reference": [
                        "核心功能可用",
                        "接口调用成功",
                    ],
                },
                {
                    "objective_key": "obj_permission_security",
                    "title": "实现权限与安全控制",
                    "description": "确保未登录、无权限和越权操作被正确拦截。",
                    "priority": "P0",
                    "acceptance_reference": [
                        "权限控制有效",
                    ],
                },
                {
                    "objective_key": "obj_testable_delivery",
                    "title": "形成可测试交付",
                    "description": "接口、页面和数据校验应支持测试 Agent 生成自动化测试。",
                    "priority": "P1",
                    "acceptance_reference": [
                        "测试规格核心用例通过",
                    ],
                },
            ],
            "module_boundaries": [
                {
                    "boundary_key": "boundary_current_module",
                    "responsibility": "当前模块负责自身核心业务数据和业务流程处理。",
                    "included_scope": [
                        "核心业务页面",
                        "核心接口",
                        "核心数据实体",
                        "权限校验",
                    ],
                    "excluded_scope": [
                        "跨模块复杂编排",
                        "第三方系统深度集成",
                    ],
                    "dependency_modules": [
                        "用户权限模块",
                        "基础数据模块",
                    ],
                },
            ],
            "frontend_implementation_items": [
                {
                    "item_key": "fe_core_page",
                    "page_key": "core_page",
                    "page_name": "当前模块核心页面",
                    "route_path": "/current-module",
                    "components": [
                        "列表组件",
                        "表单组件",
                        "详情组件",
                    ],
                    "interactions": [
                        "查询",
                        "保存",
                        "提交",
                    ],
                    "api_dependencies": [
                        "core_create_or_save",
                        "core_query_detail",
                    ],
                    "validation_points": [
                        "必填字段校验",
                        "状态操作校验",
                    ],
                },
            ],
            "backend_implementation_items": [
                {
                    "item_key": "be_core_service",
                    "service_name": "CurrentModuleService",
                    "responsibility": "处理当前模块核心业务规则、数据持久化、状态流转和权限校验。",
                    "api_keys": [
                        "core_create_or_save",
                        "core_query_detail",
                    ],
                    "entity_keys": [
                        "core_entity",
                    ],
                    "business_rules": [
                        "保存前校验必填字段",
                        "状态流转必须合法",
                        "写操作必须校验权限",
                    ],
                    "transaction_notes": [
                        "保存业务数据需开启事务",
                        "状态变更和记录写入保持一致",
                    ],
                },
            ],
            "api_implementation_items": [
                {
                    "item_key": "api_save",
                    "api_key": "core_create_or_save",
                    "method": "POST",
                    "path": "/api/current-module/save",
                    "request_summary": "提交当前模块核心业务数据。",
                    "response_summary": "返回保存后的业务数据和处理状态。",
                    "permission_keys": [
                        "current_module_write",
                    ],
                    "error_handling": [
                        "PARAM_ERROR",
                        "PERMISSION_DENIED",
                    ],
                    "test_reference": [
                        "核心业务创建成功",
                        "必填字段校验",
                    ],
                },
                {
                    "item_key": "api_detail",
                    "api_key": "core_query_detail",
                    "method": "GET",
                    "path": "/api/current-module/{id}",
                    "request_summary": "根据 ID 查询当前模块详情。",
                    "response_summary": "返回当前模块核心详情数据。",
                    "permission_keys": [
                        "current_module_read",
                    ],
                    "error_handling": [
                        "NOT_FOUND",
                    ],
                    "test_reference": [
                        "核心接口成功调用",
                    ],
                },
            ],
            "data_implementation_items": [
                {
                    "item_key": "data_core_entity",
                    "entity_key": "core_entity",
                    "table_name": "t_current_module",
                    "important_fields": [
                        "id",
                        "name",
                        "status",
                        "created_at",
                    ],
                    "constraints": [
                        "核心名称不能为空",
                        "状态值必须合法",
                    ],
                    "indexes": [
                        "主键索引",
                        "状态查询索引",
                    ],
                    "migration_notes": [
                        "创建核心业务表",
                        "根据实际业务补充唯一约束",
                    ],
                },
            ],
            "permission_and_security_items": [
                {
                    "item_key": "security_auth",
                    "security_type": "AUTH",
                    "description": "当前模块接口需要登录后访问。",
                    "implementation_hint": "通过 JWT 或统一认证中间件校验登录状态。",
                    "related_api_keys": [
                        "core_create_or_save",
                        "core_query_detail",
                    ],
                },
                {
                    "item_key": "security_permission",
                    "security_type": "PERMISSION",
                    "description": "写操作需要当前模块写权限。",
                    "implementation_hint": "在 Controller 或 Service 层进行权限校验。",
                    "related_api_keys": [
                        "core_create_or_save",
                    ],
                },
            ],
            "file_generation_plan": [
                {
                    "file_key": "backend_controller",
                    "layer": "backend",
                    "suggested_path": "src/main/java/.../CurrentModuleController.java",
                    "file_purpose": "提供当前模块接口入口。",
                    "generation_priority": "P0",
                    "depends_on": [
                        "backend_service",
                    ],
                },
                {
                    "file_key": "backend_service",
                    "layer": "backend",
                    "suggested_path": "src/main/java/.../CurrentModuleService.java",
                    "file_purpose": "实现核心业务逻辑、权限校验和事务控制。",
                    "generation_priority": "P0",
                    "depends_on": [
                        "backend_entity",
                    ],
                },
                {
                    "file_key": "backend_entity",
                    "layer": "backend",
                    "suggested_path": "src/main/java/.../CurrentModuleEntity.java",
                    "file_purpose": "定义当前模块核心数据实体。",
                    "generation_priority": "P0",
                    "depends_on": [],
                },
                {
                    "file_key": "frontend_page",
                    "layer": "frontend",
                    "suggested_path": "src/pages/current-module/index.vue",
                    "file_purpose": "实现当前模块核心页面。",
                    "generation_priority": "P1",
                    "depends_on": [
                        "api_save",
                        "api_detail",
                    ],
                },
                {
                    "file_key": "test_api",
                    "layer": "test",
                    "suggested_path": "tests/current-module/api.spec.ts",
                    "file_purpose": "验证核心接口成功、参数错误和权限错误场景。",
                    "generation_priority": "P1",
                    "depends_on": [
                        "backend_controller",
                    ],
                },
            ],
            "development_sequence": [
                {
                    "step_key": "step_1_data",
                    "step_order": 1,
                    "title": "实现数据模型",
                    "description": "创建当前模块核心实体、表结构和基础约束。",
                    "deliverables": [
                        "实体类",
                        "数据库迁移脚本",
                    ],
                    "dependencies": [],
                },
                {
                    "step_key": "step_2_backend",
                    "step_order": 2,
                    "title": "实现后端接口",
                    "description": "实现核心保存和查询接口，补充权限校验和事务控制。",
                    "deliverables": [
                        "Controller",
                        "Service",
                        "Repository",
                    ],
                    "dependencies": [
                        "数据模型",
                    ],
                },
                {
                    "step_key": "step_3_frontend",
                    "step_order": 3,
                    "title": "实现前端页面",
                    "description": "实现当前模块核心页面、表单校验和接口联调。",
                    "deliverables": [
                        "页面组件",
                        "接口调用封装",
                    ],
                    "dependencies": [
                        "后端接口",
                    ],
                },
                {
                    "step_key": "step_4_test",
                    "step_order": 4,
                    "title": "完成测试验收",
                    "description": "执行核心功能、接口、权限和异常场景测试。",
                    "deliverables": [
                        "测试用例",
                        "测试报告",
                    ],
                    "dependencies": [
                        "前后端联调",
                    ],
                },
            ],
            "test_and_acceptance_handoff": [
                {
                    "item_key": "test_core_function",
                    "test_type": "FUNCTION",
                    "reference_cases": [
                        "核心业务创建成功",
                        "必填字段校验",
                    ],
                    "acceptance_points": [
                        "核心功能可用",
                        "数据校验有效",
                    ],
                    "automation_hint": "优先生成页面主流程和核心接口自动化测试。",
                },
                {
                    "item_key": "test_permission",
                    "test_type": "PERMISSION",
                    "reference_cases": [
                        "未登录访问拦截",
                        "无权限操作拦截",
                    ],
                    "acceptance_points": [
                        "权限控制有效",
                    ],
                    "automation_hint": "补充 401、403 场景断言。",
                },
            ],
            "risks_and_attention": [
                {
                    "risk_key": "risk_context_fallback",
                    "title": "交接包为兜底精简版本",
                    "risk_level": "MEDIUM",
                    "description": "本次交接包由兜底策略生成，部分字段、路径和文件名需要后续基于真实资产补全。",
                    "mitigation": "全局优化阶段引入资产摘要、分层上下文和 JSON 修复重试机制。",
                },
                {
                    "risk_key": "risk_permission",
                    "title": "权限边界需人工确认",
                    "risk_level": "HIGH",
                    "description": "当前模块权限点可能依赖企业实际角色体系。",
                    "mitigation": "开发前由产品或架构负责人确认权限矩阵。",
                },
            ],
            "downstream_hints": {
                "backend_agent_hints": [
                    "优先生成实体、Repository、Service 和 Controller。",
                    "接口实现需要包含参数校验、权限校验和事务控制。",
                    "异常返回结构保持稳定。",
                ],
                "frontend_agent_hints": [
                    "优先实现核心页面、表单校验和接口调用。",
                    "权限不足和异常状态需要友好提示。",
                ],
                "test_agent_hints": [
                    "优先生成核心接口测试和主流程 E2E 测试。",
                    "补充参数错误、权限错误和状态错误断言。",
                ],
                "integration_hints": [
                    "前后端联调前确认接口路径和字段命名。",
                    "测试环境需要准备最小可用数据。",
                ],
            },
            "analysis_summary": f"本开发交接包为当前模块提供核心开发目标、前后端实现清单、接口与数据实现、权限安全、文件计划、开发顺序和测试验收要求。LLM 原始生成失败，已启用兜底策略：{type(error).__name__}。",
        }

        return DevHandoffPackageLlmOutput.model_validate(fallback_payload)
    
    def _get_requirement_text(self, request: AgentTaskRequest) -> str:
        if request.input.get("requirement_text"):
            return str(request.input["requirement_text"])

        if request.input.get("supplement"):
            return str(request.input["supplement"])

        if request.requirement_text:
            return request.requirement_text

        return ""

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

    def _extract_artifact(
        self,
        context_package: dict[str, Any],
        artifact_type: str,
    ) -> Any:
        artifacts = context_package.get("artifacts")

        if isinstance(artifacts, dict):
            return artifacts.get(artifact_type, {})

        if isinstance(artifacts, list):
            for artifact in artifacts:
                if not isinstance(artifact, dict):
                    continue

                if artifact.get("artifact_type") == artifact_type:
                    return artifact

        return {}

    def _compact_json(
        self,
        value: Any,
        max_chars: int,
    ) -> str:
        text = json.dumps(value or {}, ensure_ascii=False, default=str)

        if len(text) <= max_chars:
            return text

        return (
            text[:max_chars]
            + "\n...【上下文已裁剪：请基于已给信息生成开发交接包，保证 JSON 完整】"
        )

    def _build_markdown(self, package: DevHandoffPackageLlmOutput) -> str:
        info = package.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块开发交接包",
            "",
            "## 1. 开发交接包基本信息",
            f"- **交接包 Key**：{info.package_key}",
            f"- **交接包名称**：{info.package_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **目标 Agent**：{self._join_list(info.target_agents)}",
            f"- **交接目标**：{info.package_goal}",
            f"- **实现范围**：{info.implementation_scope}",
            "",
            "## 2. 开发目标",
        ]

        if package.development_objectives:
            for item in package.development_objectives:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.objective_key}）",
                        f"  - 优先级：{item.priority}",
                        f"  - 说明：{item.description}",
                        f"  - 验收引用：{self._join_list(item.acceptance_reference)}",
                    ]
                )
        else:
            lines.append("- 暂无开发目标。")

        lines.extend(["", "## 3. 模块边界"])

        if package.module_boundaries:
            for item in package.module_boundaries:
                lines.extend(
                    [
                        f"- **{item.boundary_key}**",
                        f"  - 模块职责：{item.responsibility}",
                        f"  - 包含范围：{self._join_list(item.included_scope)}",
                        f"  - 排除范围：{self._join_list(item.excluded_scope)}",
                        f"  - 依赖模块：{self._join_list(item.dependency_modules)}",
                    ]
                )
        else:
            lines.append("- 暂无模块边界说明。")

        lines.extend(["", "## 4. 前端实现清单"])

        if package.frontend_implementation_items:
            for item in package.frontend_implementation_items:
                lines.extend(
                    [
                        f"### {item.page_name}（{item.page_key}）",
                        f"- **建议路由**：{item.route_path or '无'}",
                        f"- **建议组件**：{self._join_list(item.components)}",
                        f"- **交互行为**：{self._join_list(item.interactions)}",
                        f"- **依赖接口**：{self._join_list(item.api_dependencies)}",
                        f"- **前端校验点**：{self._join_list(item.validation_points)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无前端实现清单。")

        lines.extend(["", "## 5. 后端实现清单"])

        if package.backend_implementation_items:
            for item in package.backend_implementation_items:
                lines.extend(
                    [
                        f"### {item.service_name}（{item.item_key}）",
                        f"- **职责**：{item.responsibility}",
                        f"- **关联接口**：{self._join_list(item.api_keys)}",
                        f"- **关联实体**：{self._join_list(item.entity_keys)}",
                        f"- **业务规则**：{self._join_list(item.business_rules)}",
                        f"- **事务说明**：{self._join_list(item.transaction_notes)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无后端实现清单。")

        lines.extend(["", "## 6. 接口实现清单"])

        if package.api_implementation_items:
            for item in package.api_implementation_items:
                lines.extend(
                    [
                        f"### {item.api_key}",
                        f"- **接口**：`{item.method} {item.path}`",
                        f"- **请求摘要**：{item.request_summary}",
                        f"- **响应摘要**：{item.response_summary}",
                        f"- **权限**：{self._join_list(item.permission_keys)}",
                        f"- **错误处理**：{self._join_list(item.error_handling)}",
                        f"- **测试引用**：{self._join_list(item.test_reference)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无接口实现清单。")

        lines.extend(["", "## 7. 数据实现清单"])

        if package.data_implementation_items:
            for item in package.data_implementation_items:
                lines.extend(
                    [
                        f"### {item.entity_key}",
                        f"- **建议表名**：{item.table_name or '无'}",
                        f"- **重要字段**：{self._join_list(item.important_fields)}",
                        f"- **约束**：{self._join_list(item.constraints)}",
                        f"- **索引**：{self._join_list(item.indexes)}",
                        f"- **迁移说明**：{self._join_list(item.migration_notes)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无数据实现清单。")

        lines.extend(["", "## 8. 权限与安全要求"])

        if package.permission_and_security_items:
            for item in package.permission_and_security_items:
                lines.extend(
                    [
                        f"- **{item.security_type}**（{item.item_key}）",
                        f"  - 说明：{item.description}",
                        f"  - 实现建议：{item.implementation_hint}",
                        f"  - 关联接口：{self._join_list(item.related_api_keys)}",
                    ]
                )
        else:
            lines.append("- 暂无权限与安全要求。")

        lines.extend(["", "## 9. 文件生成建议"])

        if package.file_generation_plan:
            for item in package.file_generation_plan:
                lines.extend(
                    [
                        f"- **{item.suggested_path}**",
                        f"  - 层级：{item.layer}",
                        f"  - 用途：{item.file_purpose}",
                        f"  - 优先级：{item.generation_priority}",
                        f"  - 依赖：{self._join_list(item.depends_on)}",
                    ]
                )
        else:
            lines.append("- 暂无文件生成建议。")

        lines.extend(["", "## 10. 开发顺序"])

        if package.development_sequence:
            for item in sorted(package.development_sequence, key=lambda step: step.step_order):
                lines.extend(
                    [
                        f"### {item.step_order}. {item.title}（{item.step_key}）",
                        f"- **说明**：{item.description}",
                        f"- **交付物**：{self._join_list(item.deliverables)}",
                        f"- **依赖项**：{self._join_list(item.dependencies)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无开发顺序。")

        lines.extend(["", "## 11. 测试与验收交接"])

        if package.test_and_acceptance_handoff:
            for item in package.test_and_acceptance_handoff:
                lines.extend(
                    [
                        f"- **{item.test_type}**（{item.item_key}）",
                        f"  - 参考用例：{self._join_list(item.reference_cases)}",
                        f"  - 验收点：{self._join_list(item.acceptance_points)}",
                        f"  - 自动化提示：{item.automation_hint or '无'}",
                    ]
                )
        else:
            lines.append("- 暂无测试与验收交接。")

        lines.extend(["", "## 12. 风险与注意事项"])

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
            lines.append("- 暂无风险与注意事项。")

        hints = package.downstream_hints

        lines.extend(
            [
                "",
                "## 13. 下游 Agent 提示",
                "### 13.1 后端 Agent 提示",
                self._bullet_list(hints.backend_agent_hints),
                "",
                "### 13.2 前端 Agent 提示",
                self._bullet_list(hints.frontend_agent_hints),
                "",
                "### 13.3 测试 Agent 提示",
                self._bullet_list(hints.test_agent_hints),
                "",
                "### 13.4 集成提示",
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