from __future__ import annotations

from app.graphs.requirement.context.prompt_context_builder import RequirementPromptContextBuilder
import json
from pathlib import Path
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.data_model import (
    DATA_MODEL_LLM_JSON_SCHEMA,
    DataModelArtifact,
    DataModelLlmOutput,
)
from app.services.llm_service import LlmService
from app.graphs.requirement.prompt_template_renderer import PromptTemplateRenderer


class DataModelAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()
        self.prompt_context_builder = RequirementPromptContextBuilder()
        self.prompt_template_renderer = PromptTemplateRenderer()

    async def run(self, request: AgentTaskRequest) -> DataModelArtifact:
        system_prompt = self.prompt_template_renderer.resolve_system_prompt(
            request=request,
            fallback_system_prompt=self._load_prompt("data_model.md"),
        )

        user_prompt = self.prompt_template_renderer.render_user_prompt(
            request=request,
            fallback_user_prompt_builder=lambda: self._build_user_prompt(request),
        )

        llm_result = await self.llm_service.generate_structured_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            output_schema_name="data_model_output",
            output_json_schema=DATA_MODEL_LLM_JSON_SCHEMA,
        )

        parsed = DataModelLlmOutput.model_validate_json(llm_result.text)
        markdown = self._build_markdown(parsed)

        return DataModelArtifact(
            basic_info=parsed.basic_info,
            entities=parsed.entities,
            relationships=parsed.relationships,
            indexes=parsed.indexes,
            constraints=parsed.constraints,
            status_models=parsed.status_models,
            page_bindings=parsed.page_bindings,
            api_usage_hints=parsed.api_usage_hints,
            data_security=parsed.data_security,
            migration_hints=parsed.migration_hints,
            downstream_hints=parsed.downstream_hints,
            development_notes=parsed.development_notes,
            testing_notes=parsed.testing_notes,
            analysis_summary=parsed.analysis_summary,
            markdown=markdown,
        )

    def _build_user_prompt(self, request: AgentTaskRequest) -> str:
        sections = [
            "请为当前指定模块生成企业级 DATA_MODEL 数据模型资产。",
            "",
            "【强制限制】",
            "1. 必须输出完整可解析 JSON。",
            "2. entities 最多 4 个。",
            "3. 每个 entity 的 fields 最多 8 个。",
            "4. relationships、indexes、constraints、page_bindings、api_usage_hints 均保持精简。",
            "5. 不要展开完整数据库设计说明书。",
            "6. 不要输出 markdown 字段。",
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
                ],
                artifact_title_map={
                    "MODULE_TREE": "模块树摘要",
                    "MODULE_PRD": "当前模块 PRD 摘要",
                    "BUSINESS_FLOW": "当前模块业务流程摘要",
                    "PAGE_SPEC": "当前模块页面规范摘要",
                },
                max_artifact_chars=3500,
            )
        )

        sections.extend(
            [
                "【生成要求】",
                "1. 优先从页面字段、业务流程和模块 PRD 中抽取核心实体和字段。",
                "2. 字段设计要服务后续 API_CONTRACT、TEST_SPEC 和 DEV_HANDOFF_PACKAGE。",
                "3. 状态字段、索引、约束和数据安全规则只保留当前模块最核心内容。",
                "4. 如果上游资产缺失，应生成保守但可落地的最小数据模型。",
                "",
            ]
        )

        sections.extend(self.prompt_context_builder.build_json_output_rules())

        return "\n".join(sections)
    
    def _build_fallback_output(
        self,
        request: AgentTaskRequest,
        error: Exception,
    ) -> DataModelLlmOutput:
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
                "model_key": f"{module_key}_data_model",
                "model_name": f"{module_name}数据模型",
                "related_module_key": str(module_key),
                "related_module_name": str(module_name),
                "design_goal": "围绕当前模块核心业务对象建立稳定、可扩展、可追踪的数据模型。",
                "database_type_suggestion": "PostgreSQL",
                "naming_convention": "建议表名使用 t_业务对象，字段名使用小写下划线，主键统一使用 id，时间字段统一使用 created_at、updated_at。",
            },
            "entities": [
                {
                    "entity_key": "core_entity",
                    "entity_name": f"{module_name}核心实体",
                    "table_name": f"t_{str(module_key).replace('-', '_')}",
                    "aggregate_role": "AGGREGATE_ROOT",
                    "description": "承载当前模块的核心业务数据。",
                    "primary_key": "id",
                    "fields": [
                        {
                            "field_key": "id",
                            "field_name": "主键",
                            "column_name": "id",
                            "data_type": "uuid",
                            "length": None,
                            "nullable": False,
                            "unique": True,
                            "default_value": None,
                            "description": "业务数据唯一标识。",
                            "business_meaning": "用于唯一定位当前模块的一条业务记录。",
                            "validation_rules": ["不能为空", "全表唯一"],
                            "source_page_fields": [],
                        },
                        {
                            "field_key": "name",
                            "field_name": "名称",
                            "column_name": "name",
                            "data_type": "varchar",
                            "length": "128",
                            "nullable": False,
                            "unique": False,
                            "default_value": None,
                            "description": "当前业务对象的名称。",
                            "business_meaning": "用于页面展示、检索和业务识别。",
                            "validation_rules": ["不能为空", "长度不超过128"],
                            "source_page_fields": [],
                        },
                        {
                            "field_key": "status",
                            "field_name": "状态",
                            "column_name": "status",
                            "data_type": "varchar",
                            "length": "32",
                            "nullable": False,
                            "unique": False,
                            "default_value": "DRAFT",
                            "description": "业务记录当前状态。",
                            "business_meaning": "用于控制业务流程流转和操作权限。",
                            "validation_rules": ["不能为空", "必须属于预定义状态集合"],
                            "source_page_fields": [],
                        },
                        {
                            "field_key": "created_at",
                            "field_name": "创建时间",
                            "column_name": "created_at",
                            "data_type": "timestamp",
                            "length": None,
                            "nullable": False,
                            "unique": False,
                            "default_value": "CURRENT_TIMESTAMP",
                            "description": "记录创建时间。",
                            "business_meaning": "用于审计、排序和问题追踪。",
                            "validation_rules": ["系统自动生成"],
                            "source_page_fields": [],
                        },
                    ],
                    "lifecycle_summary": "数据从草稿创建开始，经过提交、处理、完成或取消等状态流转，最终进入终态。",
                }
            ],
            "relationships": [],
            "indexes": [
                {
                    "index_key": "idx_core_entity_status",
                    "index_name": "idx_core_entity_status",
                    "entity_key": "core_entity",
                    "table_name": f"t_{str(module_key).replace('-', '_')}",
                    "columns": ["status"],
                    "index_type": "NORMAL",
                    "reason": "支持按业务状态进行筛选和列表查询。",
                }
            ],
            "constraints": [
                {
                    "constraint_key": "pk_core_entity",
                    "entity_key": "core_entity",
                    "constraint_type": "PRIMARY_KEY",
                    "fields": ["id"],
                    "rule_description": "id 作为主键，不能为空且全表唯一。",
                    "violation_handling": "数据库拒绝写入，后端返回数据约束错误。",
                },
                {
                    "constraint_key": "nn_core_entity_name",
                    "entity_key": "core_entity",
                    "constraint_type": "NOT_NULL",
                    "fields": ["name"],
                    "rule_description": "名称不能为空。",
                    "violation_handling": "后端参数校验失败并返回明确错误提示。",
                },
            ],
            "status_models": [
                {
                    "status_key": "core_entity_status",
                    "entity_key": "core_entity",
                    "status_field": "status",
                    "allowed_values": ["DRAFT", "SUBMITTED", "COMPLETED", "CANCELLED"],
                    "initial_value": "DRAFT",
                    "terminal_values": ["COMPLETED", "CANCELLED"],
                    "transition_rules": [
                        "DRAFT 可流转为 SUBMITTED",
                        "SUBMITTED 可流转为 COMPLETED 或 CANCELLED",
                        "终态不可再次流转",
                    ],
                }
            ],
            "page_bindings": [
                {
                    "binding_key": "core_page_binding",
                    "page_key": "core_page",
                    "page_name": f"{module_name}核心页面",
                    "entity_key": "core_entity",
                    "fields": ["id", "name", "status", "created_at"],
                    "usage_description": "用于当前模块列表、详情、创建和编辑页面的数据展示与提交。",
                }
            ],
            "api_usage_hints": [
                {
                    "api_hint_key": "core_entity_api",
                    "api_name": f"{module_name}核心接口",
                    "method": "POST",
                    "path_suggestion": f"/api/{str(module_key).replace('_', '-')}",
                    "request_entities": ["core_entity"],
                    "response_entities": ["core_entity"],
                    "description": "接口契约生成时可基于核心实体补充创建、查询、更新和状态流转接口。",
                }
            ],
            "data_security": [
                {
                    "security_key": "core_entity_internal",
                    "entity_key": "core_entity",
                    "fields": ["id", "name", "status"],
                    "security_level": "INTERNAL",
                    "protection_strategy": "通过登录态、角色权限和操作审计控制访问。",
                }
            ],
            "migration_hints": [
                {
                    "migration_key": "create_core_entity_table",
                    "title": "创建核心业务表",
                    "description": "需要创建当前模块核心实体表、主键、状态字段和常用索引。",
                    "risk": "字段命名和状态枚举若后续频繁变化，会影响接口契约和测试用例。",
                    "recommendation": "在接口契约冻结前保持字段精简，并通过迁移脚本管理变更。",
                }
            ],
            "downstream_hints": {
                "api_contract_hints": [
                    "基于 core_entity 生成创建、查询、更新和状态流转接口。",
                    "接口请求和响应字段应优先覆盖 id、name、status、created_at。",
                ],
                "test_spec_hints": [
                    "重点验证必填字段、状态流转、权限控制和非法状态操作。",
                    "针对 name 为空、status 非法、重复提交等场景设计测试用例。",
                ],
                "dev_handoff_hints": [
                    "开发交接包应明确核心实体表名、字段含义、索引和状态流转规则。",
                    "后端实现时需要统一参数校验、状态校验和异常返回结构。",
                ],
                "database_migration_hints": [
                    "优先创建核心实体表和状态索引。",
                    "后续若新增字段，应通过 Flyway migration 管理。",
                ],
                "backend_code_hints": [
                    "建议将 core_entity 作为聚合根实现 Repository、Service 和状态流转校验。",
                    "状态流转逻辑应集中在领域服务或应用服务中，避免散落在 Controller。",
                ],
            },
            "development_notes": [
                "当前为兜底精简数据模型，建议后续结合真实 PRD、页面规范和业务流程补充字段。",
                "字段、索引和约束应保持可演进，避免一次性过度设计。",
                f"LLM 原始生成失败，已启用兜底策略：{type(error).__name__}",
            ],
            "testing_notes": [
                "测试时应重点覆盖必填字段、状态流转、权限访问和异常输入。",
                "后续测试规格可基于本数据模型补充接口级和数据库级断言。",
            ],
            "analysis_summary": "本数据模型围绕当前模块核心业务实体建立了最小可用的数据结构，包括主键、名称、状态、时间字段、索引、约束、状态模型和下游生成提示，可作为 API 契约、测试规格和开发交接包的稳定输入。",
        }

        return DataModelLlmOutput.model_validate(fallback_payload)

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
            + "\n...【上下文已裁剪：请只基于已给信息生成核心数据模型，保证 JSON 完整】"
        )

    def _build_markdown(self, model: DataModelLlmOutput) -> str:
        info = model.basic_info

        lines: list[str] = [
            f"# {info.related_module_name} 模块数据模型",
            "",
            "## 1. 数据模型基本信息",
            f"- **模型 Key**：{info.model_key}",
            f"- **模型名称**：{info.model_name}",
            f"- **关联模块**：{info.related_module_name}（{info.related_module_key}）",
            f"- **数据库建议**：{info.database_type_suggestion}",
            f"- **命名规范**：{info.naming_convention}",
            f"- **设计目标**：{info.design_goal}",
            "",
            "## 2. 实体设计",
        ]

        if model.entities:
            for entity in model.entities:
                lines.extend(
                    [
                        f"### 2.x {entity.entity_name}（{entity.entity_key}）",
                        f"- **表名**：`{entity.table_name}`",
                        f"- **实体角色**：{entity.aggregate_role}",
                        f"- **主键**：`{entity.primary_key}`",
                        f"- **说明**：{entity.description}",
                        f"- **生命周期**：{entity.lifecycle_summary}",
                        "",
                        "| 字段名称 | 字段名 | 类型 | 可空 | 唯一 | 默认值 | 说明 |",
                        "| --- | --- | --- | --- | --- | --- | --- |",
                    ]
                )

                if entity.fields:
                    for field in entity.fields:
                        lines.append(
                            "| "
                            f"{field.field_name} | "
                            f"`{field.column_name}` | "
                            f"{field.data_type}{self._format_length(field.length)} | "
                            f"{'是' if field.nullable else '否'} | "
                            f"{'是' if field.unique else '否'} | "
                            f"{field.default_value or '无'} | "
                            f"{field.description} |"
                        )
                else:
                    lines.append("| 无 | 无 | 无 | 无 | 无 | 无 | 无 |")

                lines.append("")
        else:
            lines.append("- 未识别出明确实体。")

        lines.extend(["", "## 3. 实体关系"])

        if model.relationships:
            for item in model.relationships:
                lines.extend(
                    [
                        f"- **{item.relationship_key}**",
                        f"  - 关系：{item.source_entity_key} → {item.target_entity_key}",
                        f"  - 类型：{item.relationship_type}",
                        f"  - 字段：`{item.source_field}` → `{item.target_field}`",
                        f"  - 说明：{item.description}",
                        f"  - 级联规则：{self._join_list(item.cascade_rules)}",
                    ]
                )
        else:
            lines.append("- 未识别出明确实体关系。")

        lines.extend(["", "## 4. 索引设计"])

        if model.indexes:
            for item in model.indexes:
                lines.extend(
                    [
                        f"- **{item.index_name}**（{item.index_key}）",
                        f"  - 表：`{item.table_name}`",
                        f"  - 字段：{self._join_list(item.columns)}",
                        f"  - 类型：{item.index_type}",
                        f"  - 原因：{item.reason}",
                    ]
                )
        else:
            lines.append("- 暂无明确索引建议。")

        lines.extend(["", "## 5. 数据约束"])

        if model.constraints:
            for item in model.constraints:
                lines.extend(
                    [
                        f"- **{item.constraint_key}**",
                        f"  - 实体：{item.entity_key}",
                        f"  - 类型：{item.constraint_type}",
                        f"  - 字段：{self._join_list(item.fields)}",
                        f"  - 规则：{item.rule_description}",
                        f"  - 违反处理：{item.violation_handling}",
                    ]
                )
        else:
            lines.append("- 暂无明确数据约束。")

        lines.extend(["", "## 6. 状态模型"])

        if model.status_models:
            for item in model.status_models:
                lines.extend(
                    [
                        f"- **{item.status_key}**",
                        f"  - 实体：{item.entity_key}",
                        f"  - 状态字段：`{item.status_field}`",
                        f"  - 初始状态：{item.initial_value}",
                        f"  - 允许状态：{self._join_list(item.allowed_values)}",
                        f"  - 终态：{self._join_list(item.terminal_values)}",
                        f"  - 流转规则：{self._join_list(item.transition_rules)}",
                    ]
                )
        else:
            lines.append("- 暂无明确状态模型。")

        lines.extend(["", "## 7. 页面数据绑定"])

        if model.page_bindings:
            for item in model.page_bindings:
                lines.extend(
                    [
                        f"- **{item.page_name}**（{item.page_key}）",
                        f"  - 实体：{item.entity_key}",
                        f"  - 字段：{self._join_list(item.fields)}",
                        f"  - 用途：{item.usage_description}",
                    ]
                )
        else:
            lines.append("- 暂无明确页面数据绑定。")

        lines.extend(["", "## 8. API 数据使用提示"])

        if model.api_usage_hints:
            for item in model.api_usage_hints:
                lines.extend(
                    [
                        f"- **{item.api_name}**（{item.api_hint_key}）",
                        f"  - 方法：{item.method}",
                        f"  - 路径：`{item.path_suggestion}`",
                        f"  - 请求实体：{self._join_list(item.request_entities)}",
                        f"  - 响应实体：{self._join_list(item.response_entities)}",
                        f"  - 说明：{item.description}",
                    ]
                )
        else:
            lines.append("- 暂无明确 API 数据使用提示。")

        lines.extend(["", "## 9. 数据安全"])

        if model.data_security:
            for item in model.data_security:
                lines.extend(
                    [
                        f"- **{item.security_key}**",
                        f"  - 实体：{item.entity_key}",
                        f"  - 字段：{self._join_list(item.fields)}",
                        f"  - 安全等级：{item.security_level}",
                        f"  - 保护策略：{item.protection_strategy}",
                    ]
                )
        else:
            lines.append("- 暂无明确数据安全规则。")

        lines.extend(["", "## 10. 数据迁移提示"])

        if model.migration_hints:
            for item in model.migration_hints:
                lines.extend(
                    [
                        f"- **{item.title}**（{item.migration_key}）",
                        f"  - 说明：{item.description}",
                        f"  - 风险：{item.risk}",
                        f"  - 建议：{item.recommendation}",
                    ]
                )
        else:
            lines.append("- 暂无明确数据迁移提示。")

        hints = model.downstream_hints

        lines.extend(
            [
                "",
                "## 11. 下游工程资产生成提示",
                "### 11.1 API 契约提示",
                self._bullet_list(hints.api_contract_hints),
                "",
                "### 11.2 测试规格提示",
                self._bullet_list(hints.test_spec_hints),
                "",
                "### 11.3 开发交接提示",
                self._bullet_list(hints.dev_handoff_hints),
                "",
                "### 11.4 数据库迁移提示",
                self._bullet_list(hints.database_migration_hints),
                "",
                "### 11.5 后端代码提示",
                self._bullet_list(hints.backend_code_hints),
                "",
                "## 12. 开发注意事项",
                self._bullet_list(model.development_notes),
                "",
                "## 13. 测试注意事项",
                self._bullet_list(model.testing_notes),
                "",
                "## 14. 分析摘要",
                model.analysis_summary,
            ]
        )

        return "\n".join(lines)

    def _format_length(self, length: str | None) -> str:
        if not length:
            return ""
        return f"({length})"

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