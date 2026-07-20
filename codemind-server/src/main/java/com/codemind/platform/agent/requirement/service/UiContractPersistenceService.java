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
import java.util.UUID;

@Service
public class UiContractPersistenceService {

    private static final String ARTIFACT_TYPE = "UI_CONTRACT";

    private final ProjectAccessService projectAccessService;
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
                    extractArtifactContent(responseEnvelope);

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
