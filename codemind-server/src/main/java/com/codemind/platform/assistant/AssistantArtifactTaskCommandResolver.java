package com.codemind.platform.assistant;

import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AssistantArtifactTaskCommandResolver {

    public AssistantArtifactTaskCommand resolve(
            String messageText
    ) {
        return resolve(messageText, null, null);
    }

    public AssistantArtifactTaskCommand resolve(
            String messageText,
            String selectedArtifactType
    ) {
        return resolve(messageText, selectedArtifactType, null);
    }

    public AssistantArtifactTaskCommand resolve(
            String messageText,
            String selectedArtifactType,
            AssistantIntentClassificationResult classification
    ) {
        String text = resolveEffectiveText(messageText, classification);

        if (text.isBlank()) {
            throw new IllegalArgumentException("请说明要生成哪类需求资产。");
        }

        boolean overwriteExisting = containsAny(
                text,
                List.of(
                        "重新生成",
                        "重生成",
                        "覆盖",
                        "强制生成",
                        "重新跑",
                        "再生成",
                        "做一下",
                        "帮我做",
                        "出一下",
                        "帮我出",
                        "写一下",
                        "编写",
                        "整理",
                        "搞一下",
                        "弄一下"
                )
        );

        if (classification != null && classification.isFullChain()) {
            return new AssistantArtifactTaskCommand(
                    true,
                    null,
                    "FULL_CHAIN",
                    classification.isCoreOnly() ? "当前节点核心资产" : "完整需求资产链路",
                    text,
                    overwriteExisting
            );
        }

        if (isFullChainIntent(text)) {
            return new AssistantArtifactTaskCommand(
                    true,
                    null,
                    "FULL_CHAIN",
                    isCoreOnlyIntent(text) ? "当前节点核心资产" : "完整需求资产链路",
                    text,
                    overwriteExisting
            );
        }

        TaskType taskType = resolveTaskType(
                text,
                selectedArtifactType,
                classification
        );

        return new AssistantArtifactTaskCommand(
                false,
                taskType,
                resolveArtifactType(taskType),
                resolveTitle(taskType),
                text,
                overwriteExisting
        );
    }

    private String resolveEffectiveText(
            String messageText,
            AssistantIntentClassificationResult classification
    ) {
        if (
                classification != null &&
                        classification.getNormalizedMessage() != null &&
                        !classification.getNormalizedMessage().isBlank()
        ) {
            return classification.getNormalizedMessage().trim();
        }

        return messageText == null ? "" : messageText.trim();
    }

    private boolean isFullChainIntent(
            String text
    ) {
        return containsAny(
                text,
                List.of(
                        "核心资产",
                        "当前节点核心资产",
                        "完整链路",
                        "全链路",
                        "完整需求资产",
                        "完整资产",
                        "全套资产",
                        "一键生成",
                        "全部生成",
                        "所有资产",
                        "后续链路",
                        "全都生成"
                )
        );
    }

    private boolean isCoreOnlyIntent(
            String text
    ) {
        return containsAny(
                text,
                List.of(
                        "核心资产",
                        "当前层级",
                        "当前节点",
                        "默认资产"
                )
        );
    }

    private TaskType resolveTaskType(
            String text,
            String selectedArtifactType,
            AssistantIntentClassificationResult classification
    ) {
        TaskType classifiedTaskType = resolveTaskTypeFromClassification(
                classification
        );

        if (classifiedTaskType != null) {
            return classifiedTaskType;
        }

        String normalized = text.toUpperCase(Locale.ROOT);

        if (
                normalized.contains("UI_CONTRACT") ||
                        normalized.contains("UI CONTRACT") ||
                        containsAny(text, List.of("交互原型", "界面原型", "UI原型", "UI 原型", "UI契约", "UI 契约", "Mock", "mock", "预览界面", "模拟界面", "原型界面"))
        ) {
            return TaskType.GENERATE_UI_CONTRACT;
        }

        if (
                normalized.contains("API_CONTRACT") ||
                        normalized.contains("API CONTRACT") ||
                        containsAny(text, List.of("接口契约", "接口文档", "接口规范", "功能点接口", "页面接口", "模块接口", "接口规划", "接口清单", "后端接口", "API接口", "API 接口", "端点", "接口", "API", "api"))
        ) {
            return TaskType.GENERATE_API_CONTRACT;
        }

        if (
                normalized.contains("DATA_MODEL") ||
                        normalized.contains("DATA MODEL") ||
                        containsAny(text, List.of("数据模型", "表结构", "数据库", "实体模型", "字段模型", "ViewModel", "视图模型", "数据表", "数据字典", "数据主题", "数据约束"))
        ) {
            return TaskType.GENERATE_DATA_MODEL;
        }

        if (
                normalized.contains("TEST_SPEC") ||
                        normalized.contains("TEST SPEC") ||
                        containsAny(text, List.of("测试规格", "测试用例", "测试方案", "验收标准", "验收用例", "测试点", "功能点测试", "页面测试", "用例"))
        ) {
            return TaskType.GENERATE_TEST_SPEC;
        }

        if (
                normalized.contains("DEV_HANDOFF_PACKAGE") ||
                        normalized.contains("HANDOFF") ||
                        containsAny(text, List.of("开发交接包", "交接包", "开发说明", "功能点开发说明", "页面开发交接包", "开发交付", "交付包", "研发交接", "开发文档"))
        ) {
            return TaskType.GENERATE_DEV_HANDOFF_PACKAGE;
        }

        if (
                normalized.contains("PAGE_SPEC") ||
                        normalized.contains("PAGE SPEC") ||
                        containsAny(text, List.of("页面规范", "页面地图", "页面清单", "场景页面清单", "页面说明", "页面设计", "模块/页面地图"))
        ) {
            return TaskType.GENERATE_PAGE_SPEC;
        }

        if (
                normalized.contains("BUSINESS_FLOW") ||
                        normalized.contains("BUSINESS FLOW") ||
                        containsAny(text, List.of("业务流程", "流程说明", "交互流程", "功能点流程", "页面交互流程", "流程总览", "流程图", "操作流程", "流转流程"))
        ) {
            return TaskType.GENERATE_BUSINESS_FLOW;
        }

        if (
                normalized.contains("MODULE_PRD") ||
                        normalized.contains("PRD") ||
                        containsAny(text, List.of("需求", "需求说明", "模块需求", "页面需求", "功能需求", "功能点需求", "场景需求", "业务域概览", "需求文档", "需求资产", "需求说明书"))
        ) {
            return TaskType.GENERATE_MODULE_PRD;
        }

        TaskType selectedTaskType = resolveTaskTypeFromSelectedArtifactType(selectedArtifactType);

        if (selectedTaskType != null && isGenericGenerateConfirmation(text)) {
            return selectedTaskType;
        }

        throw new IllegalArgumentException(
                "我识别到你想生成需求资产，但没有明确资产类型。你可以直接说：生成功能点需求、生成功能点接口、生成当前页面交互原型、生成测试用例、生成开发交接包，或生成当前节点核心资产。"
        );
    }

    private TaskType resolveTaskTypeFromClassification(
            AssistantIntentClassificationResult classification
    ) {
        if (classification == null) {
            return null;
        }

        if (classification.getTaskType() != null && !classification.getTaskType().isBlank()) {
            try {
                return TaskType.valueOf(
                        classification.getTaskType().trim().toUpperCase(Locale.ROOT)
                );
            } catch (Exception ignored) {
                // Fall through and try artifact type.
            }
        }

        return resolveTaskTypeFromSelectedArtifactType(
                classification.getArtifactType()
        );
    }

    private boolean isGenericGenerateConfirmation(
            String text
    ) {
        return containsAny(
                text,
                List.of(
                        "开始生成",
                        "开始",
                        "生成",
                        "确认生成",
                        "执行生成",
                        "执行",
                        "可以",
                        "好的",
                        "确定",
                        "就这个",
                        "继续",
                        "做一下",
                        "出一下",
                        "搞一下",
                        "弄一下"
                )
        );
    }

    private TaskType resolveTaskTypeFromSelectedArtifactType(
            String selectedArtifactType
    ) {
        if (selectedArtifactType == null || selectedArtifactType.isBlank()) {
            return null;
        }

        String value = selectedArtifactType.trim().toUpperCase(Locale.ROOT);

        return switch (value) {
            case "MODULE_PRD" -> TaskType.GENERATE_MODULE_PRD;
            case "BUSINESS_FLOW" -> TaskType.GENERATE_BUSINESS_FLOW;
            case "PAGE_SPEC" -> TaskType.GENERATE_PAGE_SPEC;
            case "DATA_MODEL" -> TaskType.GENERATE_DATA_MODEL;
            case "API_CONTRACT" -> TaskType.GENERATE_API_CONTRACT;
            case "TEST_SPEC" -> TaskType.GENERATE_TEST_SPEC;
            case "UI_CONTRACT" -> TaskType.GENERATE_UI_CONTRACT;
            case "DEV_HANDOFF_PACKAGE" -> TaskType.GENERATE_DEV_HANDOFF_PACKAGE;
            default -> null;
        };
    }

    private String resolveArtifactType(
            TaskType taskType
    ) {
        return switch (taskType) {
            case GENERATE_MODULE_PRD -> "MODULE_PRD";
            case GENERATE_BUSINESS_FLOW -> "BUSINESS_FLOW";
            case GENERATE_PAGE_SPEC -> "PAGE_SPEC";
            case GENERATE_DATA_MODEL -> "DATA_MODEL";
            case GENERATE_API_CONTRACT -> "API_CONTRACT";
            case GENERATE_TEST_SPEC -> "TEST_SPEC";
            case GENERATE_UI_CONTRACT -> "UI_CONTRACT";
            case GENERATE_DEV_HANDOFF_PACKAGE -> "DEV_HANDOFF_PACKAGE";
            default -> throw new IllegalArgumentException("不支持的需求资产任务类型：" + taskType);
        };
    }

    private String resolveTitle(
            TaskType taskType
    ) {
        return switch (taskType) {
            case GENERATE_MODULE_PRD -> "需求说明";
            case GENERATE_BUSINESS_FLOW -> "流程说明";
            case GENERATE_PAGE_SPEC -> "页面/结构说明";
            case GENERATE_DATA_MODEL -> "数据模型";
            case GENERATE_API_CONTRACT -> "接口契约";
            case GENERATE_TEST_SPEC -> "测试规格";
            case GENERATE_UI_CONTRACT -> "交互原型";
            case GENERATE_DEV_HANDOFF_PACKAGE -> "开发说明";
            default -> taskType.name();
        };
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
}