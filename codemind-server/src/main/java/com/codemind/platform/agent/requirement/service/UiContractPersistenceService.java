// A9.10.14.4.3.15.7.24L3B5B1_7B_FIX_EFFECTIVELY_FINAL_ARTIFACT_CONTENT
// A9.10.14.4.3.15.7.24L3B5B1_7_JAVA_PRE_VALIDATE_STABLE_KEYS
package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.common.gateway.AgentGatewayService;
import com.codemind.platform.agent.requirement.dto.GenerateUiContractRequest;
import com.codemind.platform.artifact.ArtifactService;
import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.audit.AuditService;
import com.codemind.platform.context.ContextPackageService;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import com.codemind.platform.task.TaskStatus;
import com.codemind.platform.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class UiContractPersistenceService {

    // A9.10.14.4.3.15.6.24L2C4A_UI_CONTRACT_NAV_CONTEXT_FROM_MODULE_TREE

    private static final String ARTIFACT_TYPE = "UI_CONTRACT";

    private final ProjectAccessService projectAccessService;
    private final ProjectModuleRepository projectModuleRepository;
    private final AgentTaskService agentTaskService;
    private final ContextPackageService contextPackageService;
    private final AgentGatewayService agentGatewayService;
    private final ArtifactService artifactService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;
    private final UiContractGenerationReadinessService readinessService;
    private final UiContractValidationService validationService;

    public UiContractPersistenceService(
            ProjectAccessService projectAccessService,
            ProjectModuleRepository projectModuleRepository,
            AgentTaskService agentTaskService,
            ContextPackageService contextPackageService,
            AgentGatewayService agentGatewayService,
            ArtifactService artifactService,
            AuditService auditService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider,
            UiContractGenerationReadinessService readinessService,
            UiContractValidationService validationService
    ) {
        this.projectAccessService = projectAccessService;
        this.projectModuleRepository = projectModuleRepository;
        this.agentTaskService = agentTaskService;
        this.contextPackageService = contextPackageService;
        this.agentGatewayService = agentGatewayService;
        this.artifactService = artifactService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
        this.readinessService = readinessService;
        this.validationService = validationService;
    }

    public Map<String, Object> generateAndPersistUiContract(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateUiContractRequest request
    ) {
        ProjectEntity project = resolveProject(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        assertPageNodeForUiContract(module);

        GenerateUiContractRequest safeRequest = request == null
                ? new GenerateUiContractRequest()
                : request;

        UiContractGenerationReadinessResult readiness =
                readinessService.evaluate(
                        project.getId(),
                        module
                );

        readiness.assertReady();

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        Map<String, Object> requestPayload = buildRequestPayload(
                project,
                workspaceId,
                projectCode,
                module,
                safeRequest,
                readiness
        );

        AgentTaskEntity task = agentTaskService.createRunningTask(
                project.getId(),
                module.getId(),
                TaskType.GENERATE_UI_CONTRACT,
                requestPayload,
                currentUser.username()
        );

        return executeUiContractGeneration(
                task,
                project,
                workspaceId,
                projectCode,
                module,
                safeRequest,
                readiness,
                currentUser.username()
        );
    }

    public Map<String, Object> generateAndPersistUiContractForExistingTask(
            UUID taskId,
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateUiContractRequest request,
            String username
    ) {
        ProjectEntity project = resolveProject(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        assertPageNodeForUiContract(module);

        GenerateUiContractRequest safeRequest = request == null
                ? new GenerateUiContractRequest()
                : request;

        AgentTaskEntity task = agentTaskService.getById(taskId);

        UiContractGenerationReadinessResult readiness =
                readinessService.evaluate(
                        project.getId(),
                        module
                );

        if (!readiness.ready()) {
            String errorMessage = readiness.blockers().isEmpty()
                    ? "当前 PAGE 尚未满足交互原型生成条件。"
                    : String.join("；", readiness.blockers());

            agentTaskService.markFailed(
                    task.getId(),
                    "UI_CONTRACT_NOT_READY",
                    errorMessage
            );

            throw new IllegalStateException(errorMessage);
        }

        return executeUiContractGeneration(
                task,
                project,
                workspaceId,
                projectCode,
                module,
                safeRequest,
                readiness,
                username
        );
    }

    private Map<String, Object> executeUiContractGeneration(
            AgentTaskEntity task,
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            ProjectModuleEntity module,
            GenerateUiContractRequest request,
            UiContractGenerationReadinessResult readiness,
            String username
    ) {
        Map<String, Object> requestPayload =
                mutablePayload(task.getRequestPayload());

        if (requestPayload.isEmpty()) {
            requestPayload = buildRequestPayload(
                    project,
                    workspaceId,
                    projectCode,
                    module,
                    request,
                    readiness
            );
        } else {
            requestPayload.put(
                    "ui_contract_readiness",
                    readiness.toMap()
            );
        }

        try {
            Map<String, Object> contextPackage =
                    contextPackageService.buildAndLogContextPackage(
                            task,
                            project,
                            module,
                            TaskType.GENERATE_UI_CONTRACT,
                            requestPayload
                    );

            enrichUiContractNavigationContext(
                    contextPackage,
                    project,
                    module
            );

            contextPackage.put(
                    "ui_contract_readiness",
                    readiness.toMap()
            );

            Object response = agentGatewayService.generateUiContract(
                    task,
                    project,
                    module,
                    request,
                    contextPackage
            );

            Map<String, Object> responseEnvelope = toMap(response);

            if (isFailedResponse(responseEnvelope)) {
                String errorCode = String.valueOf(
                        responseEnvelope.getOrDefault(
                                "error_code",
                                "PYTHON_AGENT_RESPONSE_FAILED"
                        )
                );

                String errorMessage = String.valueOf(
                        responseEnvelope.getOrDefault(
                                "error_message",
                                "Python agent returned FAILED status."
                        )
                );

                agentTaskService.markFailed(
                        task.getId(),
                        errorCode,
                        errorMessage
                );

                throw new IllegalStateException(errorMessage);
            }

            Map<String, Object> artifactContent =
                a91578NormalizeStableUiContractRuntimeKeys(
                        extractArtifactContent(responseEnvelope)
                );
UiContractValidationResult validation =
                    validationService.validate(
                            project.getId(),
                            module,
                            artifactContent
                    );

            validation.assertValid();

            String contentMarkdown = extractMarkdown(
                    responseEnvelope,
                    artifactContent
            );

            boolean partialSuccess =
                    shouldMarkPartialSuccess(responseEnvelope)
                            || !readiness.warnings().isEmpty()
                            || !validation.warnings().isEmpty();

            ArtifactVersionEntity artifact =
                    transactionTemplate.execute(status -> {
                        ArtifactVersionEntity savedArtifact =
                                artifactService.saveArtifact(
                                        project.getId(),
                                        module.getId(),
                                        task.getId(),
                                        ARTIFACT_TYPE,
                                        module.getModuleName() + " 交互原型",
                                        artifactContent,
                                        contentMarkdown,
                                        username
                                );

                        if (partialSuccess) {
                            Map<String, Object> enrichedResponse =
                                    new LinkedHashMap<>(responseEnvelope);

                            enrichedResponse.put(
                                    "ui_contract_readiness",
                                    readiness.toMap()
                            );
                            enrichedResponse.put(
                                    "ui_contract_validation",
                                    validation.toMap()
                            );

                            agentTaskService.markPartialSuccess(
                                    task.getId(),
                                    enrichedResponse,
                                    savedArtifact.getId(),
                                    "UI_CONTRACT_SUCCESS_WITH_WARNINGS",
                                    buildWarningMessage(
                                            readiness.warnings(),
                                            validation.warnings()
                                    )
                            );
                        } else {
                            agentTaskService.markSuccess(
                                    task.getId(),
                                    responseEnvelope,
                                    savedArtifact.getId()
                            );
                        }

                        Map<String, Object> after =
                                new LinkedHashMap<>();
                        after.put("artifact_type", ARTIFACT_TYPE);
                        after.put(
                                "module_id",
                                module.getId().toString()
                        );
                        after.put(
                                "module_key",
                                module.getModuleKey()
                        );
                        after.put(
                                "version_no",
                                savedArtifact.getVersionNo()
                        );
                        after.put(
                                "readiness",
                                readiness.toMap()
                        );
                        after.put(
                                "validation",
                                validation.toMap()
                        );

                        Map<String, Object> metadata =
                                new LinkedHashMap<>();
                        metadata.put(
                                "task_id",
                                task.getId().toString()
                        );
                        metadata.put(
                                "project_code",
                                projectCode
                        );
                        metadata.put(
                                "workspace_id",
                                workspaceId == null
                                        ? ""
                                        : workspaceId.toString()
                        );

                        auditService.record(
                                project.getId(),
                                "USER",
                                username,
                                "GENERATE_UI_CONTRACT",
                                "ARTIFACT_VERSION",
                                savedArtifact.getId(),
                                null,
                                after,
                                metadata
                        );

                        return savedArtifact;
                    });

            if (artifact == null) {
                throw new IllegalStateException(
                        "Failed to save UI_CONTRACT artifact."
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", task.getId());
            result.put("artifactId", artifact.getId());
            result.put("artifactType", artifact.getArtifactType());
            result.put("moduleId", module.getId());
            result.put("moduleKey", module.getModuleKey());
            result.put("moduleName", module.getModuleName());
            result.put("content", artifactContent);
            result.put("contentMarkdown", contentMarkdown);
            result.put("readiness", readiness.toMap());
            result.put("validation", validation.toMap());
            result.put(
                    "taskStatus",
                    partialSuccess
                            ? TaskStatus.PARTIAL_SUCCESS.name()
                            : TaskStatus.SUCCESS.name()
            );

            return result;

        } catch (Exception e) {
            AgentTaskEntity latestTask =
                    agentTaskService.getById(task.getId());

            if (!TaskStatus.FAILED.name().equals(
                    latestTask.getStatus()
            )) {
                agentTaskService.markFailed(
                        task.getId(),
                        "GENERATE_UI_CONTRACT_FAILED",
                        safeMessage(e)
                );
            }

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("error_message", safeMessage(e));
            after.put("module_id", module.getId().toString());
            after.put("module_key", module.getModuleKey());
            after.put("readiness", readiness.toMap());

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("project_code", projectCode);
            metadata.put(
                    "workspace_id",
                    workspaceId == null
                            ? ""
                            : workspaceId.toString()
            );

            auditService.record(
                    project.getId(),
                    "USER",
                    username,
                    "GENERATE_UI_CONTRACT_FAILED",
                    "AGENT_TASK",
                    task.getId(),
                    null,
                    after,
                    metadata
            );

            throw e;
        }
    }


    // A9.10.14.4.3.15.6.24L2C4A_UI_CONTRACT_NAV_CONTEXT_FROM_MODULE_TREE
    // UI_CONTRACT is PAGE-scoped, but page actions such as 新增 / 查看详情 / 编辑 / 返回
    // require real sibling PAGE ids.  This method injects project/module page candidates
    // into context_package before the Python UI_CONTRACT agent runs.
    private void enrichUiContractNavigationContext(
            Map<String, Object> contextPackage,
            ProjectEntity project,
            ProjectModuleEntity currentModule
    ) {
        if (contextPackage == null
                || project == null
                || project.getId() == null
                || currentModule == null
                || currentModule.getId() == null) {
            return;
        }

        List<ProjectModuleEntity> activeModules =
                projectModuleRepository
                        .findByProjectIdOrderByLevelNoAscSortOrderAsc(project.getId())
                        .stream()
                        .filter(item -> item != null && "ACTIVE".equalsIgnoreCase(String.valueOf(item.getStatus())))
                        .toList();

        Map<UUID, ProjectModuleEntity> byId = new LinkedHashMap<>();
        for (ProjectModuleEntity item : activeModules) {
            if (item != null && item.getId() != null) {
                byId.put(item.getId(), item);
            }
        }

        List<Map<String, Object>> allPages = new ArrayList<>();
        List<Map<String, Object>> siblingPages = new ArrayList<>();
        List<Map<String, Object>> navigationCandidates = new ArrayList<>();

        UUID currentOwnerModuleId = resolveVisibleOwnerModuleId(
                currentModule,
                byId
        );

        for (ProjectModuleEntity candidate : activeModules) {
            if (!isPageNode(candidate)) {
                continue;
            }

            Map<String, Object> pagePayload =
                    buildPageNavigationPayload(
                            candidate,
                            currentModule,
                            currentOwnerModuleId,
                            byId
                    );

            allPages.add(pagePayload);

            if (isSameNavigationScope(
                    candidate,
                    currentModule,
                    currentOwnerModuleId,
                    byId
            )) {
                siblingPages.add(pagePayload);
            }

            if (!Objects.equals(candidate.getId(), currentModule.getId())
                    && isSameNavigationScope(
                    candidate,
                    currentModule,
                    currentOwnerModuleId,
                    byId
            )) {
                navigationCandidates.add(pagePayload);
            }
        }

        contextPackage.put("module_tree_pages", allPages);
        contextPackage.put("moduleTreePages", allPages);
        contextPackage.put("pages", allPages);
        contextPackage.put("sibling_pages", siblingPages);
        contextPackage.put("siblingPages", siblingPages);
        contextPackage.put("navigation_candidates", navigationCandidates);
        contextPackage.put("navigationCandidates", navigationCandidates);
        contextPackage.put("navigationTargets", navigationCandidates);

        Map<String, Object> hiddenPrototypeContext =
                toMutableMap(contextPackage.get("hidden_prototype_context"));

        hiddenPrototypeContext.put("module_tree_pages", allPages);
        hiddenPrototypeContext.put("sibling_pages", siblingPages);
        hiddenPrototypeContext.put("navigation_candidates", navigationCandidates);
        hiddenPrototypeContext.put(
                "navigationContextSource",
                "A9.10.14.4.3.15.6.24L2C4A"
        );

        contextPackage.put(
                "hidden_prototype_context",
                hiddenPrototypeContext
        );

        System.out.println(
                "[A9.10.14.4.3.15.6.24L2C4A] injected UI_CONTRACT navigation context: allPages="
                        + allPages.size()
                        + ", siblingPages="
                        + siblingPages.size()
                        + ", navigationCandidates="
                        + navigationCandidates.size()
                        + ", currentPage="
                        + currentModule.getModuleName()
        );
    }

    private Map<String, Object> buildPageNavigationPayload(
            ProjectModuleEntity page,
            ProjectModuleEntity currentModule,
            UUID currentOwnerModuleId,
            Map<UUID, ProjectModuleEntity> byId
    ) {
        Map<String, Object> extra = page.getExtra() == null
                ? Map.of()
                : page.getExtra();

        String pageKind = text(
                extra.get("pageKind"),
                extra.get("page_kind"),
                extra.get("pageType"),
                extra.get("page_type"),
                extra.get("layoutType"),
                extra.get("layout_type"),
                page.getModuleName()
        );

        String routePath = text(
                extra.get("routePath"),
                extra.get("route_path"),
                extra.get("route"),
                "/" + safeRouteSegment(page.getModuleKey())
        );

        UUID ownerModuleId = resolveVisibleOwnerModuleId(
                page,
                byId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetPageId", page.getId().toString());
        result.put("targetPageKey", text(page.getModuleKey()));
        result.put("targetPageName", text(page.getModuleName()));
        result.put("targetRoute", routePath);
        result.put("route", routePath);
        result.put("routePath", routePath);
        result.put("navigationStatus", "RESOLVED");
        result.put("status", "RESOLVED");

        result.put("pageId", page.getId().toString());
        result.put("pageKey", text(page.getModuleKey()));
        result.put("pageName", text(page.getModuleName()));
        result.put("moduleId", page.getId().toString());
        result.put("moduleKey", text(page.getModuleKey()));
        result.put("moduleName", text(page.getModuleName()));
        result.put("moduleType", text(page.getModuleType()));
        result.put("pageKind", pageKind);
        result.put("layoutType", text(extra.get("layoutType"), extra.get("layout_type")));
        result.put("parentId", page.getParentId() == null ? "" : page.getParentId().toString());
        result.put("ownerModuleId", ownerModuleId == null ? "" : ownerModuleId.toString());
        result.put("currentPage", Objects.equals(page.getId(), currentModule.getId()));
        result.put("sameParent", Objects.equals(page.getParentId(), currentModule.getParentId()));
        result.put("sameOwnerModule", Objects.equals(ownerModuleId, currentOwnerModuleId));
        result.put("source", "A9.10.14.4.3.15.6.24L2C4A");

        Object pagePrototypeContext = extra.get("pagePrototypeContext");
        if (pagePrototypeContext instanceof Map<?, ?> rawContext) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawContext.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            result.put("pagePrototypeContext", normalized);
        }

        return result;
    }

    private boolean isSameNavigationScope(
            ProjectModuleEntity candidate,
            ProjectModuleEntity currentModule,
            UUID currentOwnerModuleId,
            Map<UUID, ProjectModuleEntity> byId
    ) {
        if (candidate == null || currentModule == null) {
            return false;
        }

        if (Objects.equals(candidate.getParentId(), currentModule.getParentId())) {
            return true;
        }

        UUID candidateOwnerId =
                resolveVisibleOwnerModuleId(
                        candidate,
                        byId
                );

        return currentOwnerModuleId != null
                && Objects.equals(candidateOwnerId, currentOwnerModuleId);
    }

    private UUID resolveVisibleOwnerModuleId(
            ProjectModuleEntity node,
            Map<UUID, ProjectModuleEntity> byId
    ) {
        if (node == null) {
            return null;
        }

        ProjectModuleEntity cursor = node;

        while (cursor != null) {
            if ("MODULE".equalsIgnoreCase(normalizeModuleType(cursor.getModuleType()))) {
                return cursor.getId();
            }

            UUID parentId = cursor.getParentId();
            cursor = parentId == null ? null : byId.get(parentId);
        }

        return node.getParentId();
    }

    private boolean isPageNode(
            ProjectModuleEntity node
    ) {
        if (node == null) {
            return false;
        }

        String type = normalizeModuleType(node.getModuleType());

        return "PAGE".equals(type)
                || "PAGE_MODULE".equals(type);
    }

    private String normalizeModuleType(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String text = value.trim().toUpperCase();

        if (text.contains("PAGE")
                || text.contains("SCREEN")
                || text.contains("VIEW")
                || text.contains("DIALOG")
                || text.contains("MODAL")) {
            return "PAGE";
        }

        if (text.contains("MODULE")
                || text.contains("DOMAIN")
                || text.contains("SCENE")) {
            return "MODULE";
        }

        if (text.contains("FEATURE")
                || text.contains("CAPABILITY")
                || text.contains("FUNCTION")) {
            return "FEATURE";
        }

        return text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMutableMap(
            Object value
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (value instanceof Map<?, ?> source) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(
                            String.valueOf(entry.getKey()),
                            entry.getValue()
                    );
                }
            }
        }

        return result;
    }

    private String safeRouteSegment(
            String value
    ) {
        String text = value == null ? "" : value.trim();

        if (text.isBlank()) {
            return "page";
        }

        return text
                .replaceAll("[^a-zA-Z0-9_\\-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String text(
            Object... values
    ) {
        if (values == null) {
            return "";
        }

        for (Object value : values) {
            if (value == null) {
                continue;
            }

            String text = String.valueOf(value).trim();

            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }


    private ProjectEntity resolveProject(
            UUID workspaceId,
            String projectCode
    ) {
        if (workspaceId == null) {
            return projectAccessService.resolveAccessibleProjectEntityByCode(
                    projectCode
            );
        }

        return projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );
    }

    private Map<String, Object> buildRequestPayload(
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            ProjectModuleEntity module,
            GenerateUiContractRequest request,
            UiContractGenerationReadinessResult readiness
    ) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();

        requestPayload.put(
                "project_id",
                project.getId().toString()
        );
        requestPayload.put("project_code", projectCode);
        requestPayload.put(
                "module_id",
                module.getId().toString()
        );
        requestPayload.put(
                "module_key",
                module.getModuleKey()
        );
        requestPayload.put(
                "module_name",
                module.getModuleName()
        );
        requestPayload.put(
                "task_type",
                TaskType.GENERATE_UI_CONTRACT.name()
        );
        requestPayload.put(
                "artifact_type",
                ARTIFACT_TYPE
        );
        requestPayload.put(
                "requirement_text",
                request.getRequirementText()
        );
        requestPayload.put(
                "ui_contract_readiness",
                readiness.toMap()
        );

        if (workspaceId != null) {
            requestPayload.put(
                    "workspace_id",
                    workspaceId.toString()
            );
        }

        return requestPayload;
    }

    private Map<String, Object> mutablePayload(
            Map<String, Object> source
    ) {
        return source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
    }

    private boolean isFailedResponse(
            Map<String, Object> responseEnvelope
    ) {
        Object status = responseEnvelope.get("status");

        return status != null
                && TaskStatus.FAILED.name().equalsIgnoreCase(
                String.valueOf(status)
        );
    }

    private boolean shouldMarkPartialSuccess(
            Map<String, Object> responseEnvelope
    ) {
        Object metadataValue = responseEnvelope.get("metadata");

        if (!(metadataValue instanceof Map<?, ?> metadata)) {
            return false;
        }

        Object persistenceHintValue =
                metadata.get("persistence_hint");

        if (!(persistenceHintValue instanceof Map<?, ?> persistenceHint)) {
            return false;
        }

        Object recommendedStatus =
                persistenceHint.get("recommended_task_status");

        return recommendedStatus != null
                && "SUCCESS_WITH_WARNINGS".equalsIgnoreCase(
                String.valueOf(recommendedStatus)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(
            Object value
    ) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(
                        String.valueOf(entry.getKey()),
                        entry.getValue()
                );
            }

            return result;
        }

        return objectMapper.convertValue(value, Map.class);
    }

    private Map<String, Object> extractArtifactContent(
            Map<String, Object> responseEnvelope
    ) {
        Object content = responseEnvelope.get("content");

        if (content instanceof Map<?, ?> contentMap) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                result.put(
                        String.valueOf(entry.getKey()),
                        entry.getValue()
                );
            }

            return result;
        }

        return responseEnvelope;
    }


    // A9.10.14.4.3.15.7.24L3B5B1_7_JAVA_PRE_VALIDATE_STABLE_KEYS
    // Final Java-side hardening before validationService.validate(...).
    // This does not relax validation. It converts label-like Chinese runtime keys
    // into deterministic ASCII keys while keeping visible label/title unchanged.
    @SuppressWarnings("unchecked")
    private Map<String, Object> a91578NormalizeStableUiContractRuntimeKeys(
            Map<String, Object> artifactContent
    ) {
        if (artifactContent == null || artifactContent.isEmpty()) {
            return artifactContent;
        }

        Map<String, Object> mockData = a91578AsMap(artifactContent.get("mockData"));

        for (Map<String, Object> component : a91578CollectUiContractComponents(artifactContent)) {
            String componentType = a91578String(
                    component.getOrDefault(
                            "componentType",
                            component.getOrDefault("type", "")
                    )
            ).toUpperCase();

            if (componentType.contains("TABLE")) {
                a91578NormalizeTableRuntimeKeys(component, mockData);
            }

            if (
                    componentType.contains("FORM")
                            || componentType.contains("FIELD_GROUP")
                            || componentType.contains("SEARCH")
                            || componentType.contains("FILTER")
                            || componentType.contains("QUERY")
            ) {
                a91578NormalizeFormRuntimeKeys(component);
            }
        }

        if (!mockData.isEmpty()) {
            artifactContent.put("mockData", mockData);
        }

        return artifactContent;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> a91578CollectUiContractComponents(Object root) {
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        a91578VisitUiContractComponents(root, result, visited);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void a91578VisitUiContractComponents(
            Object value,
            java.util.List<Map<String, Object>> result,
            java.util.Set<Object> visited
    ) {
        if (value == null || visited.contains(value)) {
            return;
        }

        visited.add(value);

        if (value instanceof Map<?, ?> sourceMap) {
            Map<String, Object> map = (Map<String, Object>) sourceMap;

            if (
                    map.containsKey("componentId")
                            || map.containsKey("componentType")
                            || map.containsKey("type")
            ) {
                result.add(map);
            }

            for (String key : java.util.List.of(
                    "page",
                    "layout",
                    "componentTree",
                    "components",
                    "children",
                    "items",
                    "sections",
                    "panels",
                    "cards",
                    "slots",
                    "body"
            )) {
                Object child = map.get(key);

                if (child instanceof java.util.List<?> list) {
                    for (Object item : list) {
                        a91578VisitUiContractComponents(item, result, visited);
                    }
                } else if (child instanceof Map<?, ?>) {
                    a91578VisitUiContractComponents(child, result, visited);
                }
            }

            return;
        }

        if (value instanceof java.util.List<?> list) {
            for (Object item : list) {
                a91578VisitUiContractComponents(item, result, visited);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void a91578NormalizeTableRuntimeKeys(
            Map<String, Object> component,
            Map<String, Object> mockData
    ) {
        Map<String, Object> props = a91578AsMap(component.get("props"));
        java.util.List<Object> columns = a91578AsList(component.get("columns"));

        if (columns.isEmpty()) {
            columns = a91578AsList(props.get("columns"));
        }

        if (columns.isEmpty()) {
            return;
        }

        String dataBindingKey = a91578FirstNonBlank(
                component.get("dataBindingKey"),
                props.get("dataKey"),
                props.get("dataBindingKey"),
                "rows"
        );

        Object rowsValue = mockData.get(dataBindingKey);
        java.util.List<Object> rows =
                rowsValue instanceof java.util.List<?> list
                        ? (java.util.List<Object>) list
                        : java.util.List.of();

        java.util.Set<String> usedKeys = new java.util.LinkedHashSet<>();
        java.util.List<String[]> renamePairs = new java.util.ArrayList<>();

        for (int index = 0; index < columns.size(); index += 1) {
            Object columnValue = columns.get(index);

            if (!(columnValue instanceof Map<?, ?> sourceColumn)) {
                continue;
            }

            Map<String, Object> column = (Map<String, Object>) sourceColumn;

            String oldColumnKey = a91578String(column.get("columnKey"));
            String oldFieldKey = a91578String(column.get("fieldKey"));
            String oldDataIndex = a91578String(column.get("dataIndex"));
            String oldKey = a91578String(column.get("key"));
            String oldName = a91578String(column.get("name"));

            String label = a91578FirstNonBlank(
                    column.get("label"),
                    column.get("title"),
                    oldColumnKey,
                    oldFieldKey,
                    oldDataIndex,
                    oldKey,
                    oldName
            );

            String stableKey = a91578StableRuntimeKey(
                    a91578FirstNonBlank(oldColumnKey, oldFieldKey, oldDataIndex, oldKey, oldName, label),
                    label,
                    "column",
                    index,
                    usedKeys
            );

            for (String old : java.util.List.of(oldColumnKey, oldFieldKey, oldDataIndex, oldKey, oldName)) {
                if (!old.isBlank() && !old.equals(stableKey)) {
                    renamePairs.add(new String[] { old, stableKey });
                }
            }

            column.put("columnKey", stableKey);
            column.put("fieldKey", stableKey);
            column.put("dataIndex", stableKey);
            column.put("key", stableKey);
            column.put("name", stableKey);

            if (!label.isBlank()) {
                column.putIfAbsent("label", label);
                column.putIfAbsent("title", label);
            }
        }

        component.put("columns", columns);
        props.put("columns", columns);
        component.put("props", props);

        for (Object rowValue : rows) {
            if (!(rowValue instanceof Map<?, ?> sourceRow)) {
                continue;
            }

            Map<String, Object> row = (Map<String, Object>) sourceRow;

            for (String[] pair : renamePairs) {
                String oldKey = pair[0];
                String stableKey = pair[1];

                if (row.containsKey(oldKey) && !row.containsKey(stableKey)) {
                    row.put(stableKey, row.get(oldKey));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void a91578NormalizeFormRuntimeKeys(Map<String, Object> component) {
        Map<String, Object> props = a91578AsMap(component.get("props"));
        java.util.List<Object> fields = a91578AsList(component.get("fields"));

        if (fields.isEmpty()) {
            fields = a91578AsList(props.get("fields"));
        }

        if (fields.isEmpty()) {
            return;
        }

        java.util.Set<String> usedKeys = new java.util.LinkedHashSet<>();

        for (int index = 0; index < fields.size(); index += 1) {
            Object fieldValue = fields.get(index);

            if (!(fieldValue instanceof Map<?, ?> sourceField)) {
                continue;
            }

            Map<String, Object> field = (Map<String, Object>) sourceField;

            String label = a91578FirstNonBlank(
                    field.get("label"),
                    field.get("title"),
                    field.get("fieldKey"),
                    field.get("key"),
                    field.get("name")
            );

            String stableKey = a91578StableRuntimeKey(
                    a91578FirstNonBlank(field.get("fieldKey"), field.get("key"), field.get("name"), label),
                    label,
                    "field",
                    index,
                    usedKeys
            );

            field.put("fieldKey", stableKey);
            field.put("key", stableKey);
            field.put("name", stableKey);

            if (!label.isBlank()) {
                field.putIfAbsent("label", label);
                field.putIfAbsent("title", label);
            }
        }

        component.put("fields", fields);
        props.put("fields", fields);
        component.put("props", props);
    }

    private String a91578StableRuntimeKey(
            String raw,
            String label,
            String prefix,
            int index,
            java.util.Set<String> usedKeys
    ) {
        String candidate = a91578String(raw);

        if (a91578IsStableAsciiKey(candidate) && !usedKeys.contains(candidate)) {
            usedKeys.add(candidate);
            return candidate;
        }

        String text = a91578FirstNonBlank(label, raw);
        java.util.List<String> parts = new java.util.ArrayList<>();

        java.util.Map<String, String> semanticMap = new java.util.LinkedHashMap<>();
        semanticMap.put("订单金额", "order_amount");
        semanticMap.put("金额", "amount");
        semanticMap.put("负责人", "owner");
        semanticMap.put("责任人", "owner");
        semanticMap.put("订单编号", "order_no");
        semanticMap.put("订单号", "order_no");
        semanticMap.put("订单状态", "order_status");
        semanticMap.put("状态", "status");
        semanticMap.put("客户", "customer");
        semanticMap.put("供应商", "supplier");
        semanticMap.put("商品", "product");
        semanticMap.put("数量", "quantity");
        semanticMap.put("时间", "time");
        semanticMap.put("日期", "date");
        semanticMap.put("名称", "name");
        semanticMap.put("编号", "code");
        semanticMap.put("备注", "remark");
        semanticMap.put("导出", "export");
        semanticMap.put("导入", "import");

        for (Map.Entry<String, String> entry : semanticMap.entrySet()) {
            if (text.contains(entry.getKey()) && !parts.contains(entry.getValue())) {
                parts.add(entry.getValue());
            }
        }

        String base;

        if (!parts.isEmpty()) {
            base = String.join("_", parts);
        } else {
            base = text
                    .toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9_\\-:.]+", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_+|_+$", "");

            if (base.isBlank()) {
                base = prefix + "_" + (index + 1);
            }
        }

        if (!base.matches("^[A-Za-z].*")) {
            base = prefix + "_" + base;
        }

        candidate = base;
        int suffix = 2;

        while (usedKeys.contains(candidate) || !a91578IsStableAsciiKey(candidate)) {
            candidate = base + "_" + suffix;
            suffix += 1;
        }

        usedKeys.add(candidate);
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> a91578AsMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return new java.util.LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> a91578AsList(Object value) {
        if (value instanceof java.util.List<?> list) {
            return (java.util.List<Object>) list;
        }

        return new java.util.ArrayList<>();
    }

    private String a91578FirstNonBlank(Object... values) {
        for (Object value : values) {
            String text = a91578String(value);
            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private String a91578String(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean a91578IsStableAsciiKey(String value) {
        return value != null && value.matches("^[A-Za-z0-9][A-Za-z0-9_\\-:.]*$");
    }


    private String extractMarkdown(
            Map<String, Object> responseEnvelope,
            Map<String, Object> artifactContent
    ) {
        Object contentMarkdown =
                responseEnvelope.get("content_markdown");

        if (contentMarkdown != null) {
            return String.valueOf(contentMarkdown);
        }

        Object artifactMarkdown = artifactContent.get("markdown");

        if (artifactMarkdown != null) {
            return String.valueOf(artifactMarkdown);
        }

        Object markdown = responseEnvelope.get("markdown");

        if (markdown != null) {
            return String.valueOf(markdown);
        }

        return null;
    }

    private String buildWarningMessage(
            List<String> readinessWarnings,
            List<String> validationWarnings
    ) {
        List<String> all = new ArrayList<>();

        if (readinessWarnings != null) {
            all.addAll(readinessWarnings);
        }

        if (validationWarnings != null) {
            all.addAll(validationWarnings);
        }

        if (all.isEmpty()) {
            return "UI_CONTRACT generated with warnings.";
        }

        return String.join("；", all);
    }

    private String safeMessage(
            Exception e
    ) {
        if (e == null) {
            return "Unknown UI_CONTRACT generation error.";
        }

        if (e.getMessage() == null
                || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }

        return e.getMessage();
    }

    private void assertPageNodeForUiContract(
            ProjectModuleEntity module
    ) {
        if (module == null) {
            throw new IllegalArgumentException(
                    "module is required."
            );
        }

        String moduleType = module.getModuleType();

        if ("PAGE".equalsIgnoreCase(moduleType)
                || "PAGE_MODULE".equalsIgnoreCase(moduleType)) {
            return;
        }

        if ("FEATURE".equalsIgnoreCase(moduleType)) {
            throw new IllegalArgumentException(
                    "当前选中节点是页面功能点，请在其所属 PAGE 页面节点生成交互原型。"
            );
        }

        throw new IllegalArgumentException(
                "交互原型只能基于 PAGE 页面节点生成。"
                        + "请先在模块树中选择具体页面节点。"
        );
    }
}
