from __future__ import annotations
from app.graphs.requirement.context.prompt_context_builder import RequirementPromptContextBuilder
import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.api_contract import (
    API_CONTRACT_LLM_JSON_SCHEMA,
    ApiContractArtifact,
    ApiContractLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer


class ApiContractAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_context_builder = RequirementPromptContextBuilder()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> ApiContractArtifact:
        try:
            system_prompt = self.prompt_template_renderer.resolve_system_prompt(
                request=request,
                fallback_system_prompt=self._load_prompt("api_contract.md"),
            )

            user_prompt = self.prompt_template_renderer.render_user_prompt(
                request=request,
                fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
            )

            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="api_contract_output",
                output_json_schema=API_CONTRACT_LLM_JSON_SCHEMA,
            )

            parsed = ApiContractLlmOutput.model_validate_json(llm_result.text)

        except Exception as exc:
            parsed = self._build_fallback_output(request, exc)

        markdown = self._build_markdown(parsed)

        return ApiContractArtifact(
            basic_info=parsed.basic_info,
            endpoints=parsed.endpoints,
            permission_requirements=parsed.permission_requirements,
            data_mappings=parsed.data_mappings,
            workflow_bindings=parsed.workflow_bindings,
            security_requirements=parsed.security_requirements,
            compatibility_requirements=parsed.compatibility_requirements,
            development_notes=parsed.development_notes,
            testing_notes=parsed.testing_notes,
            downstream_hints=parsed.downstream_hints,
            analysis_summary=parsed.analysis_summary,
            markdown=markdown,
        )
    
    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        sections = [
            "请为当前指定模块生成企业级 API_CONTRACT 接口契约资产。",
            "",
            "【强制限制】",
            "1. 必须输出完整可解析 JSON。",
            "2. endpoints 最多 3 个。",
            "3. 每个接口只保留核心字段。",
            "4. 每个接口错误码最多 3 个。",
            "5. 不要展开所有接口。",
            "6. 未展开接口写入 development_notes。",
            "7. 不要输出 markdown 字段。",
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
                ],
                artifact_title_map={
                    "MODULE_TREE": "模块树摘要",
                    "MODULE_PRD": "当前模块 PRD 摘要",
                    "BUSINESS_FLOW": "当前模块业务流程摘要",
                    "PAGE_SPEC": "当前模块页面规范摘要",
                    "DATA_MODEL": "当前模块数据模型摘要",
                },
                max_artifact_chars=3500,
            )
        )

        sections.extend(
            [
                "【生成要求】",
                "1. 优先从 DATA_MODEL 中抽取实体字段，从 PAGE_SPEC 中抽取页面动作，从 BUSINESS_FLOW 中抽取流程触发点。",
                "2. 接口契约必须服务前端集成、后端实现和测试规格生成。",
                "3. 写接口要说明事务边界、幂等要求、权限要求和错误码。",
                "4. 读接口要说明路径参数、响应字段、权限要求和数据不存在处理。",
                "5. 如果上游资产缺失，应生成保守但可落地的核心接口契约。",
                "",
            ]
        )

        sections.extend(self.prompt_context_builder.build_json_output_rules())

        return "\n".join(sections)
    
    def _build_fallback_output(
        self,
        request: AgentTaskRequest,
        error: Exception,
    ) -> ApiContractLlmOutput:
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

        base_path = f"/api/{str(module_key).replace('_', '-')}"

        fallback_payload = {
            "basic_info": {
                "contract_key": f"{module_key}_api_contract",
                "contract_name": f"{module_name}接口契约",
                "related_module_key": str(module_key),
                "related_module_name": str(module_name),
                "api_style": "RESTful",
                "base_path": base_path,
                "design_goal": "为当前模块提供核心业务操作接口，支撑前端页面集成、后端服务实现、测试规格生成和开发交接。",
                "version_strategy": "建议采用 /api/v1 路径版本或统一网关版本策略。",
            },
            "endpoints": [
                {
                    "api_key": "core_create_or_save",
                    "api_name": "核心业务保存接口",
                    "method": "POST",
                    "path": f"{base_path}/save",
                    "summary": "保存当前模块核心业务数据。",
                    "business_purpose": "用于支撑当前模块新增、编辑或提交核心业务数据的主操作。",
                    "related_page_keys": ["core_page"],
                    "related_entity_keys": ["core_entity"],
                    "related_flow_step_keys": ["main_flow"],
                    "permission_keys": ["current_module_write"],
                    "preconditions": [
                        "用户已登录",
                        "用户具备当前模块写权限",
                    ],
                    "postconditions": [
                        "业务数据被保存",
                        "返回保存后的核心业务数据",
                    ],
                    "request_parameters": [],
                    "request_body_fields": [
                        {
                            "field_key": "name",
                            "name": "name",
                            "data_type": "string",
                            "required": True,
                            "default_value": None,
                            "description": "当前模块核心业务名称。",
                            "validation_rules": [
                                "不能为空",
                                "长度不超过128",
                            ],
                            "related_entity_field": "core_entity.name",
                        },
                        {
                            "field_key": "status",
                            "name": "status",
                            "data_type": "string",
                            "required": False,
                            "default_value": "DRAFT",
                            "description": "当前模块业务状态。",
                            "validation_rules": [
                                "必须属于预定义状态集合",
                            ],
                            "related_entity_field": "core_entity.status",
                        },
                    ],
                    "response_fields": [
                        {
                            "field_key": "id",
                            "name": "id",
                            "data_type": "string",
                            "nullable": False,
                            "description": "保存后的业务数据唯一标识。",
                            "related_entity_field": "core_entity.id",
                        },
                        {
                            "field_key": "status",
                            "name": "status",
                            "data_type": "string",
                            "nullable": False,
                            "description": "保存后的业务状态。",
                            "related_entity_field": "core_entity.status",
                        },
                    ],
                    "success_status_code": "200",
                    "error_codes": [
                        {
                            "error_key": "param_error",
                            "http_status": "400",
                            "error_code": "PARAM_ERROR",
                            "message": "参数错误",
                            "scenario": "请求参数缺失、格式错误或不满足业务校验。",
                            "handling_suggestion": "前端提示用户修正输入后重新提交。",
                        },
                        {
                            "error_key": "permission_denied",
                            "http_status": "403",
                            "error_code": "PERMISSION_DENIED",
                            "message": "权限不足",
                            "scenario": "当前用户没有当前模块写权限。",
                            "handling_suggestion": "前端提示无权限或引导用户申请权限。",
                        },
                        {
                            "error_key": "state_error",
                            "http_status": "409",
                            "error_code": "STATE_ERROR",
                            "message": "业务状态不允许操作",
                            "scenario": "当前业务数据处于不允许保存或提交的状态。",
                            "handling_suggestion": "前端刷新数据状态并提示用户当前状态不可操作。",
                        },
                    ],
                    "idempotent": False,
                    "transaction_required": True,
                    "transaction_boundary": "参数校验、权限校验、状态校验和核心业务数据保存应在同一业务事务边界内完成。",
                    "rate_limit_rule": "建议对重复提交和高频写入操作进行基础限流。",
                    "audit_log_required": True,
                    "test_focus": [
                        "成功保存",
                        "参数错误",
                        "权限不足",
                    ],
                },
                {
                    "api_key": "core_query_detail",
                    "api_name": "核心业务详情查询接口",
                    "method": "GET",
                    "path": f"{base_path}/{{id}}",
                    "summary": "查询当前模块核心业务详情。",
                    "business_purpose": "用于支撑详情页展示、编辑前回显和后续流程处理。",
                    "related_page_keys": ["core_detail_page"],
                    "related_entity_keys": ["core_entity"],
                    "related_flow_step_keys": [],
                    "permission_keys": ["current_module_read"],
                    "preconditions": [
                        "用户已登录",
                    ],
                    "postconditions": [
                        "返回业务详情数据",
                    ],
                    "request_parameters": [
                        {
                            "param_key": "id",
                            "name": "id",
                            "in_location": "path",
                            "data_type": "string",
                            "required": True,
                            "default_value": None,
                            "description": "业务数据唯一标识。",
                            "validation_rules": [
                                "不能为空",
                            ],
                        }
                    ],
                    "request_body_fields": [],
                    "response_fields": [
                        {
                            "field_key": "id",
                            "name": "id",
                            "data_type": "string",
                            "nullable": False,
                            "description": "业务数据唯一标识。",
                            "related_entity_field": "core_entity.id",
                        },
                        {
                            "field_key": "name",
                            "name": "name",
                            "data_type": "string",
                            "nullable": False,
                            "description": "业务名称。",
                            "related_entity_field": "core_entity.name",
                        },
                        {
                            "field_key": "status",
                            "name": "status",
                            "data_type": "string",
                            "nullable": False,
                            "description": "业务状态。",
                            "related_entity_field": "core_entity.status",
                        },
                    ],
                    "success_status_code": "200",
                    "error_codes": [
                        {
                            "error_key": "not_found",
                            "http_status": "404",
                            "error_code": "NOT_FOUND",
                            "message": "数据不存在",
                            "scenario": "根据 id 未查询到业务数据。",
                            "handling_suggestion": "前端提示数据不存在并刷新列表。",
                        }
                    ],
                    "idempotent": True,
                    "transaction_required": False,
                    "transaction_boundary": "只读查询接口不需要开启写事务。",
                    "rate_limit_rule": None,
                    "audit_log_required": False,
                    "test_focus": [
                        "查询成功",
                        "数据不存在",
                        "无权限访问",
                    ],
                },
            ],
            "permission_requirements": [
                {
                    "permission_key": "current_module_read",
                    "permission_name": "当前模块查看权限",
                    "roles": ["NORMAL_USER", "ADMIN"],
                    "api_keys": ["core_query_detail"],
                    "description": "允许用户查看授权范围内的当前模块数据。",
                },
                {
                    "permission_key": "current_module_write",
                    "permission_name": "当前模块写入权限",
                    "roles": ["ADMIN"],
                    "api_keys": ["core_create_or_save"],
                    "description": "允许用户新增、编辑或提交当前模块核心业务数据。",
                },
            ],
            "data_mappings": [
                {
                    "mapping_key": "map_save_request_to_core_entity",
                    "api_key": "core_create_or_save",
                    "entity_key": "core_entity",
                    "mapping_type": "REQUEST_TO_ENTITY",
                    "field_mappings": [
                        "name -> core_entity.name",
                        "status -> core_entity.status",
                    ],
                    "description": "保存接口请求体字段映射到核心业务实体。",
                },
                {
                    "mapping_key": "map_core_entity_to_detail_response",
                    "api_key": "core_query_detail",
                    "entity_key": "core_entity",
                    "mapping_type": "ENTITY_TO_RESPONSE",
                    "field_mappings": [
                        "core_entity.id -> id",
                        "core_entity.name -> name",
                        "core_entity.status -> status",
                    ],
                    "description": "核心业务实体字段映射到详情查询响应结构。",
                },
            ],
            "workflow_bindings": [
                {
                    "binding_key": "bind_save_to_main_flow",
                    "api_key": "core_create_or_save",
                    "flow_step_key": "main_flow",
                    "trigger_timing": "用户提交核心业务表单时触发。",
                    "description": "保存接口支撑当前模块主业务流程的创建或提交步骤。",
                }
            ],
            "security_requirements": [
                {
                    "security_key": "auth_required_for_core_api",
                    "api_key": "core_create_or_save",
                    "security_type": "AUTHENTICATION",
                    "description": "核心写接口必须要求登录访问。",
                    "implementation_hint": "通过 JWT、Session 或统一网关鉴权实现。",
                },
                {
                    "security_key": "permission_check_for_write",
                    "api_key": "core_create_or_save",
                    "security_type": "AUTHORIZATION",
                    "description": "核心写接口必须校验当前模块写权限。",
                    "implementation_hint": "在应用服务层统一校验权限，避免只在前端控制。",
                },
            ],
            "compatibility_requirements": [
                {
                    "compatibility_key": "stable_response_structure",
                    "api_key": "core_query_detail",
                    "scenario": "前端详情页和自动化测试依赖稳定响应结构。",
                    "requirement": "响应字段名称和基础结构应保持兼容，新增字段不应破坏旧调用方。",
                    "recommendation": "通过版本策略和非破坏性字段扩展保持兼容。",
                }
            ],
            "downstream_hints": {
                "backend_code_hints": [
                    "优先实现核心保存接口和详情查询接口。",
                    "Service 层需要包含参数校验、权限校验、状态校验和事务控制。",
                    "异常返回结构需要保持稳定，便于前端和测试断言。",
                ],
                "frontend_integration_hints": [
                    "前端保存页面对接 core_create_or_save。",
                    "详情页对接 core_query_detail。",
                    "参数错误、权限不足和数据不存在需要明确提示。",
                ],
                "test_spec_hints": [
                    "测试规格应覆盖成功保存、参数错误、权限不足和数据不存在场景。",
                    "接口测试需要校验状态码、业务错误码和响应字段。",
                ],
                "dev_handoff_hints": [
                    "开发交接包应引用核心接口路径、权限要求、事务边界和错误码。",
                    "需要明确接口字段和数据模型字段之间的映射关系。",
                ],
                "documentation_hints": [
                    "后续应补充真实接口路径、请求示例、响应示例和错误码示例。",
                ],
            },
            "development_notes": [
                "当前为兜底精简接口契约，建议后续基于稳定数据模型和页面规范补充精确字段。",
                "核心写接口需要处理参数校验、权限校验、状态校验、幂等和事务。",
                "详情查询接口需要处理数据不存在、无权限访问和字段脱敏。",
                f"LLM 原始生成失败，已启用兜底策略：{type(error).__name__}",
            ],
            "testing_notes": [
                "优先测试成功保存、参数错误、权限不足、状态错误和数据不存在场景。",
                "接口响应结构需要保持稳定，便于前端和自动化测试断言。",
            ],
            "analysis_summary": "本接口契约为当前模块生成核心保存与详情查询接口，覆盖接口路径、请求字段、响应字段、错误码、权限、安全、数据映射和测试关注点，可作为测试规格和开发交接包的稳定输入。",
        }

        return ApiContractLlmOutput.model_validate(fallback_payload)

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
            + "\n...【上下文已裁剪：请只基于已给信息生成最核心接口，保证 JSON 完整】"
        )

    def _build_markdown(self, contract: ApiContractLlmOutput) -> str:
        info = contract.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块接口契约",
            "",
            "## 1. 接口契约基本信息",
            f"- **契约 Key**：{info.contract_key}",
            f"- **契约名称**：{info.contract_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **接口风格**：{info.api_style}",
            f"- **基础路径**：`{info.base_path}`",
            f"- **版本策略**：{info.version_strategy}",
            f"- **设计目标**：{info.design_goal}",
            "",
            "## 2. 接口清单",
        ]

        if contract.endpoints:
            for endpoint in contract.endpoints:
                lines.extend(
                    [
                        f"### 2.x {endpoint.api_name}（{endpoint.api_key}）",
                        "",
                        f"- **方法**：`{endpoint.method}`",
                        f"- **路径**：`{endpoint.path}`",
                        f"- **摘要**：{endpoint.summary}",
                        f"- **业务目的**：{endpoint.business_purpose}",
                        f"- **成功状态码**：{endpoint.success_status_code}",
                        f"- **是否幂等**：{'是' if endpoint.idempotent else '否'}",
                        f"- **是否需要事务**：{'是' if endpoint.transaction_required else '否'}",
                        f"- **事务边界**：{endpoint.transaction_boundary}",
                        f"- **限流规则**：{endpoint.rate_limit_rule or '无'}",
                        f"- **是否需要审计日志**：{'是' if endpoint.audit_log_required else '否'}",
                        f"- **关联页面**：{self._join_list(endpoint.related_page_keys)}",
                        f"- **关联实体**：{self._join_list(endpoint.related_entity_keys)}",
                        f"- **关联流程步骤**：{self._join_list(endpoint.related_flow_step_keys)}",
                        f"- **权限要求**：{self._join_list(endpoint.permission_keys)}",
                        f"- **前置条件**：{self._join_list(endpoint.preconditions)}",
                        f"- **后置条件**：{self._join_list(endpoint.postconditions)}",
                        "",
                        "#### 请求参数",
                    ]
                )

                if endpoint.request_parameters:
                    lines.extend(
                        [
                            "| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |",
                            "| --- | --- | --- | --- | --- | --- |",
                        ]
                    )
                    for param in endpoint.request_parameters:
                        lines.append(
                            "| "
                            f"`{param.name}` | "
                            f"{param.in_location} | "
                            f"{param.data_type} | "
                            f"{'是' if param.required else '否'} | "
                            f"{param.default_value or '无'} | "
                            f"{param.description} |"
                        )
                else:
                    lines.append("- 无请求参数。")

                lines.extend(["", "#### 请求体字段"])

                if endpoint.request_body_fields:
                    lines.extend(
                        [
                            "| 字段 | 类型 | 必填 | 默认值 | 关联实体字段 | 说明 |",
                            "| --- | --- | --- | --- | --- | --- |",
                        ]
                    )
                    for field in endpoint.request_body_fields:
                        lines.append(
                            "| "
                            f"`{field.name}` | "
                            f"{field.data_type} | "
                            f"{'是' if field.required else '否'} | "
                            f"{field.default_value or '无'} | "
                            f"{field.related_entity_field or '无'} | "
                            f"{field.description} |"
                        )
                else:
                    lines.append("- 无请求体字段。")

                lines.extend(["", "#### 响应字段"])

                if endpoint.response_fields:
                    lines.extend(
                        [
                            "| 字段 | 类型 | 可空 | 关联实体字段 | 说明 |",
                            "| --- | --- | --- | --- | --- |",
                        ]
                    )
                    for field in endpoint.response_fields:
                        lines.append(
                            "| "
                            f"`{field.name}` | "
                            f"{field.data_type} | "
                            f"{'是' if field.nullable else '否'} | "
                            f"{field.related_entity_field or '无'} | "
                            f"{field.description} |"
                        )
                else:
                    lines.append("- 无响应字段。")

                lines.extend(["", "#### 错误码"])

                if endpoint.error_codes:
                    lines.extend(
                        [
                            "| HTTP 状态 | 业务错误码 | 提示信息 | 场景 | 处理建议 |",
                            "| --- | --- | --- | --- | --- |",
                        ]
                    )
                    for error in endpoint.error_codes:
                        lines.append(
                            "| "
                            f"{error.http_status} | "
                            f"`{error.error_code}` | "
                            f"{error.message} | "
                            f"{error.scenario} | "
                            f"{error.handling_suggestion} |"
                        )
                else:
                    lines.append("- 暂无错误码。")

                lines.extend(
                    [
                        "",
                        f"#### 测试关注点",
                        self._bullet_list(endpoint.test_focus),
                        "",
                    ]
                )
        else:
            lines.append("- 未识别出明确接口。")

        lines.extend(["", "## 3. 权限要求"])

        if contract.permission_requirements:
            for item in contract.permission_requirements:
                lines.extend(
                    [
                        f"- **{item.permission_name}**（{item.permission_key}）",
                        f"  - 角色：{self._join_list(item.roles)}",
                        f"  - 接口：{self._join_list(item.api_keys)}",
                        f"  - 说明：{item.description}",
                    ]
                )
        else:
            lines.append("- 暂无明确权限要求。")

        lines.extend(["", "## 4. 数据映射"])

        if contract.data_mappings:
            for item in contract.data_mappings:
                lines.extend(
                    [
                        f"- **{item.mapping_key}**",
                        f"  - 接口：{item.api_key}",
                        f"  - 实体：{item.entity_key}",
                        f"  - 类型：{item.mapping_type}",
                        f"  - 字段映射：{self._join_list(item.field_mappings)}",
                        f"  - 说明：{item.description}",
                    ]
                )
        else:
            lines.append("- 暂无明确数据映射。")

        lines.extend(["", "## 5. 流程绑定"])

        if contract.workflow_bindings:
            for item in contract.workflow_bindings:
                lines.extend(
                    [
                        f"- **{item.binding_key}**",
                        f"  - 接口：{item.api_key}",
                        f"  - 流程步骤：{item.flow_step_key}",
                        f"  - 触发时机：{item.trigger_timing}",
                        f"  - 说明：{item.description}",
                    ]
                )
        else:
            lines.append("- 暂无明确流程绑定。")

        lines.extend(["", "## 6. 安全要求"])

        if contract.security_requirements:
            for item in contract.security_requirements:
                lines.extend(
                    [
                        f"- **{item.security_key}**",
                        f"  - 接口：{item.api_key}",
                        f"  - 类型：{item.security_type}",
                        f"  - 说明：{item.description}",
                        f"  - 实现建议：{item.implementation_hint}",
                    ]
                )
        else:
            lines.append("- 暂无明确安全要求。")

        lines.extend(["", "## 7. 兼容性要求"])

        if contract.compatibility_requirements:
            for item in contract.compatibility_requirements:
                lines.extend(
                    [
                        f"- **{item.compatibility_key}**",
                        f"  - 接口：{item.api_key}",
                        f"  - 场景：{item.scenario}",
                        f"  - 要求：{item.requirement}",
                        f"  - 建议：{item.recommendation}",
                    ]
                )
        else:
            lines.append("- 暂无明确兼容性要求。")

        hints = contract.downstream_hints

        lines.extend(
            [
                "",
                "## 8. 下游工程资产生成提示",
                "### 8.1 后端代码提示",
                self._bullet_list(hints.backend_code_hints),
                "",
                "### 8.2 前端集成提示",
                self._bullet_list(hints.frontend_integration_hints),
                "",
                "### 8.3 测试规格提示",
                self._bullet_list(hints.test_spec_hints),
                "",
                "### 8.4 开发交接提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "### 8.5 文档提示",
                self._bullet_list(hints.documentation_hints),
                "",
                "## 9. 开发注意事项",
                self._bullet_list(contract.development_notes),
                "",
                "## 10. 测试注意事项",
                self._bullet_list(contract.testing_notes),
                "",
                "## 11. 分析摘要",
                contract.analysis_summary,
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