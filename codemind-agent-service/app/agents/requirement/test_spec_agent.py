from __future__ import annotations
from app.graphs.requirement.context.prompt_context_builder import RequirementPromptContextBuilder
import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.test_spec import (
    TEST_SPEC_LLM_JSON_SCHEMA,
    TestSpecArtifact,
    TestSpecLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer


class TestSpecAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_context_builder = RequirementPromptContextBuilder()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> TestSpecArtifact:
        try:
            system_prompt = self.prompt_template_renderer.resolve_system_prompt(
                request=request,
                fallback_system_prompt=self._load_prompt("test_spec.md"),
            )

            user_prompt = self.prompt_template_renderer.render_user_prompt(
                request=request,
                fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
            )

            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="test_spec_output",
                output_json_schema=TEST_SPEC_LLM_JSON_SCHEMA,
            )

            parsed = TestSpecLlmOutput.model_validate_json(llm_result.text)

        except Exception as exc:
            parsed = self._build_fallback_output(request, exc)

        markdown = self._build_markdown(parsed)

        return TestSpecArtifact(
            basic_info=parsed.basic_info,
            test_scope=parsed.test_scope,
            acceptance_criteria=parsed.acceptance_criteria,
            functional_test_cases=parsed.functional_test_cases,
            api_test_cases=parsed.api_test_cases,
            flow_test_cases=parsed.flow_test_cases,
            permission_test_cases=parsed.permission_test_cases,
            data_validation_test_cases=parsed.data_validation_test_cases,
            exception_test_cases=parsed.exception_test_cases,
            test_data_requirements=parsed.test_data_requirements,
            automation_hints=parsed.automation_hints,
            downstream_hints=parsed.downstream_hints,
            risk_notes=parsed.risk_notes,
            analysis_summary=parsed.analysis_summary,
            markdown=markdown,
        )

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        sections = [
            "请为当前指定模块生成企业级 TEST_SPEC 测试规格资产。",
            "",
            "【强制限制】",
            "1. 必须输出完整可解析 JSON。",
            "2. functional_test_cases 最多 4 条。",
            "3. api_test_cases 最多 4 条。",
            "4. flow_test_cases 最多 3 条。",
            "5. permission_test_cases 最多 3 条。",
            "6. data_validation_test_cases 最多 3 条。",
            "7. exception_test_cases 最多 3 条。",
            "8. acceptance_criteria 最多 5 条。",
            "9. 每个测试用例 steps 最多 4 步。",
            "10. 每个测试用例 expected_results 最多 3 条。",
            "11. 不要展开全部接口、全部页面、全部字段。",
            "12. 不要输出 markdown 字段。",
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
                ],
                artifact_title_map={
                    "MODULE_TREE": "模块树摘要",
                    "MODULE_PRD": "当前模块 PRD 摘要",
                    "BUSINESS_FLOW": "当前模块业务流程摘要",
                    "PAGE_SPEC": "当前模块页面规范摘要",
                    "DATA_MODEL": "当前模块数据模型摘要",
                    "API_CONTRACT": "当前模块接口契约摘要",
                },
                max_artifact_chars=3000,
            )
        )

        sections.extend(
            [
                "【生成要求】",
                "1. 优先围绕 API_CONTRACT 的核心接口生成接口测试用例。",
                "2. 优先围绕 PAGE_SPEC 的核心页面动作生成功能测试用例。",
                "3. 优先围绕 BUSINESS_FLOW 的关键状态流转生成流程测试用例。",
                "4. 优先围绕 DATA_MODEL 的必填字段、状态字段和约束生成数据校验用例。",
                "5. 权限、参数错误、状态错误、数据不存在必须优先覆盖。",
                "6. 如果上游资产缺失，应生成保守但可执行的最小测试规格。",
                "",
            ]
        )

        sections.extend(self.prompt_context_builder.build_json_output_rules())

        return "\n".join(sections)
    
    def _build_fallback_output(
        self,
        request: AgentTaskRequest,
        error: Exception,
    ) -> TestSpecLlmOutput:
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
                "test_spec_key": f"{module_key}_test_spec",
                "test_spec_name": f"{module_name}测试规格",
                "related_module_key": str(module_key),
                "related_module_name": str(module_name),
                "test_goal": "验证当前模块核心功能、接口、权限、数据校验和异常场景是否满足需求。",
                "test_strategy": "采用核心功能测试、接口测试、流程测试、权限测试和异常场景测试相结合的方式进行验证。",
                "test_level": "API、E2E、ACCEPTANCE",
            },
            "test_scope": [
                {
                    "scope_key": "scope_core_function",
                    "scope_name": "核心功能测试",
                    "scope_type": "FUNCTION",
                    "description": "覆盖当前模块的创建、编辑、提交、查询等核心业务操作。",
                    "included": True,
                },
                {
                    "scope_key": "scope_api",
                    "scope_name": "核心接口测试",
                    "scope_type": "API",
                    "description": "覆盖当前模块主要接口的请求参数、响应结构、错误返回和权限控制。",
                    "included": True,
                },
                {
                    "scope_key": "scope_permission",
                    "scope_name": "权限测试",
                    "scope_type": "PERMISSION",
                    "description": "覆盖未登录、无权限和越权访问等关键权限场景。",
                    "included": True,
                },
            ],
            "acceptance_criteria": [
                {
                    "criterion_key": "ac_core_function_available",
                    "title": "核心功能可用",
                    "description": "当前模块核心业务操作能够按照预期完成，并正确返回处理结果。",
                    "related_requirement_keys": [],
                    "verification_method": "功能测试与接口测试",
                },
                {
                    "criterion_key": "ac_data_validation_effective",
                    "title": "数据校验有效",
                    "description": "必填字段、关键格式、状态流转和业务约束能够被正确校验。",
                    "related_requirement_keys": [],
                    "verification_method": "数据校验测试",
                },
                {
                    "criterion_key": "ac_permission_control_effective",
                    "title": "权限控制有效",
                    "description": "未授权用户不能访问受保护资源，越权操作能够被拦截。",
                    "related_requirement_keys": [],
                    "verification_method": "权限测试",
                },
            ],
            "functional_test_cases": [
                {
                    "case_key": "ft_create_success",
                    "title": "核心业务创建成功",
                    "priority": "P0",
                    "related_page_keys": [],
                    "related_flow_step_keys": [],
                    "preconditions": [
                        "用户已登录",
                        "用户具备当前模块操作权限",
                    ],
                    "steps": [
                        "进入当前模块核心操作页面",
                        "填写必要业务字段",
                        "提交保存操作",
                    ],
                    "expected_results": [
                        "系统保存成功",
                        "页面展示成功提示",
                        "数据可在列表或详情中查询到",
                    ],
                    "test_data": "准备一组满足必填字段和业务规则的有效数据。",
                },
                {
                    "case_key": "ft_required_field_validation",
                    "title": "必填字段校验",
                    "priority": "P1",
                    "related_page_keys": [],
                    "related_flow_step_keys": [],
                    "preconditions": [
                        "用户已登录",
                    ],
                    "steps": [
                        "进入当前模块操作页面",
                        "清空关键必填字段",
                        "提交表单",
                    ],
                    "expected_results": [
                        "系统阻止提交",
                        "页面提示必填字段错误",
                    ],
                    "test_data": "准备缺少关键必填字段的数据。",
                },
            ],
            "api_test_cases": [
                {
                    "case_key": "api_core_success",
                    "title": "核心接口成功调用",
                    "api_key": "core_api",
                    "method": "POST",
                    "path": "/api/current-module/core-action",
                    "request_focus": [
                        "必填参数完整",
                        "业务字段合法",
                    ],
                    "expected_status_codes": [
                        "200",
                    ],
                    "expected_response_checks": [
                        "返回成功状态",
                        "返回核心业务数据",
                    ],
                    "negative_cases": [
                        "缺少必填参数",
                        "参数格式错误",
                    ],
                },
                {
                    "case_key": "api_permission_denied",
                    "title": "无权限访问接口",
                    "api_key": "core_api_permission",
                    "method": "POST",
                    "path": "/api/current-module/core-action",
                    "request_focus": [
                        "无权限用户访问",
                    ],
                    "expected_status_codes": [
                        "403",
                    ],
                    "expected_response_checks": [
                        "返回权限不足提示",
                    ],
                    "negative_cases": [
                        "未登录访问",
                        "越权访问",
                    ],
                },
            ],
            "flow_test_cases": [
                {
                    "case_key": "flow_main_success",
                    "title": "主流程成功流转",
                    "flow_key": "main_flow",
                    "scenario": "验证当前模块核心主流程能够从初始状态流转到目标状态。",
                    "preconditions": [
                        "存在有效业务数据",
                    ],
                    "steps": [
                        "发起核心业务操作",
                        "提交处理",
                        "查看处理结果",
                    ],
                    "expected_state_changes": [
                        "业务状态按预期变化",
                        "操作记录可追踪",
                    ],
                },
            ],
            "permission_test_cases": [
                {
                    "case_key": "perm_not_login",
                    "title": "未登录访问拦截",
                    "role": "ANONYMOUS",
                    "permission_key": "current_module_access",
                    "allowed_actions": [],
                    "denied_actions": [
                        "访问页面",
                        "调用接口",
                    ],
                    "expected_result": "系统拒绝访问并提示需要登录。",
                },
                {
                    "case_key": "perm_no_permission",
                    "title": "无权限操作拦截",
                    "role": "NORMAL_USER",
                    "permission_key": "current_module_write",
                    "allowed_actions": [
                        "查看授权范围内数据",
                    ],
                    "denied_actions": [
                        "新增",
                        "编辑",
                        "删除",
                    ],
                    "expected_result": "系统拒绝操作并返回权限不足提示。",
                },
            ],
            "data_validation_test_cases": [
                {
                    "case_key": "data_required_validation",
                    "title": "核心必填字段校验",
                    "entity_key": "core_entity",
                    "field_name": "required_field",
                    "validation_rule": "核心必填字段不能为空。",
                    "valid_examples": [
                        "有效字段值",
                    ],
                    "invalid_examples": [
                        "",
                        None,
                    ],
                    "expected_error": "提示字段不能为空。",
                },
            ],
            "exception_test_cases": [
                {
                    "case_key": "ex_param_error",
                    "title": "参数错误处理",
                    "exception_type": "PARAM_ERROR",
                    "trigger_condition": "提交缺失必填参数或格式错误参数。",
                    "expected_behavior": "系统返回参数错误提示，不产生脏数据。",
                    "related_api_keys": [
                        "core_api",
                    ],
                },
                {
                    "case_key": "ex_state_error",
                    "title": "非法状态操作",
                    "exception_type": "STATE_ERROR",
                    "trigger_condition": "对不允许操作的业务状态执行提交、审批、删除等操作。",
                    "expected_behavior": "系统拒绝操作并提示当前状态不允许执行。",
                    "related_api_keys": [
                        "core_api",
                    ],
                },
            ],
            "test_data_requirements": [
                {
                    "data_key": "data_valid_core",
                    "data_name": "有效核心业务数据",
                    "related_entity_key": "core_entity",
                    "description": "用于验证当前模块核心成功流程的最小有效数据。",
                    "setup_method": "通过页面录入或接口初始化。",
                },
                {
                    "data_key": "data_invalid_core",
                    "data_name": "无效校验数据",
                    "related_entity_key": "core_entity",
                    "description": "用于验证必填、格式、权限和状态异常的数据。",
                    "setup_method": "手工构造或接口构造。",
                },
            ],
            "automation_hints": [
                {
                    "hint_key": "auto_api_core",
                    "target": "API",
                    "recommendation": "优先生成核心接口的成功用例、参数错误用例和权限错误用例。",
                    "priority": "P0",
                },
                {
                    "hint_key": "auto_e2e_core",
                    "target": "E2E",
                    "recommendation": "优先覆盖当前模块主流程的页面端到端测试。",
                    "priority": "P1",
                },
            ],
            "downstream_hints": {
                "test_agent_hints": [
                    "优先生成核心接口和主流程测试。",
                    "对权限、参数错误和状态错误场景补充自动化断言。",
                    "根据后续接口契约补全精确路径和字段。",
                ],
                "backend_fix_hints": [
                    "后端需要保证参数校验、权限校验和状态流转校验一致。",
                    "异常返回结构需要稳定，便于自动化测试断言。",
                ],
                "frontend_fix_hints": [
                    "前端需要对必填字段和核心格式进行即时校验。",
                    "权限不足和异常返回需要有明确提示。",
                ],
                "dev_handoff_hints": [
                    "开发交接包应引用本测试规格中的核心验收标准。",
                    "后续可基于真实接口契约补充详细测试用例。",
                ],
            },
            "risk_notes": [
                "当前为兜底精简测试规格，建议后续根据真实接口契约补充精确路径和字段。",
                "权限、状态流转和异常处理是当前模块重点漏测风险。",
                f"LLM 原始生成失败，已启用兜底策略：{type(error).__name__}",
            ],
            "analysis_summary": "本测试规格覆盖当前模块核心功能、接口、权限、数据校验、异常场景和验收标准，可作为测试 Agent 与开发交接包的稳定输入。由于本次启用了兜底策略，后续建议在全局优化阶段引入资产摘要和分层上下文机制。",
        }

        return TestSpecLlmOutput.model_validate(fallback_payload)

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
            + "\n...【上下文已裁剪：请基于已给信息生成核心测试规格，保证 JSON 完整】"
        )

    def _build_markdown(self, spec: TestSpecLlmOutput) -> str:
        info = spec.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块测试规格",
            "",
            "## 1. 测试规格基本信息",
            f"- **测试规格 Key**：{info.test_spec_key}",
            f"- **测试规格名称**：{info.test_spec_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **测试层级**：{info.test_level}",
            f"- **测试目标**：{info.test_goal}",
            f"- **测试策略**：{info.test_strategy}",
            "",
            "## 2. 测试范围",
        ]

        if spec.test_scope:
            for item in spec.test_scope:
                lines.extend(
                    [
                        f"- **{item.scope_name}**（{item.scope_key}）",
                        f"  - 类型：{item.scope_type}",
                        f"  - 是否纳入：{'是' if item.included else '否'}",
                        f"  - 说明：{item.description}",
                    ]
                )
        else:
            lines.append("- 暂无测试范围。")

        lines.extend(["", "## 3. 验收标准"])

        if spec.acceptance_criteria:
            for item in spec.acceptance_criteria:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.criterion_key}）",
                        f"  - 说明：{item.description}",
                        f"  - 关联需求：{self._join_list(item.related_requirement_keys)}",
                        f"  - 验证方式：{item.verification_method}",
                    ]
                )
        else:
            lines.append("- 暂无验收标准。")

        lines.extend(["", "## 4. 功能测试用例"])

        if spec.functional_test_cases:
            for item in spec.functional_test_cases:
                lines.extend(
                    [
                        f"### {item.title}（{item.case_key}）",
                        f"- **优先级**：{item.priority}",
                        f"- **关联页面**：{self._join_list(item.related_page_keys)}",
                        f"- **关联流程**：{self._join_list(item.related_flow_step_keys)}",
                        f"- **前置条件**：{self._join_list(item.preconditions)}",
                        f"- **测试步骤**：{self._join_list(item.steps)}",
                        f"- **预期结果**：{self._join_list(item.expected_results)}",
                        f"- **测试数据**：{item.test_data or '无'}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无功能测试用例。")

        lines.extend(["", "## 5. 接口测试用例"])

        if spec.api_test_cases:
            for item in spec.api_test_cases:
                lines.extend(
                    [
                        f"### {item.title}（{item.case_key}）",
                        f"- **接口**：`{item.method} {item.path}`",
                        f"- **API Key**：{item.api_key}",
                        f"- **请求关注点**：{self._join_list(item.request_focus)}",
                        f"- **预期状态码**：{self._join_list(item.expected_status_codes)}",
                        f"- **响应校验**：{self._join_list(item.expected_response_checks)}",
                        f"- **反向用例**：{self._join_list(item.negative_cases)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无接口测试用例。")

        lines.extend(["", "## 6. 业务流程测试用例"])

        if spec.flow_test_cases:
            for item in spec.flow_test_cases:
                lines.extend(
                    [
                        f"### {item.title}（{item.case_key}）",
                        f"- **流程 Key**：{item.flow_key}",
                        f"- **场景**：{item.scenario}",
                        f"- **前置条件**：{self._join_list(item.preconditions)}",
                        f"- **步骤**：{self._join_list(item.steps)}",
                        f"- **预期状态变化**：{self._join_list(item.expected_state_changes)}",
                        "",
                    ]
                )
        else:
            lines.append("- 暂无业务流程测试用例。")

        lines.extend(["", "## 7. 权限测试用例"])

        if spec.permission_test_cases:
            for item in spec.permission_test_cases:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.case_key}）",
                        f"  - 角色：{item.role}",
                        f"  - 权限：{item.permission_key}",
                        f"  - 允许动作：{self._join_list(item.allowed_actions)}",
                        f"  - 拒绝动作：{self._join_list(item.denied_actions)}",
                        f"  - 预期结果：{item.expected_result}",
                    ]
                )
        else:
            lines.append("- 暂无权限测试用例。")

        lines.extend(["", "## 8. 数据校验测试用例"])

        if spec.data_validation_test_cases:
            for item in spec.data_validation_test_cases:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.case_key}）",
                        f"  - 实体：{item.entity_key}",
                        f"  - 字段：`{item.field_name}`",
                        f"  - 校验规则：{item.validation_rule}",
                        f"  - 合法示例：{self._join_list(item.valid_examples)}",
                        f"  - 非法示例：{self._join_list(item.invalid_examples)}",
                        f"  - 预期错误：{item.expected_error}",
                    ]
                )
        else:
            lines.append("- 暂无数据校验测试用例。")

        lines.extend(["", "## 9. 异常场景测试用例"])

        if spec.exception_test_cases:
            for item in spec.exception_test_cases:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.case_key}）",
                        f"  - 异常类型：{item.exception_type}",
                        f"  - 触发条件：{item.trigger_condition}",
                        f"  - 预期处理：{item.expected_behavior}",
                        f"  - 关联接口：{self._join_list(item.related_api_keys)}",
                    ]
                )
        else:
            lines.append("- 暂无异常场景测试用例。")

        lines.extend(["", "## 10. 测试数据要求"])

        if spec.test_data_requirements:
            for item in spec.test_data_requirements:
                lines.extend(
                    [
                        f"- **{item.data_name}**（{item.data_key}）",
                        f"  - 关联实体：{item.related_entity_key or '无'}",
                        f"  - 说明：{item.description}",
                        f"  - 准备方式：{item.setup_method}",
                    ]
                )
        else:
            lines.append("- 暂无测试数据要求。")

        lines.extend(["", "## 11. 自动化测试建议"])

        if spec.automation_hints:
            for item in spec.automation_hints:
                lines.extend(
                    [
                        f"- **{item.target}**（{item.hint_key}）",
                        f"  - 优先级：{item.priority}",
                        f"  - 建议：{item.recommendation}",
                    ]
                )
        else:
            lines.append("- 暂无自动化测试建议。")

        hints = spec.downstream_hints

        lines.extend(
            [
                "",
                "## 12. 下游提示",
                "### 12.1 测试 Agent 提示",
                self._bullet_list(hints.test_agent_hints),
                "",
                "### 12.2 后端修复提示",
                self._bullet_list(hints.backend_fix_hints),
                "",
                "### 12.3 前端修复提示",
                self._bullet_list(hints.frontend_fix_hints),
                "",
                "### 12.4 开发交接提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "## 13. 风险提示",
                self._bullet_list(spec.risk_notes),
                "",
                "## 14. 分析摘要",
                spec.analysis_summary,
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