package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UiContractValidationService {

    private static final String ACTIVE = "ACTIVE";

    private static final Set<String> COMPONENT_TYPES = Set.of(
            "PAGE_HEADER",
            "SECTION",
            "CARD",
            "FORM",
            "FIELD_GROUP",
            "TABLE",
            "TOOLBAR",
            "BUTTON",
            "LINK",
            "TABS",
            "MODAL",
            "ALERT",
            "DESCRIPTION_LIST",
            "STEPS",
            "DIVIDER",
            "TEXT",
            "EMPTY_STATE"
    );

    private static final Set<String> FIELD_TYPES = Set.of(
            "INPUT",
            "PASSWORD",
            "TEXTAREA",
            "SELECT",
            "CHECKBOX",
            "RADIO",
            "NUMBER",
            "EMAIL",
            "DATE",
            "DATETIME",
            "SWITCH",
            "HIDDEN"
    );

    private static final Set<String> ACTION_TYPES = Set.of(
            "SET_VALUE",
            "RESET_FORM",
            "SUBMIT_FORM",
            "OPEN_MODAL",
            "CLOSE_MODAL",
            "SWITCH_TAB",
            "NAVIGATE",
            "SHOW_TOAST",
            "OPEN_CONFIRM",
            "CONFIRM_ACTION",
            "CANCEL_ACTION",
            "SET_PAGE_STATE",
            "QUERY_DATA",
            "DELETE_ROW"
    );

    private static final Set<String> PAGE_STATE_TYPES = Set.of(
            "DEFAULT",
            "LOADING",
            "EMPTY",
            "ERROR",
            "SUCCESS",
            "DISABLED"
    );

    private static final Set<String> COVERAGE_STATUSES = Set.of(
            "FULL",
            "PARTIAL",
            "MISSING",
            "NOT_APPLICABLE"
    );

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "script",
            "javascript",
            "eval",
            "onclick",
            "onload",
            "onerror",
            "innerhtml",
            "dangerouslysetinnerhtml",
            "executablecode"
    );

    private final ProjectModuleRepository projectModuleRepository;

    public UiContractValidationService(
            ProjectModuleRepository projectModuleRepository
    ) {
        this.projectModuleRepository = projectModuleRepository;
    }

    public UiContractValidationResult validate(
            UUID projectId,
            ProjectModuleEntity page,
            Map<String, Object> content
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return new UiContractValidationResult(
                    false,
                    List.of("UI_CONTRACT content 不能为空。"),
                    List.of(),
                    Map.of()
            );
        }

        rejectExecutableContent(content, "$", errors);

        requireExactString(content, "schemaVersion", "1.0", errors);
        requireExactString(content, "artifactType", "UI_CONTRACT", errors);

        if (content.containsKey("pages")
                || content.containsKey("routes")
                || content.containsKey("module")) {
            errors.add(
                    "UI_CONTRACT 仍包含已废弃的 pages/routes/module 结构。"
            );
        }

        Map<String, Object> identity = asMap(content.get("identity"));
        Map<String, Object> pageContent = asMap(content.get("page"));

        if (identity.isEmpty()) {
            errors.add("identity 必须是非空对象。");
        }

        if (pageContent.isEmpty()) {
            errors.add("page 必须是非空对象。");
        }

        validateIdentity(
                projectId,
                page,
                identity,
                pageContent,
                errors
        );

        Map<String, Object> layout = asMap(pageContent.get("layout"));

        if (layout.isEmpty()) {
            errors.add("page.layout 必须是非空对象。");
        } else {
            requireString(layout, "type", "page.layout", errors);
        }

        List<Map<String, Object>> componentTree =
                asMapList(pageContent.get("componentTree"));

        if (componentTree.isEmpty()) {
            errors.add("page.componentTree 至少包含一个组件。");
        }

        ComponentIndex componentIndex = new ComponentIndex();

        for (Map<String, Object> component : componentTree) {
            collectComponent(
                    component,
                    componentIndex,
                    errors
            );
        }

        List<Map<String, Object>> interactions =
                asMapList(content.get("interactions"));

        ActionIndex actionIndex = new ActionIndex();

        for (Map<String, Object> interaction : interactions) {
            collectInteraction(
                    interaction,
                    componentIndex,
                    actionIndex,
                    errors
            );
        }

        validateComponentActionReferences(
                componentIndex,
                actionIndex,
                errors
        );

        validateActionGraph(
                actionIndex,
                errors
        );

        validateValidationRules(
                content,
                componentIndex,
                errors
        );

        PageStateIndex pageStateIndex = validatePageStates(
                content,
                componentIndex,
                errors
        );

        validateMockScenarios(
                content,
                actionIndex,
                pageStateIndex,
                errors
        );

        NavigationIndex navigationIndex = validateNavigationTargets(
                projectId,
                page.getId(),
                content,
                errors
        );

        validateActionTargets(
                actionIndex,
                componentIndex,
                pageStateIndex,
                navigationIndex,
                errors,
                warnings
        );

        Set<String> expectedFeatureIds =
                currentFeatureIds(projectId, page.getId());

        CoverageMetrics coverageMetrics = validateFeatureCoverage(
                content,
                expectedFeatureIds,
                componentIndex,
                actionIndex,
                errors,
                warnings
        );

        validateApiBindings(
                content,
                actionIndex,
                errors
        );

        validateQualitySummary(
                content,
                coverageMetrics,
                errors,
                warnings
        );

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("componentCount", componentIndex.componentIds.size());
        metrics.put("fieldCount", componentIndex.fieldKeys.size());
        metrics.put("actionCount", actionIndex.actionIds.size());
        metrics.put("pageStateCount", pageStateIndex.stateKeys.size());
        metrics.put("expectedFeatureCount", expectedFeatureIds.size());
        metrics.put("coveredFeatureCount", coverageMetrics.coveredCount());
        metrics.put("fullFeatureCount", coverageMetrics.fullCount());
        metrics.put("coverageRate", coverageMetrics.coverageRate());

        return new UiContractValidationResult(
                errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings),
                Map.copyOf(metrics)
        );
    }

    private void validateIdentity(
            UUID projectId,
            ProjectModuleEntity page,
            Map<String, Object> identity,
            Map<String, Object> pageContent,
            List<String> errors
    ) {
        String expectedProjectId = projectId.toString();
        String expectedPageId = page.getId().toString();
        String expectedPageKey = page.getModuleKey();
        String expectedPageName = page.getModuleName();

        requireString(identity, "projectId", "identity", errors);
        requireString(identity, "pageId", "identity", errors);
        requireString(identity, "pageKey", "identity", errors);
        requireString(identity, "pageName", "identity", errors);
        requireString(identity, "pageKind", "identity", errors);

        requireString(pageContent, "pageId", "page", errors);
        requireString(pageContent, "pageKey", "page", errors);
        requireString(pageContent, "pageName", "page", errors);
        requireString(pageContent, "route", "page", errors);
        requireString(pageContent, "title", "page", errors);
        requireString(pageContent, "pageKind", "page", errors);

        compareExact(
                "identity.projectId",
                expectedProjectId,
                stringValue(identity.get("projectId")),
                errors
        );

        compareExact(
                "identity.pageId",
                expectedPageId,
                stringValue(identity.get("pageId")),
                errors
        );

        compareExact(
                "page.pageId",
                expectedPageId,
                stringValue(pageContent.get("pageId")),
                errors
        );

        compareExact(
                "identity.pageKey",
                expectedPageKey,
                stringValue(identity.get("pageKey")),
                errors
        );

        compareExact(
                "page.pageKey",
                expectedPageKey,
                stringValue(pageContent.get("pageKey")),
                errors
        );

        compareExact(
                "identity.pageName",
                expectedPageName,
                stringValue(identity.get("pageName")),
                errors
        );

        compareExact(
                "page.pageName",
                expectedPageName,
                stringValue(pageContent.get("pageName")),
                errors
        );
    }

    private void collectComponent(
            Map<String, Object> component,
            ComponentIndex index,
            List<String> errors
    ) {
        String componentId = stringValue(component.get("componentId"));
        String componentType = upper(component.get("type"));

        if (componentId.isBlank()) {
            errors.add("组件缺少 componentId。");
            return;
        }

        if (!index.componentIds.add(componentId)) {
            errors.add("componentId 重复：" + componentId);
        }

        if (!COMPONENT_TYPES.contains(componentType)) {
            errors.add(
                    "组件 "
                            + componentId
                            + " 使用了不支持的 type："
                            + componentType
            );
        }

        index.componentTypes.put(componentId, componentType);

        for (String actionId : asStringList(component.get("actionIds"))) {
            index.actionReferences.add(actionId);
        }

        for (String featureId : asStringList(component.get("featureIds"))) {
            index.featureReferences.add(featureId);
        }

        for (Map<String, Object> field : asMapList(component.get("fields"))) {
            String fieldKey = stringValue(field.get("fieldKey"));
            String fieldType = upper(field.get("type"));

            if (fieldKey.isBlank()) {
                errors.add(
                        "组件 "
                                + componentId
                                + " 存在缺少 fieldKey 的字段。"
                );
            } else if (!index.fieldKeys.add(fieldKey)) {
                errors.add("fieldKey 重复：" + fieldKey);
            }

            if (!FIELD_TYPES.contains(fieldType)) {
                errors.add(
                        "字段 "
                                + fieldKey
                                + " 使用了不支持的 type："
                                + fieldType
                );
            }

            List<String> ruleIds =
                    asStringList(field.get("validationRuleIds"));

            index.fieldRuleReferences.put(fieldKey, ruleIds);

            for (String featureId : asStringList(field.get("featureIds"))) {
                index.featureReferences.add(featureId);
            }
        }

        Set<String> columnKeys = new LinkedHashSet<>();

        for (Map<String, Object> column : asMapList(component.get("columns"))) {
            String columnKey = stringValue(column.get("columnKey"));

            if (columnKey.isBlank()) {
                errors.add(
                        "组件 "
                                + componentId
                                + " 存在缺少 columnKey 的表格列。"
                );
            } else if (!columnKeys.add(columnKey)) {
                errors.add(
                        "组件 "
                                + componentId
                                + " 的 columnKey 重复："
                                + columnKey
                );
            }
        }

        for (Map<String, Object> child : asMapList(component.get("children"))) {
            collectComponent(
                    child,
                    index,
                    errors
            );
        }
    }

    private void collectInteraction(
            Map<String, Object> interaction,
            ComponentIndex componentIndex,
            ActionIndex actionIndex,
            List<String> errors
    ) {
        String actionId = stringValue(interaction.get("actionId"));
        String actionType = upper(interaction.get("type"));

        if (actionId.isBlank()) {
            errors.add("interaction 缺少 actionId。");
            return;
        }

        if (!actionIndex.actionIds.add(actionId)) {
            errors.add("actionId 重复：" + actionId);
        }

        if (!ACTION_TYPES.contains(actionType)) {
            errors.add(
                    "动作 "
                            + actionId
                            + " 使用了不支持的 type："
                            + actionType
            );
        }

        String sourceComponentId =
                stringValue(interaction.get("sourceComponentId"));

        if (!sourceComponentId.isBlank()
                && !componentIndex.componentIds.contains(sourceComponentId)) {
            errors.add(
                    "动作 "
                            + actionId
                            + " 的 sourceComponentId 不存在："
                            + sourceComponentId
            );
        }

        actionIndex.actionsById.put(actionId, interaction);

        for (String featureId : asStringList(interaction.get("featureIds"))) {
            actionIndex.featureReferences.add(featureId);
        }
    }

    private void validateComponentActionReferences(
            ComponentIndex componentIndex,
            ActionIndex actionIndex,
            List<String> errors
    ) {
        for (String actionId : componentIndex.actionReferences) {
            if (!actionIndex.actionIds.contains(actionId)) {
                errors.add(
                        "组件引用了不存在的 actionId："
                                + actionId
                );
            }
        }
    }

    private void validateActionGraph(
            ActionIndex actionIndex,
            List<String> errors
    ) {
        for (Map.Entry<String, Map<String, Object>> entry
                : actionIndex.actionsById.entrySet()) {
            String actionId = entry.getKey();
            Map<String, Object> action = entry.getValue();

            for (String ref : asStringList(action.get("successActionIds"))) {
                if (!actionIndex.actionIds.contains(ref)) {
                    errors.add(
                            "动作 "
                                    + actionId
                                    + " 引用了不存在的 successActionId："
                                    + ref
                    );
                }
            }

            for (String ref : asStringList(action.get("failureActionIds"))) {
                if (!actionIndex.actionIds.contains(ref)) {
                    errors.add(
                            "动作 "
                                    + actionId
                                    + " 引用了不存在的 failureActionId："
                                    + ref
                    );
                }
            }
        }
    }

    private void validateValidationRules(
            Map<String, Object> content,
            ComponentIndex componentIndex,
            List<String> errors
    ) {
        Set<String> ruleIds = new LinkedHashSet<>();

        for (Map<String, Object> rule
                : asMapList(content.get("validationRules"))) {
            String ruleId = stringValue(rule.get("ruleId"));
            String fieldKey = stringValue(rule.get("fieldKey"));

            if (ruleId.isBlank()) {
                errors.add("validationRule 缺少 ruleId。");
            } else if (!ruleIds.add(ruleId)) {
                errors.add("ruleId 重复：" + ruleId);
            }

            if (!componentIndex.fieldKeys.contains(fieldKey)) {
                errors.add(
                        "校验规则 "
                                + ruleId
                                + " 引用了不存在的 fieldKey："
                                + fieldKey
                );
            }

            String compareFieldKey =
                    stringValue(rule.get("compareFieldKey"));

            if (!compareFieldKey.isBlank()
                    && !componentIndex.fieldKeys.contains(compareFieldKey)) {
                errors.add(
                        "校验规则 "
                                + ruleId
                                + " 引用了不存在的 compareFieldKey："
                                + compareFieldKey
                );
            }
        }

        for (Map.Entry<String, List<String>> entry
                : componentIndex.fieldRuleReferences.entrySet()) {
            for (String ruleId : entry.getValue()) {
                if (!ruleIds.contains(ruleId)) {
                    errors.add(
                            "字段 "
                                    + entry.getKey()
                                    + " 引用了不存在的 validationRuleId："
                                    + ruleId
                    );
                }
            }
        }
    }

    private PageStateIndex validatePageStates(
            Map<String, Object> content,
            ComponentIndex componentIndex,
            List<String> errors
    ) {
        PageStateIndex index = new PageStateIndex();

        for (Map<String, Object> state
                : asMapList(content.get("pageStates"))) {
            String stateKey = stringValue(state.get("stateKey"));
            String stateType = upper(state.get("type"));

            if (stateKey.isBlank()) {
                errors.add("pageState 缺少 stateKey。");
            } else if (!index.stateKeys.add(stateKey)) {
                errors.add("pageState.stateKey 重复：" + stateKey);
            }

            if (!PAGE_STATE_TYPES.contains(stateType)) {
                errors.add(
                        "页面状态 "
                                + stateKey
                                + " 使用了不支持的 type："
                                + stateType
                );
            }

            index.stateTypes.add(stateType);

            for (String componentId
                    : asStringList(state.get("visibleComponentIds"))) {
                if (!componentIndex.componentIds.contains(componentId)) {
                    errors.add(
                            "页面状态 "
                                    + stateKey
                                    + " 引用了不存在的 visibleComponentId："
                                    + componentId
                    );
                }
            }

            for (String componentId
                    : asStringList(state.get("disabledComponentIds"))) {
                if (!componentIndex.componentIds.contains(componentId)) {
                    errors.add(
                            "页面状态 "
                                    + stateKey
                                    + " 引用了不存在的 disabledComponentId："
                                    + componentId
                    );
                }
            }
        }

        for (String required : List.of("DEFAULT", "LOADING", "ERROR")) {
            if (!index.stateTypes.contains(required)) {
                errors.add("pageStates 缺少必需状态：" + required);
            }
        }

        return index;
    }

    private void validateMockScenarios(
            Map<String, Object> content,
            ActionIndex actionIndex,
            PageStateIndex pageStateIndex,
            List<String> errors
    ) {
        Set<String> scenarioKeys = new LinkedHashSet<>();

        for (Map<String, Object> scenario
                : asMapList(content.get("mockScenarios"))) {
            String scenarioKey = stringValue(scenario.get("scenarioKey"));
            String actionId = stringValue(scenario.get("actionId"));
            String nextPageState =
                    stringValue(scenario.get("nextPageState"));

            if (scenarioKey.isBlank()) {
                errors.add("mockScenario 缺少 scenarioKey。");
            } else if (!scenarioKeys.add(scenarioKey)) {
                errors.add("scenarioKey 重复：" + scenarioKey);
            }

            if (!actionIndex.actionIds.contains(actionId)) {
                errors.add(
                        "Mock 场景 "
                                + scenarioKey
                                + " 引用了不存在的 actionId："
                                + actionId
                );
            }

            if (!nextPageState.isBlank()
                    && !pageStateIndex.stateKeys.contains(nextPageState)) {
                errors.add(
                        "Mock 场景 "
                                + scenarioKey
                                + " 引用了不存在的 nextPageState："
                                + nextPageState
                );
            }
        }
    }

    private NavigationIndex validateNavigationTargets(
            UUID projectId,
            UUID currentPageId,
            Map<String, Object> content,
            List<String> errors
    ) {
        NavigationIndex index = new NavigationIndex();

        Set<String> actualPageIds = projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                .stream()
                .filter(module -> ACTIVE.equalsIgnoreCase(module.getStatus()))
                .filter(module -> "PAGE".equals(
                        normalizeNodeType(module.getModuleType())
                ))
                .map(module -> module.getId().toString())
                .collect(
                        java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );

        actualPageIds.remove(currentPageId.toString());

        for (Map<String, Object> target
                : asMapList(content.get("navigationTargets"))) {
            String targetPageId =
                    stringValue(target.get("targetPageId"));

            if (targetPageId.isBlank()) {
                errors.add("navigationTarget 缺少 targetPageId。");
                continue;
            }

            if (!index.targetPageIds.add(targetPageId)) {
                errors.add(
                        "navigationTarget.targetPageId 重复："
                                + targetPageId
                );
            }

            if (!actualPageIds.contains(targetPageId)) {
                errors.add(
                        "navigationTarget 引用了不存在或非 PAGE 的目标节点："
                                + targetPageId
                );
            }

            if (stringValue(target.get("route")).isBlank()) {
                errors.add(
                        "navigationTarget "
                                + targetPageId
                                + " 缺少 route。"
                );
            }
        }

        return index;
    }

    private void validateActionTargets(
            ActionIndex actionIndex,
            ComponentIndex componentIndex,
            PageStateIndex pageStateIndex,
            NavigationIndex navigationIndex,
            List<String> errors,
            List<String> warnings
    ) {
        for (Map.Entry<String, Map<String, Object>> entry
                : actionIndex.actionsById.entrySet()) {
            String actionId = entry.getKey();
            Map<String, Object> action = entry.getValue();
            String type = upper(action.get("type"));
            String targetId = stringValue(action.get("targetId"));

            if (Set.of(
                    "RESET_FORM",
                    "SUBMIT_FORM",
                    "OPEN_MODAL",
                    "SWITCH_TAB"
            ).contains(type)) {
                if (targetId.isBlank()) {
                    errors.add(
                            "动作 "
                                    + actionId
                                    + " 缺少 targetId。"
                    );
                    continue;
                }

                if (!componentIndex.componentIds.contains(targetId)) {
                    errors.add(
                            "动作 "
                                    + actionId
                                    + " 的 targetId 不存在："
                                    + targetId
                    );
                    continue;
                }
            }

            if (Set.of("RESET_FORM", "SUBMIT_FORM").contains(type)
                    && !"FORM".equals(
                    componentIndex.componentTypes.get(targetId)
            )) {
                errors.add(
                        "动作 "
                                + actionId
                                + " 必须指向 FORM 组件："
                                + targetId
                );
            }

            if ("OPEN_MODAL".equals(type)
                    && !"MODAL".equals(
                    componentIndex.componentTypes.get(targetId)
            )) {
                errors.add(
                        "动作 "
                                + actionId
                                + " 必须指向 MODAL 组件："
                                + targetId
                );
            }

            if ("SWITCH_TAB".equals(type)
                    && !"TABS".equals(
                    componentIndex.componentTypes.get(targetId)
            )) {
                errors.add(
                        "动作 "
                                + actionId
                                + " 必须指向 TABS 组件："
                                + targetId
                );
            }

            if ("NAVIGATE".equals(type)) {
                String targetPageId =
                        stringValue(action.get("targetPageId"));

                if (targetPageId.isBlank()) {
                    errors.add(
                            "导航动作 "
                                    + actionId
                                    + " 缺少 targetPageId。"
                    );
                } else if (!navigationIndex.targetPageIds
                        .contains(targetPageId)) {
                    errors.add(
                            "导航动作 "
                                    + actionId
                                    + " 未在 navigationTargets 中声明目标页面："
                                    + targetPageId
                    );
                }
            }

            if ("SET_PAGE_STATE".equals(type)) {
                if (targetId.isBlank()) {
                    errors.add(
                            "页面状态动作 "
                                    + actionId
                                    + " 缺少 targetId（stateKey）。"
                    );
                } else if (!pageStateIndex.stateKeys.contains(targetId)) {
                    errors.add(
                            "页面状态动作 "
                                    + actionId
                                    + " 引用了不存在的 stateKey："
                                    + targetId
                    );
                }
            }

            if (Set.of("QUERY_DATA", "DELETE_ROW").contains(type)
                    && stringValue(action.get("dataBindingKey")).isBlank()) {
                warnings.add(
                        "动作 "
                                + actionId
                                + " 未声明 dataBindingKey。"
                );
            }
        }
    }

    private CoverageMetrics validateFeatureCoverage(
            Map<String, Object> content,
            Set<String> expectedFeatureIds,
            ComponentIndex componentIndex,
            ActionIndex actionIndex,
            List<String> errors,
            List<String> warnings
    ) {
        Set<String> seenFeatureIds = new LinkedHashSet<>();
        int coveredCount = 0;
        int fullCount = 0;

        for (Map<String, Object> coverage
                : asMapList(content.get("featureCoverage"))) {
            String featureId = stringValue(coverage.get("featureId"));
            String status = upper(coverage.get("status"));

            if (featureId.isBlank()) {
                errors.add("featureCoverage 缺少 featureId。");
                continue;
            }

            if (!seenFeatureIds.add(featureId)) {
                errors.add(
                        "featureCoverage.featureId 重复："
                                + featureId
                );
            }

            if (!expectedFeatureIds.contains(featureId)) {
                errors.add(
                        "featureCoverage 引用了不属于当前 PAGE 的 FEATURE："
                                + featureId
                );
            }

            if (!COVERAGE_STATUSES.contains(status)) {
                errors.add(
                        "FEATURE "
                                + featureId
                                + " 使用了不支持的 coverage status："
                                + status
                );
            }

            for (String componentId
                    : asStringList(coverage.get("componentIds"))) {
                if (!componentIndex.componentIds.contains(componentId)) {
                    errors.add(
                            "FEATURE "
                                    + featureId
                                    + " 引用了不存在的 componentId："
                                    + componentId
                    );
                }
            }

            for (String actionId
                    : asStringList(coverage.get("actionIds"))) {
                if (!actionIndex.actionIds.contains(actionId)) {
                    errors.add(
                            "FEATURE "
                                    + featureId
                                    + " 引用了不存在的 actionId："
                                    + actionId
                    );
                }
            }

            if ("MISSING".equals(status)) {
                errors.add(
                        "FEATURE 尚未被原型覆盖："
                                + featureId
                );
            } else if ("PARTIAL".equals(status)) {
                warnings.add(
                        "FEATURE 仅被部分覆盖："
                                + featureId
                );
                coveredCount++;
            } else if ("FULL".equals(status)
                    || "NOT_APPLICABLE".equals(status)) {
                coveredCount++;

                if ("FULL".equals(status)) {
                    fullCount++;
                }
            }
        }

        for (String expectedFeatureId : expectedFeatureIds) {
            if (!seenFeatureIds.contains(expectedFeatureId)) {
                errors.add(
                        "当前 PAGE 的 FEATURE 未出现在 featureCoverage 中："
                                + expectedFeatureId
                );
            }
        }

        for (String referencedFeatureId
                : componentIndex.featureReferences) {
            if (!expectedFeatureIds.contains(referencedFeatureId)) {
                errors.add(
                        "组件或字段绑定了不属于当前 PAGE 的 FEATURE："
                                + referencedFeatureId
                );
            }
        }

        for (String referencedFeatureId
                : actionIndex.featureReferences) {
            if (!expectedFeatureIds.contains(referencedFeatureId)) {
                errors.add(
                        "动作绑定了不属于当前 PAGE 的 FEATURE："
                                + referencedFeatureId
                );
            }
        }

        int coverageRate = expectedFeatureIds.isEmpty()
                ? 100
                : Math.round(
                        coveredCount * 100.0f
                                / expectedFeatureIds.size()
                );

        return new CoverageMetrics(
                coveredCount,
                fullCount,
                coverageRate
        );
    }

    private void validateApiBindings(
            Map<String, Object> content,
            ActionIndex actionIndex,
            List<String> errors
    ) {
        Set<String> bindingKeys = new LinkedHashSet<>();

        for (Map<String, Object> binding
                : asMapList(content.get("apiBindings"))) {
            String bindingKey =
                    stringValue(binding.get("bindingKey"));

            if (bindingKey.isBlank()) {
                errors.add("apiBinding 缺少 bindingKey。");
            } else if (!bindingKeys.add(bindingKey)) {
                errors.add(
                        "apiBinding.bindingKey 重复："
                                + bindingKey
                );
            }

            for (String actionId
                    : asStringList(binding.get("usedByActionIds"))) {
                if (!actionIndex.actionIds.contains(actionId)) {
                    errors.add(
                            "API Binding "
                                    + bindingKey
                                    + " 引用了不存在的 actionId："
                                    + actionId
                    );
                }
            }
        }
    }

    private void validateQualitySummary(
            Map<String, Object> content,
            CoverageMetrics coverageMetrics,
            List<String> errors,
            List<String> warnings
    ) {
        Map<String, Object> quality =
                asMap(content.get("qualitySummary"));

        if (quality.isEmpty()) {
            errors.add("qualitySummary 必须是非空对象。");
            return;
        }

        Integer score = intValue(quality.get("score"));
        Integer declaredCoverageRate =
                intValue(quality.get("coverageRate"));

        if (score == null || score < 0 || score > 100) {
            errors.add("qualitySummary.score 必须位于 0 到 100。");
        }

        if (declaredCoverageRate == null) {
            errors.add("qualitySummary.coverageRate 必须是整数。");
        } else if (declaredCoverageRate
                != coverageMetrics.coverageRate()) {
            errors.add(
                    "qualitySummary.coverageRate 与实际 FEATURE 覆盖率不一致。"
            );
        }

        if (!asStringList(quality.get("blockers")).isEmpty()) {
            errors.add(
                    "qualitySummary.blockers 非空，当前 UI_CONTRACT 不允许入库。"
            );
        }

        for (String warning
                : asStringList(quality.get("warnings"))) {
            if (!warnings.contains(warning)) {
                warnings.add(warning);
            }
        }
    }

    private Set<String> currentFeatureIds(
            UUID projectId,
            UUID pageId
    ) {
        Set<String> result = new LinkedHashSet<>();

        for (ProjectModuleEntity module : projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)) {
            if (!ACTIVE.equalsIgnoreCase(module.getStatus())) {
                continue;
            }

            if (!pageId.equals(module.getParentId())) {
                continue;
            }

            if (!"FEATURE".equals(
                    normalizeNodeType(module.getModuleType())
            )) {
                continue;
            }

            result.add(module.getId().toString());
        }

        return result;
    }

    private void rejectExecutableContent(
            Object value,
            String path,
            List<String> errors
    ) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalizedKey = key
                        .replace("-", "")
                        .replace("_", "")
                        .toLowerCase(Locale.ROOT);

                boolean forbiddenEventHandler = key.matches(
                        "(?i)^on(click|load|error|change|input|submit|focus|blur|keydown|keyup|mouseover|mouseout)$"
                );

                if (FORBIDDEN_KEYS.contains(normalizedKey)
                        || forbiddenEventHandler) {
                    errors.add(
                            "UI_CONTRACT 禁止包含可执行属性："
                                    + path
                                    + "."
                                    + key
                    );
                }

                rejectExecutableContent(
                        entry.getValue(),
                        path + "." + key,
                        errors
                );
            }

            return;
        }

        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                rejectExecutableContent(
                        list.get(index),
                        path + "[" + index + "]",
                        errors
                );
            }
        }
    }

    private void requireString(
            Map<String, Object> source,
            String key,
            String path,
            List<String> errors
    ) {
        if (stringValue(source.get(key)).isBlank()) {
            errors.add(
                    path
                            + "."
                            + key
                            + " 必须是非空字符串。"
            );
        }
    }

    private void requireExactString(
            Map<String, Object> source,
            String key,
            String expected,
            List<String> errors
    ) {
        String value = stringValue(source.get(key));

        if (!expected.equals(value)) {
            errors.add(
                    key
                            + " 必须为 "
                            + expected
                            + "，实际为 "
                            + value
            );
        }
    }

    private void compareExact(
            String path,
            String expected,
            String actual,
            List<String> errors
    ) {
        if (expected == null) {
            if (actual != null && !actual.isBlank()) {
                errors.add(path + " 与当前 PAGE 节点不一致。");
            }
            return;
        }

        if (!expected.equals(actual)) {
            errors.add(path + " 与当前 PAGE 节点不一致。");
        }
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

    private String upper(
            Object value
    ) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private String stringValue(
            Object value
    ) {
        return value == null
                ? ""
                : String.valueOf(value).trim();
    }

    private Integer intValue(
            Object value
    ) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> asMap(
            Object value
    ) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(
                    String.valueOf(entry.getKey()),
                    entry.getValue()
            );
        }

        return result;
    }

    private List<Map<String, Object>> asMapList(
            Object value
    ) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object item : rawList) {
            Map<String, Object> map = asMap(item);

            if (!map.isEmpty()) {
                result.add(map);
            }
        }

        return result;
    }

    private List<String> asStringList(
            Object value
    ) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        for (Object item : rawList) {
            String text = stringValue(item);

            if (!text.isBlank()) {
                result.add(text);
            }
        }

        return result;
    }

    private static class ComponentIndex {
        private final Set<String> componentIds =
                new LinkedHashSet<>();
        private final Map<String, String> componentTypes =
                new LinkedHashMap<>();
        private final Set<String> fieldKeys =
                new LinkedHashSet<>();
        private final Set<String> actionReferences =
                new LinkedHashSet<>();
        private final Set<String> featureReferences =
                new LinkedHashSet<>();
        private final Map<String, List<String>> fieldRuleReferences =
                new LinkedHashMap<>();
    }

    private static class ActionIndex {
        private final Set<String> actionIds =
                new LinkedHashSet<>();
        private final Map<String, Map<String, Object>> actionsById =
                new LinkedHashMap<>();
        private final Set<String> featureReferences =
                new LinkedHashSet<>();
    }

    private static class PageStateIndex {
        private final Set<String> stateKeys =
                new LinkedHashSet<>();
        private final Set<String> stateTypes =
                new LinkedHashSet<>();
    }

    private static class NavigationIndex {
        private final Set<String> targetPageIds =
                new LinkedHashSet<>();
    }

    private record CoverageMetrics(
            int coveredCount,
            int fullCount,
            int coverageRate
    ) {
    }
}
