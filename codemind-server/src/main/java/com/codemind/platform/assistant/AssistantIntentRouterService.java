package com.codemind.platform.assistant;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import com.codemind.platform.module.ProjectModuleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantIntentRouterService {

    public static final String INTENT_GENERATE_MODULE_TREE = "GENERATE_MODULE_TREE";
    public static final String INTENT_PROPOSE_MODULE_TREE_PATCH = "PROPOSE_MODULE_TREE_PATCH";
    public static final String INTENT_EXECUTE_UI_COMMAND = "EXECUTE_UI_COMMAND";
    public static final String INTENT_GENERATE_REQUIREMENT_ARTIFACT = "GENERATE_REQUIREMENT_ARTIFACT";
    public static final String INTENT_EXPLAIN_CURRENT_ARTIFACT = "EXPLAIN_CURRENT_ARTIFACT";
    public static final String INTENT_ANALYZE_ATTACHMENT = "ANALYZE_ATTACHMENT";
    public static final String INTENT_GENERAL_QA = "GENERAL_QA";

    private final PythonAgentClient pythonAgentClient;

    public AssistantIntentRouterService(
            PythonAgentClient pythonAgentClient
    ) {
        this.pythonAgentClient = pythonAgentClient;
    }

    public AssistantIntentClassificationResult classify(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            boolean moduleTreeExists,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (request.getExpectedIntent() != null && !request.getExpectedIntent().isBlank()) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    normalizeIntent(request.getExpectedIntent()),
                    "EXPECTED_INTENT",
                    1.0,
                    "前端显式指定 expectedIntent。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        if (!moduleTreeExists) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_GENERATE_MODULE_TREE,
                    "RULE",
                    1.0,
                    "当前项目尚无模块树，用户输入应作为生成模块树的原始需求。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        AssistantIntentClassificationResult ruleResult = classifyByHighConfidenceRule(
                request,
                messageText,
                attachmentContexts
        );

        if (ruleResult != null) {
            return ruleResult;
        }

        AssistantIntentClassificationResult llmResult = classifyByLlm(
                projectCode,
                request,
                messageText,
                moduleTreeExists,
                moduleTree,
                attachmentContexts
        );

        if (isUsableLlmResult(llmResult)) {
            return llmResult;
        }

        AssistantIntentClassificationResult fallback = classifyByFallbackRule(
                request,
                messageText,
                attachmentContexts
        );

        if (fallback != null) {
            return fallback;
        }

        AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                INTENT_GENERAL_QA,
                "FALLBACK",
                0.2,
                "规则和大模型都未能识别为当前支持的明确操作。"
        );
        result.setNormalizedMessage(messageText);
        return result;
    }

    private AssistantIntentClassificationResult classifyByHighConfidenceRule(
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        boolean hasAttachment = request.getAttachments() != null && !request.getAttachments().isEmpty();
        boolean hasParsedAttachmentText = hasParsedAttachmentText(attachmentContexts);
        boolean hasImageAttachment = hasImageAttachment(request, attachmentContexts);

        if (isUiCommandIntent(messageText)) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_EXECUTE_UI_COMMAND,
                    "RULE",
                    0.94,
                    "用户要求通过聊天触发页面已有操作。"
            );
            result.setNormalizedMessage(messageText);
            applyRuleArtifactHints(result, messageText);
            return result;
        }

        if (hasImageAttachment && isImagePageUiChangeIntent(messageText)) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_ANALYZE_ATTACHMENT,
                    "RULE",
                    0.9,
                    "用户携带图片/原型图并要求处理页面或交互，先进入附件驱动页面变更分析。"
            );
            result.setNormalizedMessage(messageText);
            applyRuleArtifactHints(result, messageText);
            return result;
        }

        if (hasAttachment && isAttachmentArtifactDiffIntent(messageText, request.getSelectedArtifactType())) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_ANALYZE_ATTACHMENT,
                    "RULE",
                    0.9,
                    "用户要求将附件材料与当前资产做差异对比。"
            );
            result.setNormalizedMessage(messageText);
            applyRuleArtifactHints(result, messageText);
            return result;
        }

        if (isModuleTreePatchIntent(messageText)) {
            if (hasAttachment && !hasParsedAttachmentText) {
                AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                        INTENT_ANALYZE_ATTACHMENT,
                        "RULE",
                        0.9,
                        "用户提到模块树修改，但附件尚无可用解析文本。"
                );
                result.setNormalizedMessage(messageText);
                return result;
            }

            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_PROPOSE_MODULE_TREE_PATCH,
                    "RULE",
                    0.92,
                    "用户明确要求新增、删除、移动或修改模块树节点。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        if (isClearRequirementArtifactIntent(messageText, request.getSelectedArtifactType())) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_GENERATE_REQUIREMENT_ARTIFACT,
                    "RULE",
                    0.9,
                    hasAttachment
                            ? "用户明确要求基于附件或当前上下文生成需求资产。"
                            : "用户明确要求生成需求资产。"
            );
            result.setNormalizedMessage(messageText);
            applyRuleArtifactHints(result, messageText);
            return result;
        }

        if (hasParsedAttachmentText && isAttachmentDrivenRequirementChangeIntent(messageText)) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_PROPOSE_MODULE_TREE_PATCH,
                    "RULE",
                    0.9,
                    "用户要求根据附件补全或修改模块树。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        if (hasAttachment && isAttachmentAnalysisOnlyIntent(messageText)) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_ANALYZE_ATTACHMENT,
                    "RULE",
                    0.82,
                    "用户携带附件并要求查看、读取或分析附件内容。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        return null;
    }

    private AssistantIntentClassificationResult classifyByFallbackRule(
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        boolean hasAttachment = request.getAttachments() != null && !request.getAttachments().isEmpty();

        if (hasAttachment) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_ANALYZE_ATTACHMENT,
                    "FALLBACK_RULE",
                    0.7,
                    "用户携带附件，但没有明确说明应生成模块树变更或需求资产。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        if (isExplainArtifactIntent(messageText)) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_EXPLAIN_CURRENT_ARTIFACT,
                    "FALLBACK_RULE",
                    0.72,
                    "用户希望解释、检查或评审当前资产。"
            );
            result.setNormalizedMessage(messageText);
            return result;
        }

        if (containsAny(messageText, List.of("需求", "接口", "测试", "原型", "流程", "开发说明", "交接包"))) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_GENERATE_REQUIREMENT_ARTIFACT,
                    "FALLBACK_RULE",
                    0.65,
                    "用户提到了需求资产对象，按生成需求资产处理。"
            );
            result.setNormalizedMessage(messageText);
            applyRuleArtifactHints(result, messageText);
            return result;
        }

        return null;
    }

    private AssistantIntentClassificationResult classifyByLlm(
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            boolean moduleTreeExists,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        try {
            Object rawResponse = pythonAgentClient.classifyAssistantIntent(
                    UUID.randomUUID(),
                    projectCode,
                    request.getSelectedNodeId(),
                    request.getSelectedArtifactType(),
                    messageText,
                    moduleTree,
                    attachmentContexts,
                    moduleTreeExists
            );

            Map<String, Object> responseMap = toMap(rawResponse);
            Map<String, Object> content = toMap(responseMap.get("content"));

            if (content.isEmpty()) {
                content = responseMap;
            }

            AssistantIntentClassificationResult result = new AssistantIntentClassificationResult();
            result.setIntent(normalizeIntent(asString(content.get("intent"))));
            result.setConfidence(asDouble(content.get("confidence"), 0.0));
            result.setSource(asStringOrDefault(content.get("source"), "LLM"));
            result.setReason(asStringOrDefault(content.get("reason"), ""));
            result.setNormalizedMessage(asStringOrDefault(content.get("normalizedMessage"), messageText));
            result.setSuggestedReply(asStringOrDefault(content.get("suggestedReply"), ""));
            result.setRequiresUserConfirmation(asBoolean(content.get("requiresUserConfirmation"), false));
            result.setArtifactType(asString(content.get("artifactType")));
            result.setTaskType(asString(content.get("taskType")));
            result.setFullChain(asBoolean(content.get("fullChain"), false));
            result.setCoreOnly(asBoolean(content.get("coreOnly"), true));
            result.setRiskNotes(asStringList(content.get("riskNotes")));

            return result;
        } catch (Exception ex) {
            AssistantIntentClassificationResult result = AssistantIntentClassificationResult.of(
                    INTENT_GENERAL_QA,
                    "LLM_FAILED",
                    0.0,
                    "LLM 意图分类失败：" + ex.getMessage()
            );
            result.setNormalizedMessage(messageText);
            result.setRiskNotes(List.of(ex.getMessage()));
            return result;
        }
    }

    private boolean isUsableLlmResult(
            AssistantIntentClassificationResult result
    ) {
        if (result == null || result.getIntent() == null || result.getIntent().isBlank()) {
            return false;
        }

        if (!isSupportedIntent(result.getIntent())) {
            return false;
        }

        if (INTENT_GENERAL_QA.equals(result.getIntent())) {
            return result.getConfidence() != null && result.getConfidence() >= 0.75;
        }

        return result.getConfidence() != null && result.getConfidence() >= 0.55;
    }

    private boolean isSupportedIntent(
            String intent
    ) {
        return List.of(
                INTENT_GENERATE_MODULE_TREE,
                INTENT_PROPOSE_MODULE_TREE_PATCH,
                INTENT_EXECUTE_UI_COMMAND,
                INTENT_GENERATE_REQUIREMENT_ARTIFACT,
                INTENT_EXPLAIN_CURRENT_ARTIFACT,
                INTENT_ANALYZE_ATTACHMENT,
                INTENT_GENERAL_QA
        ).contains(normalizeIntent(intent));
    }

    private String normalizeIntent(
            String intent
    ) {
        if (intent == null || intent.isBlank()) {
            return INTENT_GENERAL_QA;
        }

        String value = intent.trim().toUpperCase(Locale.ROOT);

        return isSupportedIntentValue(value) ? value : INTENT_GENERAL_QA;
    }

    private boolean isSupportedIntentValue(
            String value
    ) {
        return INTENT_GENERATE_MODULE_TREE.equals(value)
                || INTENT_PROPOSE_MODULE_TREE_PATCH.equals(value)
                || INTENT_EXECUTE_UI_COMMAND.equals(value)
                || INTENT_GENERATE_REQUIREMENT_ARTIFACT.equals(value)
                || INTENT_EXPLAIN_CURRENT_ARTIFACT.equals(value)
                || INTENT_ANALYZE_ATTACHMENT.equals(value)
                || INTENT_GENERAL_QA.equals(value);
    }

    private boolean isUiCommandIntent(
            String messageText
    ) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        String text = messageText.trim();

        boolean hasOpenVerb = containsAny(
                text,
                List.of(
                        "打开",
                        "查看",
                        "看一下",
                        "看看",
                        "显示",
                        "展开",
                        "切到",
                        "进入",
                        "跳到",
                        "预览",
                        "刷新"
                )
        );

        boolean hasUiObject = containsAny(
                text,
                List.of(
                        "质量检查",
                        "质量检测",
                        "版本历史",
                        "历史版本",
                        "影响分析",
                        "交互预览",
                        "预览界面",
                        "页面预览",
                        "交互原型预览",
                        "UI预览",
                        "UI 预览",
                        "交互原型",
                        "UI_CONTRACT",
                        "UI CONTRACT",
                        "准备度",
                        "开发 Agent",
                        "开发Agent",
                        "需求 Agent",
                        "需求Agent",
                        "任务中心",
                        "项目任务中心",
                        "当前资产",
                        "页面规范",
                        "业务流程",
                        "数据模型",
                        "接口契约",
                        "测试规格",
                        "开发交接包"
                )
        );

        return hasOpenVerb && hasUiObject;
    }


    private boolean isModuleTreePatchIntent(
            String messageText
    ) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        String text = messageText.trim();

        boolean hasChangeVerb = containsAny(
                text,
                List.of(
                        "新增",
                        "增加",
                        "添加",
                        "补充",
                        "创建",
                        "新建",
                        "删除",
                        "移除",
                        "去掉",
                        "移动",
                        "挪到",
                        "放到",
                        "归到",
                        "调整",
                        "修改",
                        "重命名",
                        "改名",
                        "拆分",
                        "合并"
                )
        );

        boolean hasTreeObject = containsAny(
                text,
                List.of(
                        "模块树",
                        "节点",
                        "业务域",
                        "模块",
                        "子模块",
                        "页面",
                        "页",
                        "功能点",
                        "功能",
                        "路由",
                        "登录",
                        "注册",
                        "密码找回",
                        "审批",
                        "列表",
                        "详情",
                        "表单"
                )
        );

        boolean hasPlacementExpression = containsAny(
                text,
                List.of(
                        "放在",
                        "放到",
                        "归到",
                        "下面",
                        "下方",
                        "上面",
                        "移动到",
                        "挪到",
                        "挂到",
                        "属于"
                )
        );

        return hasChangeVerb && (hasTreeObject || hasPlacementExpression);
    }

    private boolean isClearRequirementArtifactIntent(
            String messageText,
            String selectedArtifactType
    ) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        String text = messageText.trim();
        String upperText = text.toUpperCase(Locale.ROOT);

        boolean hasGenerateVerb = containsAny(
                text,
                List.of(
                        "生成",
                        "开始生成",
                        "执行生成",
                        "确认生成",
                        "重新生成",
                        "重生成",
                        "一键生成",
                        "创建",
                        "输出",
                        "补全",
                        "完善",
                        "重新跑",
                        "再生成",
                        "开始",
                        "执行",
                        "写一下",
                        "出一下",
                        "做一下",
                        "搞一下",
                        "弄一下",
                        "整理"
                )
        );

        boolean hasSelectedArtifactType = selectedArtifactType != null && !selectedArtifactType.isBlank();

        boolean hasArtifactObject =
                upperText.contains("PRD") ||
                        upperText.contains("MODULE_PRD") ||
                        upperText.contains("BUSINESS_FLOW") ||
                        upperText.contains("PAGE_SPEC") ||
                        upperText.contains("DATA_MODEL") ||
                        upperText.contains("API_CONTRACT") ||
                        upperText.contains("TEST_SPEC") ||
                        upperText.contains("UI_CONTRACT") ||
                        upperText.contains("DEV_HANDOFF_PACKAGE") ||
                        containsAny(
                                text,
                                List.of(
                                        "需求",
                                        "需求说明",
                                        "需求文档",
                                        "模块需求",
                                        "页面需求",
                                        "功能需求",
                                        "功能点需求",
                                        "业务域概览",
                                        "场景需求",
                                        "业务流程",
                                        "流程说明",
                                        "交互流程",
                                        "功能点流程",
                                        "页面规范",
                                        "页面地图",
                                        "页面清单",
                                        "数据模型",
                                        "表结构",
                                        "数据库",
                                        "实体模型",
                                        "接口规划",
                                        "接口清单",
                                        "接口契约",
                                        "接口文档",
                                        "接口规范",
                                        "接口",
                                        "功能点接口",
                                        "页面接口",
                                        "后端接口",
                                        "端点",
                                        "API接口",
                                        "API 接口",
                                        "测试规格",
                                        "测试用例",
                                        "测试方案",
                                        "验收标准",
                                        "功能点测试",
                                        "交互原型",
                                        "界面原型",
                                        "UI原型",
                                        "UI 原型",
                                        "UI契约",
                                        "UI 契约",
                                        "开发交接包",
                                        "交接包",
                                        "开发说明",
                                        "功能点开发说明",
                                        "核心资产",
                                        "完整链路",
                                        "全链路",
                                        "完整需求资产",
                                        "完整资产",
                                        "全套资产",
                                        "所有资产",
                                        "后续链路"
                                )
                        );

        return hasGenerateVerb && (hasArtifactObject || hasSelectedArtifactType);
    }

    private void applyRuleArtifactHints(
            AssistantIntentClassificationResult result,
            String messageText
    ) {
        String text = messageText == null ? "" : messageText;
        String upperText = text.toUpperCase(Locale.ROOT);

        if (containsAny(text, List.of("核心资产", "完整链路", "全链路", "完整需求资产", "完整资产", "全套资产", "所有资产", "一键生成", "全部生成", "全都生成"))) {
            result.setFullChain(true);
            result.setCoreOnly(true);
            result.setArtifactType("FULL_CHAIN");
            result.setTaskType(null);
            return;
        }

        if (upperText.contains("UI_CONTRACT") || containsAny(text, List.of("交互原型", "界面原型", "UI原型", "UI 原型", "UI契约", "UI 契约", "Mock", "mock"))) {
            result.setArtifactType("UI_CONTRACT");
            result.setTaskType("GENERATE_UI_CONTRACT");
            return;
        }

        if (upperText.contains("API_CONTRACT") || containsAny(text, List.of("接口", "API", "api", "端点"))) {
            result.setArtifactType("API_CONTRACT");
            result.setTaskType("GENERATE_API_CONTRACT");
            return;
        }

        if (upperText.contains("TEST_SPEC") || containsAny(text, List.of("测试", "用例", "验收"))) {
            result.setArtifactType("TEST_SPEC");
            result.setTaskType("GENERATE_TEST_SPEC");
            return;
        }

        if (upperText.contains("DATA_MODEL") || containsAny(text, List.of("数据模型", "表结构", "数据库", "实体模型", "字段模型", "ViewModel", "视图模型"))) {
            result.setArtifactType("DATA_MODEL");
            result.setTaskType("GENERATE_DATA_MODEL");
            return;
        }

        if (upperText.contains("PAGE_SPEC") || containsAny(text, List.of("页面规范", "页面地图", "页面清单", "页面说明", "页面设计"))) {
            result.setArtifactType("PAGE_SPEC");
            result.setTaskType("GENERATE_PAGE_SPEC");
            return;
        }

        if (upperText.contains("BUSINESS_FLOW") || containsAny(text, List.of("流程", "业务流程", "交互流程", "流程图", "操作流程"))) {
            result.setArtifactType("BUSINESS_FLOW");
            result.setTaskType("GENERATE_BUSINESS_FLOW");
            return;
        }

        if (upperText.contains("DEV_HANDOFF_PACKAGE") || containsAny(text, List.of("开发交接包", "交接包", "开发说明", "开发文档"))) {
            result.setArtifactType("DEV_HANDOFF_PACKAGE");
            result.setTaskType("GENERATE_DEV_HANDOFF_PACKAGE");
            return;
        }

        if (upperText.contains("PRD") || containsAny(text, List.of("需求", "需求说明", "需求文档", "功能点需求", "页面需求", "模块需求", "场景需求"))) {
            result.setArtifactType("MODULE_PRD");
            result.setTaskType("GENERATE_MODULE_PRD");
        }
    }

    private boolean isExplainArtifactIntent(
            String messageText
    ) {
        return containsAny(
                messageText,
                List.of(
                        "解释",
                        "分析",
                        "检查",
                        "哪里有问题",
                        "缺什么",
                        "评审",
                        "评价",
                        "是否完整",
                        "是否合理",
                        "是否一致",
                        "一致吗",
                        "一致性",
                        "冲突",
                        "矛盾",
                        "遗漏",
                        "对不上",
                        "帮我看",
                        "有什么问题"
                )
        );
    }

    private boolean hasParsedAttachmentText(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return false;
        }

        return attachmentContexts.stream().anyMatch(
                context -> context.getParsedTextExcerpt() != null &&
                        !context.getParsedTextExcerpt().isBlank()
        );
    }

    private boolean hasImageAttachment(
            AssistantMessageRequest request,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts != null) {
            for (AssistantAttachmentParsedContext context : attachmentContexts) {
                String mimeType = context == null ? null : context.getMimeType();
                if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    return true;
                }
            }
        }

        if (request == null || request.getAttachments() == null) {
            return false;
        }

        for (AssistantAttachmentRef attachment : request.getAttachments()) {
            String mimeType = attachment == null ? null : attachment.getMimeType();
            if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return true;
            }
        }

        return false;
    }

    private boolean isImagePageUiChangeIntent(
            String messageText
    ) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        String text = messageText.trim().toLowerCase(Locale.ROOT).replaceAll("\s+", "");

        boolean mentionsImageMaterial = containsAny(
                text,
                List.of(
                        "图片",
                        "图",
                        "截图",
                        "原型图",
                        "设计稿",
                        "界面图",
                        "页面图",
                        "这张图",
                        "根据图",
                        "按图",
                        "看图",
                        "mockup",
                        "figma",
                        "ui"
                )
        );

        boolean mentionsPageOrInteraction = containsAny(
                text,
                List.of(
                        "页面",
                        "页",
                        "界面",
                        "交互",
                        "布局",
                        "表单",
                        "字段",
                        "按钮",
                        "入口",
                        "卡片",
                        "列表",
                        "详情",
                        "登录",
                        "注册",
                        "页面规范",
                        "ui_contract",
                        "ui契约",
                        "交互原型"
                )
        );

        boolean hasChangeIntent = containsAny(
                text,
                List.of(
                        "优化",
                        "修改",
                        "调整",
                        "完善",
                        "补全",
                        "补充",
                        "对齐",
                        "检查",
                        "看看",
                        "分析",
                        "生成建议",
                        "变更建议",
                        "缺什么",
                        "缺少什么"
                )
        );

        return mentionsImageMaterial && mentionsPageOrInteraction && hasChangeIntent;
    }

    private boolean isAttachmentArtifactDiffIntent(
            String messageText,
            String selectedArtifactType
    ) {
        if (selectedArtifactType == null || selectedArtifactType.isBlank()) {
            return false;
        }

        String normalized = messageText == null
                ? ""
                : messageText.trim().toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            return false;
        }

        return containsAny(
                normalized,
                List.of(
                        "附件与当前资产差异",
                        "附件和当前资产差异",
                        "附件与当前需求差异",
                        "附件和当前需求差异",
                        "附件与当前文档差异",
                        "附件和当前文档差异",
                        "对比当前资产",
                        "对比当前需求",
                        "对比当前文档",
                        "差异对比",
                        "差异分析",
                        "有什么差异",
                        "哪里不一致",
                        "不一致",
                        "冲突",
                        "缺失",
                        "遗漏",
                        "已覆盖",
                        "compare",
                        "diff"
                )
        );
    }

    private boolean isAttachmentDrivenRequirementChangeIntent(
            String messageText
    ) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        boolean hasAttachmentReference = containsAny(
                messageText,
                List.of(
                        "根据这个文件",
                        "根据该文件",
                        "根据这个文档",
                        "根据该文档",
                        "根据附件",
                        "按这个文件",
                        "按该文件",
                        "按这个文档",
                        "按该文档",
                        "按附件",
                        "从文档提取",
                        "从文件提取"
                )
        );

        boolean hasModuleTreeTarget = containsAny(
                messageText,
                List.of(
                        "模块树",
                        "节点",
                        "业务域",
                        "子模块",
                        "页面和功能点",
                        "页面、功能点",
                        "页面列表",
                        "功能清单",
                        "功能点清单",
                        "补全用户中心",
                        "补全模块",
                        "补充模块",
                        "完善模块"
                )
        );

        boolean hasTreePatchVerb = containsAny(
                messageText,
                List.of(
                        "补全模块树",
                        "补充模块树",
                        "完善模块树",
                        "修改模块树",
                        "调整模块树",
                        "生成模块树修改",
                        "改模块树",
                        "更新模块树",
                        "按文件修改",
                        "按文档修改"
                )
        );

        return hasAttachmentReference && (hasModuleTreeTarget || hasTreePatchVerb);
    }

    private boolean isAttachmentAnalysisOnlyIntent(
            String messageText
    ) {
        return containsAny(
                messageText,
                List.of(
                        "读取附件",
                        "分析附件",
                        "看看附件",
                        "看下附件",
                        "看看这个文件",
                        "看下这个文件",
                        "看看这个文档",
                        "看下这个文档",
                        "总结附件",
                        "总结文档",
                        "提取附件内容"
                )
        );
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return keywords.stream().anyMatch(text::contains);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(
            Object value
    ) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            return result;
        }

        return new LinkedHashMap<>();
    }

    private String asString(
            Object value
    ) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();

        return text.isBlank() ? null : text;
    }

    private String asStringOrDefault(
            Object value,
            String fallback
    ) {
        String text = asString(value);

        return text == null ? fallback : text;
    }

    private Double asDouble(
            Object value,
            double fallback
    ) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value == null) {
            return fallback;
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean asBoolean(
            Object value,
            boolean fallback
    ) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value == null) {
            return fallback;
        }

        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);

        if ("true".equals(text)) {
            return true;
        }

        if ("false".equals(text)) {
            return false;
        }

        return fallback;
    }

    private List<String> asStringList(
            Object value
    ) {
        if (value instanceof List<?> source) {
            List<String> result = new ArrayList<>();

            for (Object item : source) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }

            return result;
        }

        return new ArrayList<>();
    }
}
