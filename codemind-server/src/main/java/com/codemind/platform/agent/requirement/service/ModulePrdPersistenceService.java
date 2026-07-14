package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.common.gateway.AgentGatewayService;
import com.codemind.platform.agent.requirement.dto.GenerateModulePrdRequest;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ModulePrdPersistenceService {

    private static final String ARTIFACT_TYPE = "MODULE_PRD";

    private final ProjectAccessService projectAccessService;
    private final AgentTaskService agentTaskService;
    private final ContextPackageService contextPackageService;
    private final AgentGatewayService agentGatewayService;
    private final ArtifactService artifactService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public ModulePrdPersistenceService(
            ProjectAccessService projectAccessService,
            AgentTaskService agentTaskService,
            ContextPackageService contextPackageService,
            AgentGatewayService agentGatewayService,
            ArtifactService artifactService,
            AuditService auditService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider
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
    }

    public Map<String, Object> generateAndPersistModulePrd(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateModulePrdRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        return generateAndPersistModulePrdWithProjectAndModule(
                project,
                workspaceId,
                projectCode,
                module,
                request
        );
    }

    public Map<String, Object> generateAndPersistModulePrd(
            String projectCode,
            UUID moduleId,
            GenerateModulePrdRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntityByCode(
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        return generateAndPersistModulePrdWithProjectAndModule(
                project,
                null,
                projectCode,
                module,
                request
        );
    }

    public Map<String, Object> generateAndPersistModulePrdForExistingTask(
            UUID taskId,
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateModulePrdRequest request,
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

        AgentTaskEntity task = agentTaskService.getById(taskId);

        return executeModulePrdGeneration(
                task,
                project,
                workspaceId,
                projectCode,
                module,
                request,
                username
        );
    }

    private Map<String, Object> generateAndPersistModulePrdWithProjectAndModule(
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            ProjectModuleEntity module,
            GenerateModulePrdRequest request
    ) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        Map<String, Object> requestPayload = buildRequestPayload(
                project,
                workspaceId,
                projectCode,
                module,
                request
        );

        AgentTaskEntity task = agentTaskService.createRunningTask(
                project.getId(),
                module.getId(),
                TaskType.GENERATE_MODULE_PRD,
                requestPayload,
                currentUser.username()
        );

        return executeModulePrdGeneration(
                task,
                project,
                workspaceId,
                projectCode,
                module,
                request,
                currentUser.username()
        );
    }

    private Map<String, Object> executeModulePrdGeneration(
            AgentTaskEntity task,
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            ProjectModuleEntity module,
            GenerateModulePrdRequest request,
            String username
    ) {
        Map<String, Object> requestPayload = task.getRequestPayload();

        if (requestPayload == null || requestPayload.isEmpty()) {
            requestPayload = buildRequestPayload(
                    project,
                    workspaceId,
                    projectCode,
                    module,
                    request
            );
        }

        try {
            Map<String, Object> contextPackage = contextPackageService.buildAndLogContextPackage(
                    task,
                    project,
                    module,
                    TaskType.GENERATE_MODULE_PRD,
                    requestPayload
            );

            Object response = agentGatewayService.generateModulePrd(
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

            Map<String, Object> artifactContent = extractArtifactContent(responseEnvelope);
            String contentMarkdown = extractMarkdown(responseEnvelope, artifactContent);

            ArtifactVersionEntity artifact = transactionTemplate.execute(status -> {
                ArtifactVersionEntity savedArtifact = artifactService.saveArtifact(
                        project.getId(),
                        module.getId(),
                        task.getId(),
                        ARTIFACT_TYPE,
                        module.getModuleName() + " 模块 PRD",
                        artifactContent,
                        contentMarkdown,
                        username
                );

                if (shouldMarkPartialSuccess(responseEnvelope)) {
                    agentTaskService.markPartialSuccess(
                            task.getId(),
                            responseEnvelope,
                            savedArtifact.getId(),
                            "AGENT_WORKFLOW_SUCCESS_WITH_WARNINGS",
                            "Python workflow finished with warnings. See response metadata for details."
                    );
                } else {
                    agentTaskService.markSuccess(
                            task.getId(),
                            responseEnvelope,
                            savedArtifact.getId()
                    );
                }

                auditService.record(
                        project.getId(),
                        "USER",
                        username,
                        "GENERATE_MODULE_PRD",
                        "ARTIFACT_VERSION",
                        savedArtifact.getId(),
                        null,
                        Map.of(
                                "artifact_type", ARTIFACT_TYPE,
                                "module_id", module.getId().toString(),
                                "module_key", module.getModuleKey(),
                                "version_no", savedArtifact.getVersionNo()
                        ),
                        Map.of(
                                "task_id", task.getId().toString(),
                                "project_code", projectCode,
                                "workspace_id", workspaceId == null ? "" : workspaceId.toString()
                        )
                );

                return savedArtifact;
            });

            if (artifact == null) {
                throw new IllegalStateException("Failed to save MODULE_PRD artifact.");
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
            result.put(
                    "taskStatus",
                    shouldMarkPartialSuccess(responseEnvelope)
                            ? TaskStatus.PARTIAL_SUCCESS.name()
                            : TaskStatus.SUCCESS.name()
            );

            return result;

        } catch (Exception e) {
            AgentTaskEntity latestTask = agentTaskService.getById(task.getId());

            if (!TaskStatus.FAILED.name().equals(latestTask.getStatus())) {
                agentTaskService.markFailed(
                        task.getId(),
                        "GENERATE_MODULE_PRD_FAILED",
                        e.getMessage()
                );
            }

            auditService.record(
                    project.getId(),
                    "USER",
                    username,
                    "GENERATE_MODULE_PRD_FAILED",
                    "AGENT_TASK",
                    task.getId(),
                    null,
                    Map.of(
                            "error_message", e.getMessage() == null ? "" : e.getMessage(),
                            "module_id", module.getId().toString(),
                            "module_key", module.getModuleKey()
                    ),
                    Map.of(
                            "project_code", projectCode,
                            "workspace_id", workspaceId == null ? "" : workspaceId.toString()
                    )
            );

            throw e;
        }
    }

    private ProjectEntity resolveProject(
            UUID workspaceId,
            String projectCode
    ) {
        if (workspaceId == null) {
            return projectAccessService.resolveAccessibleProjectEntityByCode(projectCode);
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
            GenerateModulePrdRequest request
    ) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("project_id", project.getId().toString());
        requestPayload.put("project_code", projectCode);
        requestPayload.put("module_id", module.getId().toString());
        requestPayload.put("module_key", module.getModuleKey());
        requestPayload.put("module_name", module.getModuleName());
        requestPayload.put("task_type", TaskType.GENERATE_MODULE_PRD.name());
        requestPayload.put("artifact_type", ARTIFACT_TYPE);
        requestPayload.put("requirement_text", request.getRequirementText());

        if (workspaceId != null) {
            requestPayload.put("workspace_id", workspaceId.toString());
        }

        return requestPayload;
    }

    private boolean isFailedResponse(
            Map<String, Object> responseEnvelope
    ) {
        Object status = responseEnvelope.get("status");

        return status != null
                && TaskStatus.FAILED.name().equalsIgnoreCase(String.valueOf(status));
    }

    private boolean shouldMarkPartialSuccess(
            Map<String, Object> responseEnvelope
    ) {
        Object metadataValue = responseEnvelope.get("metadata");

        if (!(metadataValue instanceof Map<?, ?> metadata)) {
            return false;
        }

        Object persistenceHintValue = metadata.get("persistence_hint");

        if (!(persistenceHintValue instanceof Map<?, ?> persistenceHint)) {
            return false;
        }

        Object recommendedStatus = persistenceHint.get("recommended_task_status");

        return recommendedStatus != null
                && "SUCCESS_WITH_WARNINGS".equalsIgnoreCase(String.valueOf(recommendedStatus));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
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
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            return result;
        }

        return responseEnvelope;
    }

    private String extractMarkdown(
            Map<String, Object> responseEnvelope,
            Map<String, Object> artifactContent
    ) {
        Object contentMarkdown = responseEnvelope.get("content_markdown");

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
}