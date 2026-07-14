
from __future__ import annotations

import json
from typing import Any

from app.schemas.agent_protocol import AgentTaskRequest
from app.schemas.assistant_intent import (
    ASSISTANT_INTENT_CLASSIFICATION_JSON_SCHEMA,
    AssistantIntentClassificationArtifact,
)
from app.services.llm_service import LlmService


class AssistantIntentAgent:
    def __init__(self) -> None:
        self.llm_service = LlmService()

    async def run(
        self,
        request: AgentTaskRequest,
    ) -> AssistantIntentClassificationArtifact:
        message = self._extract_message(request)

        if not message.strip():
            return self._fallback(
                intent="GENERAL_QA",
                confidence=0.2,
                reason="用户没有输入可识别的消息。",
                normalized_message=message,
                suggested_reply="请说明你想生成资产、修改模块树，还是分析当前内容。",
            )

        system_prompt = self._build_system_prompt()
        user_prompt = self._build_user_prompt(
            request=request,
            message=message,
        )

        try:
            llm_result = await self.llm_service.generate_structured_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                output_schema_name="assistant_intent_classification",
                output_json_schema=ASSISTANT_INTENT_CLASSIFICATION_JSON_SCHEMA,
            )

            parsed_json = self._strip_code_fence(llm_result.text)
            data = json.loads(parsed_json)

            artifact = AssistantIntentClassificationArtifact.model_validate(
                {
                    **data,
                    "artifact_type": "ASSISTANT_INTENT_CLASSIFICATION",
                    "source": "LLM",
                }
            )

            return artifact

        except Exception as exc:
            return self._heuristic_classify(
                message=message,
                reason=f"LLM 意图分类失败，已使用 Python 兜底规则：{exc}",
            )

    def _build_system_prompt(self) -> str:
        return "\n".join(
            [
                "你是 CodeMind Studio 的 AI Assistant 意图分类器。",
                "你只负责判断用户当前消息应该触发哪个平台能力，不要生成业务内容，不要生成代码。",
                "输出必须严格符合 JSON Schema。",
                "",
                "可选 intent：",
                "1. GENERATE_MODULE_TREE：当前项目没有模块树，或用户明确要求从业务描述生成模块树。",
                "2. PROPOSE_MODULE_TREE_PATCH：用户要新增、删除、移动、重命名、调整模块树节点，或根据附件补全/修改模块树。",
                "3. GENERATE_REQUIREMENT_ARTIFACT：用户要生成需求资产，例如需求、流程、页面规范、接口、测试、原型、开发说明、当前节点核心资产。",
                "4. EXPLAIN_CURRENT_ARTIFACT：用户要解释、检查、评审、找问题、看缺失。",
                "5. ANALYZE_ATTACHMENT：用户只要求分析附件，尚未明确要修改模块树或生成资产。",
                "6. GENERAL_QA：不属于以上操作。",
                "",
                "需求资产映射：",
                "- 需求、PRD、需求说明、页面需求、功能点需求、场景需求、业务域概览 => MODULE_PRD / GENERATE_MODULE_PRD。",
                "- 流程、业务流程、交互流程、功能点流程 => BUSINESS_FLOW / GENERATE_BUSINESS_FLOW。",
                "- 页面规范、页面地图、页面清单、场景页面清单 => PAGE_SPEC / GENERATE_PAGE_SPEC。",
                "- 数据模型、表结构、数据库、ViewModel => DATA_MODEL / GENERATE_DATA_MODEL。",
                "- 接口、接口契约、接口文档、功能点接口、页面接口、API => API_CONTRACT / GENERATE_API_CONTRACT。",
                "- 测试、测试用例、测试规格、验收标准 => TEST_SPEC / GENERATE_TEST_SPEC。",
                "- 原型、交互原型、UI 契约、Mock 预览、模拟界面 => UI_CONTRACT / GENERATE_UI_CONTRACT。",
                "- 开发说明、开发交接包、交付包 => DEV_HANDOFF_PACKAGE / GENERATE_DEV_HANDOFF_PACKAGE。",
                "- 当前节点核心资产、一键生成、完整链路、完整资产、全套资产 => fullChain=true, artifactType=FULL_CHAIN, taskType=null, coreOnly=true。",
                "",
                "重要原则：",
                "- 用户说“生成功能点需求”“把这个功能点需求写一下”必须分类为 GENERATE_REQUIREMENT_ARTIFACT，artifactType=MODULE_PRD，taskType=GENERATE_MODULE_PRD。",
                "- 用户说“生成当前节点核心资产”必须分类为 GENERATE_REQUIREMENT_ARTIFACT，fullChain=true，coreOnly=true。",
                "- 用户说“给用户中心增加密码找回页”“把发送验证码放到注册页下面”必须分类为 PROPOSE_MODULE_TREE_PATCH。",
                "- 用户说“根据附件补全模块树”“按这个文档改模块树”必须分类为 PROPOSE_MODULE_TREE_PATCH。",
                "- 用户说“根据附件生成当前功能点需求”“按这个文档生成接口契约”“根据 Excel 生成功能点测试”必须分类为 GENERATE_REQUIREMENT_ARTIFACT，并按目标资产设置 artifactType/taskType。",
                "- 用户只说“分析附件”“看看这个文件”“总结这个文档”，且没有明确要求修改模块树或生成需求资产时，分类为 ANALYZE_ATTACHMENT。",
                "- 用户说“看看这个资产有什么问题”“缺什么”“是否合理”“是否一致”“接口和页面规范一致吗”“有没有冲突/遗漏”分类为 EXPLAIN_CURRENT_ARTIFACT。",
                "- 如果当前节点不是 PAGE，但用户想生成原型，仍分类为 GENERATE_REQUIREMENT_ARTIFACT，后端会做节点约束提示。",
            ]
        )

    def _build_user_prompt(
        self,
        *,
        request: AgentTaskRequest,
        message: str,
    ) -> str:
        input_payload = request.input or {}
        context_package = request.context_package or {}

        payload = {
            "task_id": request.task_id,
            "project_id": request.project_id,
            "project_code": request.project_code,
            "module_id": request.module_id,
            "task_type": request.task_type,
            "message": message,
            "selected_node_id": input_payload.get("selected_node_id"),
            "selected_artifact_type": input_payload.get("selected_artifact_type"),
            "module_tree_exists": input_payload.get("module_tree_exists"),
            "attachment_contexts": input_payload.get("attachment_contexts")
            or context_package.get("attachment_contexts")
            or [],
            "module_tree_excerpt": self._compact_module_tree(
                input_payload.get("module_tree")
                or context_package.get("module_tree")
                or []
            ),
        }

        return "\n".join(
            [
                "请判断下面用户消息的意图，并只输出 JSON。",
                "",
                json.dumps(payload, ensure_ascii=False, default=str),
            ]
        )

    def _extract_message(
        self,
        request: AgentTaskRequest,
    ) -> str:
        input_payload = request.input or {}

        for key in [
            "message",
            "text",
            "instruction",
            "user_instruction",
            "requirementText",
            "requirement_text",
        ]:
            value = input_payload.get(key)

            if value:
                return str(value)

        if request.requirement_text:
            return request.requirement_text

        return ""

    def _compact_module_tree(
        self,
        module_tree: Any,
    ) -> Any:
        if not isinstance(module_tree, list):
            return module_tree

        def compact_node(node: Any) -> Any:
            if not isinstance(node, dict):
                return node

            children = node.get("children") or []

            return {
                "id": node.get("id"),
                "parentId": node.get("parentId"),
                "moduleName": node.get("moduleName") or node.get("name") or node.get("title"),
                "moduleType": node.get("moduleType") or node.get("type"),
                "modulePath": node.get("modulePath"),
                "children": [compact_node(child) for child in children[:20]],
            }

        return [compact_node(node) for node in module_tree[:30]]

    def _heuristic_classify(
        self,
        *,
        message: str,
        reason: str,
    ) -> AssistantIntentClassificationArtifact:
        text = message.strip()
        upper = text.upper()

        if self._contains_any(
            text,
            [
                "新增",
                "增加",
                "添加",
                "删除",
                "移除",
                "移动",
                "放到",
                "放在",
                "归到",
                "重命名",
                "改名",
                "调整",
                "修改",
            ],
        ) and self._contains_any(
            text,
            [
                "模块树",
                "节点",
                "业务域",
                "模块",
                "子模块",
                "页面",
                "功能点",
                "登录",
                "注册",
                "密码找回",
                "列表",
                "详情",
            ],
        ):
            return self._fallback(
                intent="PROPOSE_MODULE_TREE_PATCH",
                confidence=0.72,
                reason=reason,
                normalized_message=text,
                suggested_reply="我会按你的描述生成模块树变更提案，请确认后再应用。",
            )

        if self._contains_any(
            text,
            [
                "根据这个文件",
                "根据该文件",
                "根据这个文档",
                "根据该文档",
                "根据附件",
                "按这个文件",
                "按这个文档",
            ],
        ) and self._contains_any(
            text,
            [
                "模块树",
                "节点",
                "页面和功能点",
                "页面、功能点",
                "功能清单",
                "补全模块",
                "补充模块",
                "完善模块",
            ],
        ):
            return self._fallback(
                intent="PROPOSE_MODULE_TREE_PATCH",
                confidence=0.74,
                reason=reason,
                normalized_message=text,
                suggested_reply="我会根据附件内容生成模块树变更提案，请确认后再应用。",
            )

        if self._contains_any(
            text,
            [
                "核心资产",
                "完整链路",
                "全链路",
                "完整资产",
                "一键生成",
                "全部生成",
                "所有资产",
            ],
        ):
            return self._fallback(
                intent="GENERATE_REQUIREMENT_ARTIFACT",
                confidence=0.72,
                reason=reason,
                normalized_message=text,
                suggested_reply="我会为当前节点生成核心需求资产。",
                artifact_type="FULL_CHAIN",
                task_type=None,
                full_chain=True,
                core_only=True,
            )

        if self._contains_any(
            text,
            [
                "生成",
                "写一下",
                "出一下",
                "做一下",
                "搞一下",
                "弄一下",
                "开始生成",
                "执行生成",
            ],
        ) and self._contains_any(
            text,
            [
                "需求",
                "PRD",
                "流程",
                "页面规范",
                "页面地图",
                "数据模型",
                "接口",
                "API",
                "测试",
                "用例",
                "原型",
                "UI",
                "开发说明",
                "交接包",
            ],
        ):
            artifact_type, task_type = self._resolve_artifact_hint(text, upper)

            return self._fallback(
                intent="GENERATE_REQUIREMENT_ARTIFACT",
                confidence=0.7,
                reason=reason,
                normalized_message=text,
                suggested_reply="我会为当前节点创建对应的需求资产生成任务。",
                artifact_type=artifact_type,
                task_type=task_type,
                full_chain=False,
                core_only=True,
            )

        if self._contains_any(
            text,
            [
                "解释",
                "分析",
                "检查",
                "哪里有问题",
                "缺什么",
                "评审",
                "评价",
                "是否合理",
                "是否完整",
                "是否一致",
                "一致吗",
                "一致性",
                "冲突",
                "矛盾",
                "遗漏",
                "对不上",
            ],
        ):
            return self._fallback(
                intent="EXPLAIN_CURRENT_ARTIFACT",
                confidence=0.68,
                reason=reason,
                normalized_message=text,
                suggested_reply="我会结合当前资产上下文进行分析。",
            )

        if self._contains_any(
            text,
            [
                "读取附件",
                "分析附件",
                "看看附件",
                "看下附件",
                "看看这个文件",
                "看下这个文件",
                "总结附件",
                "总结文档",
                "提取附件内容",
            ],
        ):
            return self._fallback(
                intent="ANALYZE_ATTACHMENT",
                confidence=0.66,
                reason=reason,
                normalized_message=text,
                suggested_reply="我会先检查附件解析状态，并给出可用于需求生成的内容摘要。",
            )

        return self._fallback(
            intent="GENERAL_QA",
            confidence=0.3,
            reason=reason,
            normalized_message=text,
            suggested_reply="我已收到你的问题。你可以直接说：生成功能点需求、生成当前节点核心资产、给用户中心增加一个页面。",
        )

    def _resolve_artifact_hint(
        self,
        text: str,
        upper: str,
    ) -> tuple[str | None, str | None]:
        if "UI_CONTRACT" in upper or self._contains_any(
            text,
            ["交互原型", "界面原型", "UI原型", "UI 原型", "UI契约", "UI 契约", "Mock", "mock"],
        ):
            return "UI_CONTRACT", "GENERATE_UI_CONTRACT"

        if "API_CONTRACT" in upper or self._contains_any(text, ["接口", "API", "api", "端点"]):
            return "API_CONTRACT", "GENERATE_API_CONTRACT"

        if "TEST_SPEC" in upper or self._contains_any(text, ["测试", "用例", "验收"]):
            return "TEST_SPEC", "GENERATE_TEST_SPEC"

        if "DATA_MODEL" in upper or self._contains_any(text, ["数据模型", "表结构", "数据库", "ViewModel"]):
            return "DATA_MODEL", "GENERATE_DATA_MODEL"

        if "PAGE_SPEC" in upper or self._contains_any(text, ["页面规范", "页面地图", "页面清单"]):
            return "PAGE_SPEC", "GENERATE_PAGE_SPEC"

        if "BUSINESS_FLOW" in upper or self._contains_any(text, ["流程", "业务流程", "交互流程"]):
            return "BUSINESS_FLOW", "GENERATE_BUSINESS_FLOW"

        if "DEV_HANDOFF_PACKAGE" in upper or self._contains_any(text, ["开发说明", "交接包", "开发交接包"]):
            return "DEV_HANDOFF_PACKAGE", "GENERATE_DEV_HANDOFF_PACKAGE"

        return "MODULE_PRD", "GENERATE_MODULE_PRD"

    def _fallback(
        self,
        *,
        intent: str,
        confidence: float,
        reason: str,
        normalized_message: str,
        suggested_reply: str,
        artifact_type: str | None = None,
        task_type: str | None = None,
        full_chain: bool = False,
        core_only: bool = True,
    ) -> AssistantIntentClassificationArtifact:
        return AssistantIntentClassificationArtifact(
            intent=intent,  # type: ignore[arg-type]
            confidence=confidence,
            source="LLM_FALLBACK",
            reason=reason,
            normalizedMessage=normalized_message,
            suggestedReply=suggested_reply,
            requiresUserConfirmation=False,
            artifactType=artifact_type,  # type: ignore[arg-type]
            taskType=task_type,  # type: ignore[arg-type]
            fullChain=full_chain,
            coreOnly=core_only,
            riskNotes=[] if confidence >= 0.7 else [reason],
        )

    def _contains_any(
        self,
        text: str,
        keywords: list[str],
    ) -> bool:
        return any(keyword in text for keyword in keywords)

    def _strip_code_fence(
        self,
        text: str,
    ) -> str:
        cleaned = text.strip()

        if cleaned.startswith("```json"):
            cleaned = cleaned.removeprefix("```json").strip()

        if cleaned.startswith("```"):
            cleaned = cleaned.removeprefix("```").strip()

        if cleaned.endswith("```"):
            cleaned = cleaned.removesuffix("```").strip()

        return cleaned.strip()

