package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UiContractGenerationReadinessService {

    private static final String ACTIVE = "ACTIVE";

    private static final Set<String> NON_INTERACTIVE_PAGE_KINDS = Set.of(
            "STATIC_PAGE",
            "READ_ONLY_PAGE",
            "SYSTEM_STATE_PAGE"
    );

    private static final Set<String> UNUSABLE_ARTIFACT_STATUSES = Set.of(
            "REJECTED",
            "ARCHIVED",
            "SUPERSEDED"
    );

    private final ProjectModuleRepository projectModuleRepository;
    private final ArtifactVersionRepository artifactVersionRepository;

    public UiContractGenerationReadinessService(
            ProjectModuleRepository projectModuleRepository,
            ArtifactVersionRepository artifactVersionRepository
    ) {
        this.projectModuleRepository = projectModuleRepository;
        this.artifactVersionRepository = artifactVersionRepository;
    }

    public UiContractGenerationReadinessResult evaluate(
            UUID projectId,
            ProjectModuleEntity page
    ) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required.");
        }

        if (page == null) {
            throw new IllegalArgumentException("page is required.");
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!"PAGE".equals(normalizeNodeType(page.getModuleType()))) {
            blockers.add("交互原型只能基于 PAGE 页面节点生成。");
        }

        String pageKind = resolvePageKind(page);

        List<ProjectModuleEntity> activeModules = projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                .stream()
                .filter(item -> ACTIVE.equalsIgnoreCase(item.getStatus()))
                .toList();

        Map<UUID, ProjectModuleEntity> activeById = new LinkedHashMap<>();
        for (ProjectModuleEntity item : activeModules) {
            activeById.put(item.getId(), item);
        }

        ArtifactVersionEntity pageSpec = currentArtifact(
                projectId,
                page.getId(),
                "PAGE_SPEC"
        );

        boolean pageSpecAvailable = isUsable(pageSpec);

        Map<String, Object> pageExtra = page.getExtra() == null
                ? Map.of()
                : new LinkedHashMap<>(page.getExtra());

        boolean hiddenPagePrototypeContextAvailable = hasNestedMap(
                pageExtra,
                "pagePrototypeContext",
                "page_prototype_context",
                "prototypeContext",
                "prototype_context"
        );

        if (!pageSpecAvailable && !hiddenPagePrototypeContextAvailable) {
            warnings.add("当前 PAGE 没有显式页面规范；系统将依据模块需求、父功能点和页面名称自动补齐原型上下文。");
        } else if (!pageSpecAvailable) {
            warnings.add("当前 PAGE 将使用隐藏原型上下文生成交互原型，不向用户暴露 PAGE_SPEC。");
        }

        List<ProjectModuleEntity> features = collectFeatureNodesForPage(
                page,
                activeModules,
                activeById
        );

        if (features.isEmpty() && !NON_INTERACTIVE_PAGE_KINDS.contains(pageKind)) {
            warnings.add(
                    "当前 PAGE 未找到父功能点；系统将依据页面名称和隐藏原型上下文生成基础交互。"
            );
        }

        List<UiContractGenerationReadinessResult.FeatureReadinessItem> featureItems =
                new ArrayList<>();

        int completeFeatureCount = 0;

        for (ProjectModuleEntity feature : features) {
            FeatureDefinitionCheck check = checkFeatureDefinition(
                    projectId,
                    feature,
                    pageSpec
            );

            if (check.complete()) {
                completeFeatureCount++;
            } else {
                blockers.add(
                        "功能点“"
                                + feature.getModuleName()
                                + "”缺少基础交互定义："
                                + String.join("、", check.missingFields())
                );
            }

            featureItems.add(
                    new UiContractGenerationReadinessResult.FeatureReadinessItem(
                            feature.getId(),
                            feature.getModuleKey(),
                            feature.getModuleName(),
                            check.complete(),
                            check.definitionSource(),
                            check.missingFields()
                    )
            );
        }

        if (!hasUsableArtifact(projectId, page.getId(), "BUSINESS_FLOW")) {
            warnings.add("当前 PAGE 缺少 BUSINESS_FLOW，复杂分支和异常流程可能需要后续补充。");
        }

        if (!hasUsableArtifact(projectId, page.getId(), "DATA_MODEL")) {
            warnings.add("当前 PAGE 缺少 DATA_MODEL，字段类型和数据结构将主要依据隐藏原型上下文与父功能点定义。");
        }

        if (!hasUsableArtifact(projectId, page.getId(), "API_CONTRACT")) {
            warnings.add("当前 PAGE 缺少 API_CONTRACT，本次原型将使用 MOCK/PLANNED 绑定。");
        }

        if (!hasUsableArtifact(projectId, page.getId(), "TEST_SPEC")) {
            warnings.add("当前 PAGE 缺少 TEST_SPEC，边界状态将在原型生成后继续补强。");
        }

        return new UiContractGenerationReadinessResult(
                blockers.isEmpty(),
                page.getId(),
                page.getModuleKey(),
                page.getModuleName(),
                pageKind,
                pageSpecAvailable,
                features.size(),
                completeFeatureCount,
                List.copyOf(featureItems),
                List.copyOf(blockers),
                List.copyOf(warnings)
        );
    }

    private FeatureDefinitionCheck checkFeatureDefinition(
            UUID projectId,
            ProjectModuleEntity feature,
            ArtifactVersionEntity pageSpec
    ) {
        /*
         * Phase 10-A rule:
         * FEATURE is an inline detail layer. It should not be forced to generate
         * an independent PRD just to satisfy UI_CONTRACT readiness.
         *
         * Readiness source priority:
         * 1. FEATURE node extra, when the module tree already carries interaction metadata.
         * 2. Current PAGE_SPEC action_specs, because PAGE_SPEC is the canonical
         *    carrier of page-level behavior and feature interaction details.
         * 3. Legacy FEATURE MODULE_PRD, kept only for backward compatibility.
         */
        Map<String, Object> extra = feature.getExtra() == null
                ? Map.of()
                : new LinkedHashMap<>(feature.getExtra());

        Map<String, Object> functionContext = readNestedMap(
                extra,
                "functionContext",
                "function_context"
        );

        FeatureDefinitionCandidate pageSpecCandidate =
                extractFeatureDefinitionFromPageSpec(
                        pageSpec,
                        feature
                );

        FeatureDefinitionCandidate inferredCandidate =
                inferFeatureDefinitionFromFeatureNode(
                        feature
                );

        Object description = firstNonBlank(
                readFirst(extra, "description", "featureDescription", "purpose"),
                readFirst(functionContext, "summary", "goal", "description", "requirementSummary"),
                pageSpecCandidate.description(),
                inferredCandidate.description()
        );

        Object trigger = firstNonBlank(
                readFirst(extra, "trigger", "triggerCondition", "userTrigger"),
                readFirst(functionContext, "trigger", "triggerCondition", "userTrigger"),
                pageSpecCandidate.trigger(),
                inferredCandidate.trigger()
        );

        Object interactionType = firstNonBlank(
                readFirst(extra, "interactionType", "interaction_type", "actionType"),
                readFirst(functionContext, "interactionType", "interaction_type", "actionType"),
                pageSpecCandidate.interactionType(),
                inferredCandidate.interactionType()
        );

        Object expectedResult = firstNonBlank(
                readFirst(extra, "expectedResult", "expected_result", "result"),
                readFirst(functionContext, "expectedResult", "expected_result", "result"),
                pageSpecCandidate.expectedResult(),
                inferredCandidate.expectedResult()
        );

        List<String> missing = new ArrayList<>();

        if (!hasText(description)) {
            missing.add("功能目的/说明");
        }

        if (!hasText(trigger)) {
            missing.add("触发方式");
        }

        if (!hasText(interactionType)) {
            missing.add("交互类型");
        }

        if (!hasText(expectedResult)) {
            missing.add("预期结果");
        }

        if (missing.isEmpty()) {
            return new FeatureDefinitionCheck(
                    true,
                    pageSpecCandidate.matched()
                            ? "PAGE_SPEC_ACTION"
                            : inferredCandidate.matched()
                            ? "FEATURE_NAME_INFERRED"
                            : "MODULE_TREE_EXTRA",
                    List.of()
            );
        }

        ArtifactVersionEntity featureRequirement = currentArtifact(
                projectId,
                feature.getId(),
                "MODULE_PRD"
        );

        if (isUsable(featureRequirement)) {
            return new FeatureDefinitionCheck(
                    true,
                    "LEGACY_FEATURE_MODULE_PRD",
                    List.of()
            );
        }

        return new FeatureDefinitionCheck(
                false,
                pageSpecCandidate.matched()
                        ? "PAGE_SPEC_ACTION_INCOMPLETE"
                        : "INCOMPLETE",
                List.copyOf(missing)
        );
    }

    private FeatureDefinitionCandidate inferFeatureDefinitionFromFeatureNode(
            ProjectModuleEntity feature
    ) {
        String featureName = feature.getModuleName() == null
                ? ""
                : feature.getModuleName().trim();

        String featureKey = feature.getModuleKey() == null
                ? ""
                : feature.getModuleKey().trim();

        String extraText = feature.getExtra() == null
                ? ""
                : String.valueOf(feature.getExtra());

        String text = normalizeMatchText(
                featureName
                        + " "
                        + featureKey
                        + " "
                        + extraText
        );

        if (!hasText(text)) {
            return new FeatureDefinitionCandidate("", "", "", "", false);
        }

        String description = "覆盖页面功能点：" + (hasText(featureName) ? featureName : featureKey) + "。";

        if (containsAny(
                text,
                List.of(
                        "错误",
                        "失败",
                        "异常",
                        "提示",
                        "error",
                        "fail",
                        "failure",
                        "invalid"
                )
        )) {
            return new FeatureDefinitionCandidate(
                    description,
                    "用户提交表单、字段校验失败或接口返回失败结果时触发。",
                    "ERROR_FEEDBACK",
                    "页面展示清晰错误提示，保留用户已输入内容，并允许用户修改后重新提交。",
                    true
            );
        }

        if (containsAny(
                text,
                List.of(
                        "成功",
                        "跳转",
                        "进入",
                        "完成",
                        "success",
                        "navigate",
                        "redirect",
                        "route"
                )
        )) {
            return new FeatureDefinitionCandidate(
                    description,
                    "用户完成关键操作且接口返回成功结果时触发。",
                    "SUCCESS_NAVIGATION",
                    "页面展示成功反馈，并按产品流程跳转到目标页面或进入成功态。",
                    true
            );
        }

        if (containsAny(
                text,
                List.of(
                        "校验",
                        "验证",
                        "检查",
                        "validate",
                        "validation",
                        "check"
                )
        )) {
            return new FeatureDefinitionCandidate(
                    description,
                    "用户输入字段、字段失焦或提交表单前触发。",
                    "FIELD_VALIDATION",
                    "系统完成字段合法性检查，错误字段给出明确提示，合法字段允许继续操作。",
                    true
            );
        }

        if (containsAny(
                text,
                List.of(
                        "查询",
                        "搜索",
                        "筛选",
                        "刷新",
                        "列表",
                        "query",
                        "search",
                        "filter",
                        "list",
                        "refresh"
                )
        )) {
            return new FeatureDefinitionCandidate(
                    description,
                    "用户输入查询条件、点击查询/刷新或进入页面加载数据时触发。",
                    "QUERY_REFRESH",
                    "页面刷新数据区域，展示查询结果、空状态或错误状态。",
                    true
            );
        }

        if (containsAny(
                text,
                List.of(
                        "弹窗",
                        "打开",
                        "详情",
                        "modal",
                        "dialog",
                        "open",
                        "detail"
                )
        )) {
            return new FeatureDefinitionCandidate(
                    description,
                    "用户点击相关按钮、链接或操作入口时触发。",
                    "OPEN_MODAL",
                    "页面打开对应弹窗或局部操作区，用户可以继续确认或取消。",
                    true
            );
        }

        if (containsAny(
                text,
                List.of(
                        "表单",
                        "提交",
                        "注册",
                        "登录",
                        "保存",
                        "认证",
                        "submit",
                        "form",
                        "register",
                        "login",
                        "save",
                        "auth"
                )
        )) {
            return new FeatureDefinitionCandidate(
                    description,
                    "用户在页面表单或主操作区点击提交/确认按钮时触发。",
                    "FORM_SUBMIT",
                    "页面完成对应业务动作，进入提交中、成功或失败反馈状态。",
                    true
            );
        }

        return new FeatureDefinitionCandidate(
                description,
                "用户在当前页面执行该功能点相关操作时触发。",
                "PAGE_INTERACTION",
                "页面完成该功能点对应的交互反馈、状态变化或数据更新。",
                true
        );
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return keywords
                .stream()
                .filter(this::hasText)
                .anyMatch(keyword -> text.contains(normalizeMatchText(keyword)));
    }


    private FeatureDefinitionCandidate extractFeatureDefinitionFromPageSpec(
            ArtifactVersionEntity pageSpec,
            ProjectModuleEntity feature
    ) {
        if (!isUsable(pageSpec)) {
            return new FeatureDefinitionCandidate("", "", "", "", false);
        }

        Object rawContent = pageSpec.getContent();

        if (!(rawContent instanceof Map<?, ?> content)) {
            return new FeatureDefinitionCandidate("", "", "", "", false);
        }

        List<?> actionSpecs = asList(
                readFirst(
                        content,
                        "action_specs",
                        "actionSpecs",
                        "actions",
                        "interactions"
                )
        );

        if (actionSpecs.isEmpty()) {
            return new FeatureDefinitionCandidate("", "", "", "", false);
        }

        Map<?, ?> matchedAction = findMatchingPageAction(
                actionSpecs,
                feature
        );

        if (matchedAction == null) {
            return new FeatureDefinitionCandidate("", "", "", "", false);
        }

        String description = firstNonBlankString(
                readFirst(matchedAction, "description", "summary"),
                readFirst(matchedAction, "action_name", "actionName", "name", "title")
        );

        String trigger = firstNonBlankString(
                readFirst(matchedAction, "trigger_position", "triggerPosition"),
                readFirst(matchedAction, "trigger", "triggerCondition", "userTrigger")
        );

        String interactionType = firstNonBlankString(
                readFirst(matchedAction, "interaction_type", "interactionType"),
                readFirst(matchedAction, "action_type", "actionType", "type"),
                inferInteractionType(matchedAction)
        );

        String expectedResult = firstNonBlankString(
                readFirst(matchedAction, "expected_result", "expectedResult", "result"),
                readFirst(matchedAction, "success_feedback", "successFeedback"),
                readFirst(matchedAction, "failure_feedback", "failureFeedback")
        );

        return new FeatureDefinitionCandidate(
                description,
                trigger,
                interactionType,
                expectedResult,
                true
        );
    }

    private Map<?, ?> findMatchingPageAction(
            List<?> actionSpecs,
            ProjectModuleEntity feature
    ) {
        Map<?, ?> best = null;
        int bestScore = 0;

        for (Object item : actionSpecs) {
            if (!(item instanceof Map<?, ?> action)) {
                continue;
            }

            int score = scoreActionAgainstFeature(
                    action,
                    feature
            );

            if (score > bestScore) {
                best = action;
                bestScore = score;
            }
        }

        return bestScore >= 40 ? best : null;
    }

    private int scoreActionAgainstFeature(
            Map<?, ?> action,
            ProjectModuleEntity feature
    ) {
        String featureKey = normalizeMatchText(feature.getModuleKey());
        String featureName = normalizeMatchText(feature.getModuleName());

        String actionKey = normalizeMatchText(
                readFirst(
                        action,
                        "action_key",
                        "actionKey",
                        "feature_key",
                        "featureKey",
                        "key"
                )
        );

        String actionName = normalizeMatchText(
                readFirst(
                        action,
                        "action_name",
                        "actionName",
                        "name",
                        "title"
                )
        );

        String actionText = normalizeMatchText(
                String.join(
                        " ",
                        valueText(readFirst(action, "description", "summary")),
                        valueText(readFirst(action, "trigger_position", "triggerPosition", "trigger")),
                        valueText(readFirst(action, "interaction_type", "interactionType", "type")),
                        valueText(readFirst(action, "expected_result", "expectedResult", "result")),
                        valueText(readFirst(action, "success_feedback", "successFeedback")),
                        valueText(readFirst(action, "failure_feedback", "failureFeedback"))
                )
        );

        int score = 0;

        if (hasText(featureKey) && hasText(actionKey) && featureKey.equals(actionKey)) {
            score += 120;
        }

        if (hasText(featureKey) && hasText(actionKey)
                && (actionKey.contains(featureKey) || featureKey.contains(actionKey))) {
            score += 70;
        }

        if (hasText(featureName) && hasText(actionName) && actionName.contains(featureName)) {
            score += 80;
        }

        if (hasText(featureName) && hasText(actionText) && actionText.contains(featureName)) {
            score += 50;
        }

        if (hasText(actionName) && hasText(featureName) && featureName.contains(actionName)) {
            score += 45;
        }

        return score;
    }

    private String inferInteractionType(
            Map<?, ?> action
    ) {
        String text = normalizeMatchText(
                String.join(
                        " ",
                        valueText(readFirst(action, "action_key", "actionKey")),
                        valueText(readFirst(action, "action_name", "actionName", "name", "title")),
                        valueText(readFirst(action, "description")),
                        valueText(readFirst(action, "success_feedback", "successFeedback")),
                        valueText(readFirst(action, "failure_feedback", "failureFeedback"))
                )
        );

        if (text.contains("跳转") || text.contains("navigate") || text.contains("redirect")) {
            return "SUCCESS_NAVIGATION";
        }

        if (text.contains("错误") || text.contains("失败") || text.contains("提示") || text.contains("error")) {
            return "ERROR_FEEDBACK";
        }

        if (text.contains("提交") || text.contains("注册") || text.contains("登录") || text.contains("submit")) {
            return "FORM_SUBMIT";
        }

        if (text.contains("校验") || text.contains("验证") || text.contains("validate")) {
            return "FIELD_VALIDATION";
        }

        if (text.contains("查询") || text.contains("搜索") || text.contains("刷新") || text.contains("search")) {
            return "QUERY_REFRESH";
        }

        return "";
    }


    private List<ProjectModuleEntity> collectFeatureNodesForPage(
            ProjectModuleEntity page,
            List<ProjectModuleEntity> activeModules,
            Map<UUID, ProjectModuleEntity> byId
    ) {
        List<ProjectModuleEntity> result = new ArrayList<>();

        ProjectModuleEntity parent = byId.get(page.getParentId());
        if (parent != null
                && "FEATURE".equals(normalizeNodeType(parent.getModuleType()))) {
            result.add(parent);
        }

        for (ProjectModuleEntity candidate : activeModules) {
            if (!page.getId().equals(candidate.getParentId())) {
                continue;
            }

            if (!"FEATURE".equals(normalizeNodeType(candidate.getModuleType()))) {
                continue;
            }

            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }

        return result;
    }

    private boolean hasNestedMap(
            Map<String, Object> source,
            String... keys
    ) {
        return !readNestedMap(source, keys).isEmpty();
    }

    private Map<String, Object> readNestedMap(
            Map<String, Object> source,
            String... keys
    ) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
        }

        return Map.of();
    }

    private List<?> asList(
            Object value
    ) {
        if (value instanceof List<?> list) {
            return list;
        }

        return List.of();
    }

    private Object firstNonBlank(
            Object... values
    ) {
        for (Object value : values) {
            if (hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private String firstNonBlankString(
            Object... values
    ) {
        Object value = firstNonBlank(values);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String valueText(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeMatchText(
            Object value
    ) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]+", "");
    }

    private boolean hasUsableArtifact(
            UUID projectId,
            UUID moduleId,
            String artifactType
    ) {
        return isUsable(currentArtifact(projectId, moduleId, artifactType));
    }

    private ArtifactVersionEntity currentArtifact(
            UUID projectId,
            UUID moduleId,
            String artifactType
    ) {
        return artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        artifactType
                )
                .orElse(null);
    }

    private boolean isUsable(
            ArtifactVersionEntity artifact
    ) {
        if (artifact == null || !Boolean.TRUE.equals(artifact.getCurrent())) {
            return false;
        }

        String status = artifact.getStatus() == null
                ? ""
                : artifact.getStatus().trim().toUpperCase(Locale.ROOT);

        return !UNUSABLE_ARTIFACT_STATUSES.contains(status);
    }

    private String resolvePageKind(
            ProjectModuleEntity page
    ) {
        Object value = page.getExtra() == null
                ? null
                : readFirst(page.getExtra(), "pageKind", "page_kind");

        if (value == null || String.valueOf(value).isBlank()) {
            return "FORM_PAGE";
        }

        return String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNodeType(
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> "FEATURE";
            default -> raw.trim().toUpperCase(Locale.ROOT);
        };
    }

    private Object readFirst(
            Map<?, ?> source,
            String... keys
    ) {
        for (String key : keys) {
            Object value = source.get(key);

            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }

        return null;
    }

    private boolean hasText(
            Object value
    ) {
        return value != null && !String.valueOf(value).trim().isBlank();
    }

    private record FeatureDefinitionCandidate(
            String description,
            String trigger,
            String interactionType,
            String expectedResult,
            boolean matched
    ) {
    }

    private record FeatureDefinitionCheck(
            boolean complete,
            String definitionSource,
            List<String> missingFields
    ) {
    }
}
