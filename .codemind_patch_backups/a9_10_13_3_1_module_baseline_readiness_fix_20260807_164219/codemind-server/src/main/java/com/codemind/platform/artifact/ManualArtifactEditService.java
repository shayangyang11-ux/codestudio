package com.codemind.platform.artifact;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.project.ProjectAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ManualArtifactEditService {

    private static final List<String> MANUAL_EDITABLE_ARTIFACT_TYPES = List.of(
            "MODULE_PRD",
            "BUSINESS_FLOW",
            "PAGE_SPEC",
            "TEST_SPEC",
            "UI_CONTRACT"
    );

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public ManualArtifactEditService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ManualArtifactEditResponse saveManualDocumentEdit(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            ManualArtifactEditRequest request
    ) {
        ProjectAccessService.ProjectAccess access =
                projectAccessService.resolveAccessibleProject(workspaceId, projectCode);

        projectAccessService.resolveAccessibleModule(
                access.projectId(),
                moduleId
        );

        String artifactType = normalizeArtifactType(request.artifactType());

        if (!MANUAL_EDITABLE_ARTIFACT_TYPES.contains(artifactType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前资产类型不允许手动编辑文档视图：" + artifactType
            );
        }

        String contentMarkdown = safeTrim(request.contentMarkdown());
        Map<String, Object> requestedContent = copyContent(request.content());
        boolean hasRequestedContent = !requestedContent.isEmpty();

        if (contentMarkdown.isBlank() && hasRequestedContent) {
            contentMarkdown = serializeContentForMarkdown(requestedContent);
        }

        if (contentMarkdown.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文档内容不能为空。"
            );
        }

        ArtifactVersionEntity previous = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        access.projectId(),
                        moduleId,
                        artifactType
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "当前节点没有可编辑的资产版本：" + artifactType
                ));

        if (request.artifactId() != null && !request.artifactId().isBlank()) {
            UUID requestedArtifactId = parseUuid(
                    request.artifactId(),
                    "artifactId"
            );

            if (!requestedArtifactId.equals(previous.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前资产版本已变化，请刷新后再编辑。"
                );
            }
        }

        String previousMarkdown = safeTrim(previous.getContentMarkdown());
        boolean markdownUnchanged = normalizeMarkdown(previousMarkdown).equals(
                normalizeMarkdown(contentMarkdown)
        );
        boolean contentUnchanged = hasRequestedContent && normalizeContentForCompare(previous.getContent()).equals(
                normalizeContentForCompare(requestedContent)
        );

        if ((!hasRequestedContent && markdownUnchanged) || (hasRequestedContent && contentUnchanged)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "资产内容没有变化，无需生成新版本。"
            );
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String username = currentUser.username();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> content = hasRequestedContent
                ? copyContent(requestedContent)
                : copyContent(previous.getContent());

        if ("UI_CONTRACT".equals(artifactType)) {
            content = validateAndNormalizeUiContractContent(content);
            contentMarkdown = serializeContentForMarkdown(content);
        }

        content.put(
                "_manual_document_edit",
                buildManualEditMetadata(
                        previous,
                        now,
                        username,
                        request.reason(),
                        artifactType
                )
        );

        int maxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                access.projectId(),
                moduleId,
                artifactType
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                access.projectId(),
                moduleId,
                artifactType
        );

        ArtifactVersionEntity saved = new ArtifactVersionEntity();
        saved.setProjectId(access.projectId());
        saved.setModuleId(moduleId);
        saved.setTaskId(null);
        saved.setArtifactType(artifactType);
        saved.setVersionNo(maxVersionNo + 1);
        saved.setTitle(previous.getTitle());
        saved.setContent(content);
        saved.setContentMarkdown(contentMarkdown);
        saved.setStatus("PENDING_REVIEW");
        saved.setCurrent(true);
        saved.setCreatedBy(username);
        copyPromptTemplateMetadata(previous, saved);

        ArtifactVersionEntity persisted = artifactVersionRepository.save(saved);

        List<String> downstreamArtifactTypes = resolveDownstreamArtifactTypes(artifactType);

        int downstreamMarkedCount = 0;

        if (!downstreamArtifactTypes.isEmpty()) {
            downstreamMarkedCount = artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                    access.projectId(),
                    List.of(moduleId),
                    downstreamArtifactTypes
            );
        }

        return toResponse(
                persisted,
                downstreamArtifactTypes,
                downstreamMarkedCount
        );
    }

    private String normalizeArtifactType(
            String value
    ) {
        String artifactType = safeTrim(value).toUpperCase();

        if (artifactType.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缺少 artifactType。"
            );
        }

        return artifactType;
    }

    private UUID parseUuid(
            String value,
            String fieldName
    ) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "非法 " + fieldName + "：" + value
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyContent(
            Map<String, Object> content
    ) {
        if (content == null || content.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return objectMapper.convertValue(
                content,
                LinkedHashMap.class
        );
    }



    private Map<String, Object> validateAndNormalizeUiContractContent(
            Map<String, Object> rawContent
    ) {
        if (rawContent == null || rawContent.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "UI_CONTRACT 内容不能为空。"
            );
        }

        Map<String, Object> content = copyContent(rawContent);
        String artifactType = safeText(content.get("artifactType"));
        if (artifactType.isBlank()) {
            content.put("artifactType", "UI_CONTRACT");
        } else if (!"UI_CONTRACT".equals(artifactType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前保存内容不是 UI_CONTRACT：" + artifactType
            );
        }

        String schemaVersion = safeText(content.get("schemaVersion"));
        if (schemaVersion.isBlank()) {
            content.put("schemaVersion", "1.0");
        } else if (!"1.0".equals(schemaVersion)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前只允许保存 schemaVersion=1.0 的 UI_CONTRACT。"
            );
        }

        Map<String, Object> page = asMap(
                content.get("page"),
                "page"
        );
        List<Object> componentTree = asList(
                page.get("componentTree"),
                "page.componentTree"
        );
        if (componentTree.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "UI_CONTRACT 至少需要一个页面组件。"
            );
        }

        normalizeRequiredPageText(page, "pageId", "page_id");
        normalizeRequiredPageText(page, "pageKey", "page_key");
        normalizeRequiredPageText(page, "pageName", "页面预览");
        normalizeRequiredPageText(page, "route", "/");
        normalizeRequiredPageText(page, "title", safeText(page.get("pageName")));
        normalizeRequiredPageText(page, "description", "");
        normalizeRequiredPageText(page, "pageKind", "BUSINESS_PAGE");

        Object layoutValue = page.get("layout");
        if (!(layoutValue instanceof Map<?, ?>)) {
            Map<String, Object> layout = new LinkedHashMap<>();
            layout.put("type", safeText(layoutValue).isBlank() ? "CUSTOM_GRID" : safeText(layoutValue));
            page.put("layout", layout);
        }

        UiContractReferenceIndex index = buildUiContractReferenceIndex(componentTree);
        normalizeListField(content, "interactions");
        normalizeListField(content, "validationRules");
        normalizeListField(content, "pageStates");
        normalizeListField(content, "mockScenarios");
        normalizeListField(content, "apiBindings");
        normalizeListField(content, "navigationTargets");
        normalizeListField(content, "featureCoverage");
        normalizeListField(content, "assumptions");

        if (!(content.get("mockData") instanceof Map<?, ?>)) {
            content.put("mockData", new LinkedHashMap<>());
        }
        if (!(content.get("developmentRules") instanceof Map<?, ?>)) {
            content.put("developmentRules", new LinkedHashMap<>());
        }
        if (!(content.get("qualitySummary") instanceof Map<?, ?>)) {
            Map<String, Object> qualitySummary = new LinkedHashMap<>();
            qualitySummary.put("score", 85);
            qualitySummary.put("coverageRate", 100);
            qualitySummary.put("generationMode", "LLM_STRUCTURED");
            qualitySummary.put("blockers", List.of());
            qualitySummary.put("warnings", List.of("A9.5 自然语言原型编辑后端保存校验已自动补齐质量摘要。"));
            content.put("qualitySummary", qualitySummary);
        }

        validateUiContractInteractions(content, index.componentIds());
        validateUiContractFeatureCoverage(content, index.componentIds());
        validateUiContractMockScenarios(content);
        validateUiContractApiBindings(content);

        content.put("page", page);
        return content;
    }

    private UiContractReferenceIndex buildUiContractReferenceIndex(
            List<Object> componentTree
    ) {
        java.util.Set<String> componentIds = new java.util.LinkedHashSet<>();
        collectUiContractComponents(
                componentTree,
                componentIds,
                "page.componentTree"
        );
        return new UiContractReferenceIndex(componentIds);
    }

    private void collectUiContractComponents(
            List<Object> components,
            java.util.Set<String> componentIds,
            String path
    ) {
        for (int index = 0; index < components.size(); index++) {
            Object value = components.get(index);
            Map<String, Object> component = asMap(
                    value,
                    path + "[" + index + "]"
            );

            String componentId = safeText(component.get("componentId"));
            if (componentId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "组件缺少 componentId：" + path + "[" + index + "]"
                );
            }
            if (!componentIds.add(componentId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "组件 componentId 重复：" + componentId
                );
            }

            String type = safeText(component.get("type"));
            if (type.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "组件缺少 type：" + componentId
                );
            }

            Object childrenValue = component.get("children");
            if (childrenValue == null) {
                component.put("children", List.of());
            } else {
                collectUiContractComponents(
                        asList(childrenValue, path + "[" + index + "].children"),
                        componentIds,
                        path + "[" + index + "].children"
                );
            }
        }
    }

    private void validateUiContractInteractions(
            Map<String, Object> content,
            java.util.Set<String> componentIds
    ) {
        List<Object> interactions = asList(
                content.get("interactions"),
                "interactions"
        );
        java.util.Set<String> actionIds = new java.util.LinkedHashSet<>();

        for (int index = 0; index < interactions.size(); index++) {
            Map<String, Object> action = asMap(
                    interactions.get(index),
                    "interactions[" + index + "]"
            );
            String actionId = safeText(action.get("actionId"));
            if (actionId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作缺少 actionId：interactions[" + index + "]"
                );
            }
            if (!actionIds.add(actionId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作 actionId 重复：" + actionId
                );
            }

            String type = safeText(action.get("type"));
            if (type.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作缺少 type：" + actionId
                );
            }

            String sourceComponentId = safeText(action.get("sourceComponentId"));
            if (!sourceComponentId.isBlank() && !componentIds.contains(sourceComponentId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作 sourceComponentId 不存在：" + sourceComponentId
                );
            }

            String targetId = safeText(action.get("targetId"));
            if (!targetId.isBlank() && !componentIds.contains(targetId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作 targetId 不存在：" + targetId
                );
            }
        }

        validateComponentActionIds(
                asList(asMap(content.get("page"), "page").get("componentTree"), "page.componentTree"),
                actionIds
        );
        validateActionChains(interactions, actionIds);
    }

    private void validateComponentActionIds(
            List<Object> components,
            java.util.Set<String> actionIds
    ) {
        for (Object value : components) {
            Map<String, Object> component = asMap(value, "component");
            String componentId = safeText(component.get("componentId"));
            for (String actionId : toStringList(component.get("actionIds"))) {
                if (!actionIds.contains(actionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "组件引用了不存在的 actionId：" + componentId + " -> " + actionId
                    );
                }
            }

            Object children = component.get("children");
            if (children instanceof List<?>) {
                validateComponentActionIds(
                        asList(children, componentId + ".children"),
                        actionIds
                );
            }
        }
    }

    private void validateActionChains(
            List<Object> interactions,
            java.util.Set<String> actionIds
    ) {
        for (Object value : interactions) {
            Map<String, Object> action = asMap(value, "interaction");
            String actionId = safeText(action.get("actionId"));
            for (String chainedActionId : toStringList(action.get("successActionIds"))) {
                if (!actionIds.contains(chainedActionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "动作 successActionIds 引用了不存在的 actionId：" + actionId + " -> " + chainedActionId
                    );
                }
            }
            for (String chainedActionId : toStringList(action.get("failureActionIds"))) {
                if (!actionIds.contains(chainedActionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "动作 failureActionIds 引用了不存在的 actionId：" + actionId + " -> " + chainedActionId
                    );
                }
            }
        }
    }

    private void validateUiContractFeatureCoverage(
            Map<String, Object> content,
            java.util.Set<String> componentIds
    ) {
        java.util.Set<String> actionIds = collectActionIds(content);
        List<Object> featureCoverage = asList(
                content.get("featureCoverage"),
                "featureCoverage"
        );

        for (Object value : featureCoverage) {
            Map<String, Object> coverage = asMap(value, "featureCoverage[]");
            String featureName = safeText(
                    coverage.get("featureName")
            );
            for (String componentId : toStringList(coverage.get("componentIds"))) {
                if (!componentIds.contains(componentId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "featureCoverage 引用了不存在的 componentId：" + featureName + " -> " + componentId
                    );
                }
            }
            for (String actionId : toStringList(coverage.get("actionIds"))) {
                if (!actionIds.contains(actionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "featureCoverage 引用了不存在的 actionId：" + featureName + " -> " + actionId
                    );
                }
            }
        }
    }

    private void validateUiContractMockScenarios(
            Map<String, Object> content
    ) {
        java.util.Set<String> actionIds = collectActionIds(content);
        List<Object> mockScenarios = asList(
                content.get("mockScenarios"),
                "mockScenarios"
        );

        for (Object value : mockScenarios) {
            Map<String, Object> scenario = asMap(value, "mockScenarios[]");
            String actionId = safeText(scenario.get("actionId"));
            if (!actionId.isBlank() && !actionIds.contains(actionId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "mockScenarios 引用了不存在的 actionId：" + actionId
                );
            }
        }
    }

    private void validateUiContractApiBindings(
            Map<String, Object> content
    ) {
        java.util.Set<String> actionIds = collectActionIds(content);
        List<Object> apiBindings = asList(
                content.get("apiBindings"),
                "apiBindings"
        );

        for (Object value : apiBindings) {
            Map<String, Object> binding = asMap(value, "apiBindings[]");
            for (String actionId : toStringList(binding.get("usedByActionIds"))) {
                if (!actionIds.contains(actionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "apiBindings.usedByActionIds 引用了不存在的 actionId：" + actionId
                    );
                }
            }
        }
    }

    private java.util.Set<String> collectActionIds(
            Map<String, Object> content
    ) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (Object value : asList(content.get("interactions"), "interactions")) {
            Map<String, Object> action = asMap(value, "interaction");
            String actionId = safeText(action.get("actionId"));
            if (!actionId.isBlank()) {
                result.add(actionId);
            }
        }
        return result;
    }

    private void normalizeListField(
            Map<String, Object> source,
            String key
    ) {
        Object value = source.get(key);
        if (!(value instanceof List<?>)) {
            source.put(key, List.of());
        }
    }

    private void normalizeRequiredPageText(
            Map<String, Object> page,
            String key,
            String fallback
    ) {
        if (safeText(page.get(key)).isBlank()) {
            page.put(key, safeText(fallback));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(
            Object value,
            String path
    ) {
        if (value instanceof Map<?, ?>) {
            return objectMapper.convertValue(value, LinkedHashMap.class);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "字段必须是对象：" + path
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(
            Object value,
            String path
    ) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?>) {
            return objectMapper.convertValue(value, List.class);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "字段必须是数组：" + path
        );
    }

    private List<String> toStringList(
            Object value
    ) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        return values.stream()
                .map(this::safeText)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private record UiContractReferenceIndex(
            java.util.Set<String> componentIds
    ) {
    }


    private String serializeContentForMarkdown(
            Map<String, Object> content
    ) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "资产内容无法序列化为 JSON。"
            );
        }
    }

    private String normalizeContentForCompare(
            Map<String, Object> content
    ) {
        try {
            Map<String, Object> normalized = copyContent(content);
            normalized.remove("_manual_document_edit");

            Object metadataValue = normalized.get("metadata");
            if (metadataValue instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = objectMapper.convertValue(
                        metadataValue,
                        LinkedHashMap.class
                );
                metadata.remove("prototypeSaveTrace");
                normalized.put("metadata", metadata);
            }

            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return String.valueOf(content);
        }
    }

    private Map<String, Object> buildManualEditMetadata(
            ArtifactVersionEntity previous,
            LocalDateTime editedAt,
            String editedBy,
            String reason,
            String artifactType
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "1.0");
        metadata.put(
                "editMode",
                "UI_CONTRACT".equals(artifactType)
                        ? "UI_CONTRACT_PROTOTYPE_EDIT"
                        : "MANUAL_DOCUMENT_VIEW"
        );
        metadata.put("sourceArtifactId", previous.getId().toString());
        metadata.put("sourceVersionNo", previous.getVersionNo());
        metadata.put("editedAt", editedAt.toString());
        metadata.put("editedBy", editedBy);
        metadata.put("reason", safeTrim(reason));
        metadata.put(
                "cardViewPolicy",
                "UI_CONTRACT".equals(artifactType)
                        ? "STRUCTURED_UI_CONTRACT_CONTENT"
                        : "MANUAL_MARKDOWN_SECTIONS"
        );
        metadata.put(
                "note",
                "UI_CONTRACT".equals(artifactType)
                        ? "当前版本由自然语言原型编辑确认保存生成，content 为结构化 UI_CONTRACT。"
                        : "当前版本由文档视图手动编辑生成，卡片视图应根据 contentMarkdown 重新拆分展示。"
        );
        return metadata;
    }

    private void copyPromptTemplateMetadata(
            ArtifactVersionEntity source,
            ArtifactVersionEntity target
    ) {
        target.setPromptTemplateId(source.getPromptTemplateId());
        target.setPromptTemplateVersionId(source.getPromptTemplateVersionId());
        target.setPromptTemplateKey(source.getPromptTemplateKey());
        target.setPromptTemplateVersion(source.getPromptTemplateVersion());
        target.setPromptTemplateChecksum(source.getPromptTemplateChecksum());
    }

    private List<String> resolveDownstreamArtifactTypes(
            String artifactType
    ) {
        return switch (artifactType) {
            case "MODULE_PRD" -> List.of(
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "BUSINESS_FLOW" -> List.of(
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "PAGE_SPEC" -> List.of(
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "TEST_SPEC" -> List.of(
                    "DEV_HANDOFF_PACKAGE"
            );
            case "UI_CONTRACT" -> List.of(
                    "DEV_HANDOFF_PACKAGE"
            );
            default -> List.of();
        };
    }

    private ManualArtifactEditResponse toResponse(
            ArtifactVersionEntity artifact,
            List<String> downstreamArtifactTypes,
            int downstreamMarkedCount
    ) {
        return new ManualArtifactEditResponse(
                artifact.getId(),
                artifact.getProjectId(),
                artifact.getModuleId(),
                artifact.getArtifactType(),
                artifact.getVersionNo(),
                artifact.getTitle(),
                artifact.getStatus(),
                artifact.getCurrent(),
                artifact.getContent(),
                artifact.getContentMarkdown(),
                artifact.getCreatedBy(),
                artifact.getCreatedAt(),
                artifact.getUpdatedAt(),
                downstreamArtifactTypes,
                downstreamMarkedCount,
                "文档编辑已保存为新版本 V" + artifact.getVersionNo() + "，当前状态为新版本待确认；下游资产已标记为受上游影响。"
        );
    }

    private String normalizeMarkdown(
            String value
    ) {
        return safeTrim(value).replace("\r\n", "\n");
    }


    private String safeText(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeTrim(
            String value
    ) {
        return value == null ? "" : value.trim();
    }
}



