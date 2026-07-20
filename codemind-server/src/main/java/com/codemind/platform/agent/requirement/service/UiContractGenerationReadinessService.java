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

        ArtifactVersionEntity pageSpec = currentArtifact(
                projectId,
                page.getId(),
                "PAGE_SPEC"
        );

        boolean pageSpecAvailable = isUsable(pageSpec);

        if (!pageSpecAvailable) {
            blockers.add("当前 PAGE 缺少可用的 PAGE_SPEC，请先生成页面规范。");
        }

        List<ProjectModuleEntity> features = projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                .stream()
                .filter(item -> ACTIVE.equalsIgnoreCase(item.getStatus()))
                .filter(item -> page.getId().equals(item.getParentId()))
                .filter(item -> "FEATURE".equals(normalizeNodeType(item.getModuleType())))
                .toList();

        if (features.isEmpty() && !NON_INTERACTIVE_PAGE_KINDS.contains(pageKind)) {
            blockers.add(
                    "当前交互型 PAGE 尚未定义任何 FEATURE。"
                            + "请先在页面下建立功能点，或将页面类型设置为静态、只读或系统状态页面。"
            );
        }

        List<UiContractGenerationReadinessResult.FeatureReadinessItem> featureItems =
                new ArrayList<>();

        int completeFeatureCount = 0;

        for (ProjectModuleEntity feature : features) {
            FeatureDefinitionCheck check = checkFeatureDefinition(
                    projectId,
                    feature
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
            warnings.add("当前 PAGE 缺少 DATA_MODEL，字段类型和数据结构将主要依据 PAGE_SPEC 与 FEATURE 定义。");
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
            ProjectModuleEntity feature
    ) {
        ArtifactVersionEntity featureRequirement = currentArtifact(
                projectId,
                feature.getId(),
                "MODULE_PRD"
        );

        if (isUsable(featureRequirement)) {
            return new FeatureDefinitionCheck(
                    true,
                    "FEATURE_MODULE_PRD",
                    List.of()
            );
        }

        Map<String, Object> extra = feature.getExtra() == null
                ? Map.of()
                : new LinkedHashMap<>(feature.getExtra());

        List<String> missing = new ArrayList<>();

        if (!hasText(readFirst(extra, "description", "featureDescription", "purpose"))) {
            missing.add("功能目的/说明");
        }

        if (!hasText(readFirst(extra, "trigger", "triggerCondition", "userTrigger"))) {
            missing.add("触发方式");
        }

        if (!hasText(readFirst(extra, "interactionType", "interaction_type", "actionType"))) {
            missing.add("交互类型");
        }

        if (!hasText(readFirst(extra, "expectedResult", "expected_result", "result"))) {
            missing.add("预期结果");
        }

        return new FeatureDefinitionCheck(
                missing.isEmpty(),
                missing.isEmpty() ? "MODULE_TREE_EXTRA" : "INCOMPLETE",
                List.copyOf(missing)
        );
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
            Map<String, Object> source,
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
        return value != null && !String.valueOf(value).isBlank();
    }

    private record FeatureDefinitionCheck(
            boolean complete,
            String definitionSource,
            List<String> missingFields
    ) {
    }
}
