package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateBusinessFlowRequest;
import com.codemind.platform.agent.requirement.dto.GenerateModulePrdRequest;
import com.codemind.platform.agent.requirement.dto.GeneratePageSpecRequest;
import com.codemind.platform.agent.requirement.dto.GenerateUiContractRequest;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RequirementArtifactAsyncTaskService {

    private final ProjectAccessService projectAccessService;
    private final AgentTaskService agentTaskService;
    private final CurrentUserProvider currentUserProvider;
    private final RequirementNodeGenerationPolicy nodeGenerationPolicy;
    private final UiContractGenerationReadinessService uiContractReadinessService;

    public RequirementArtifactAsyncTaskService(
            ProjectAccessService projectAccessService,
            AgentTaskService agentTaskService,
            CurrentUserProvider currentUserProvider,
            RequirementNodeGenerationPolicy nodeGenerationPolicy,
            UiContractGenerationReadinessService uiContractReadinessService
    ) {
        this.projectAccessService = projectAccessService;
        this.agentTaskService = agentTaskService;
        this.currentUserProvider = currentUserProvider;
        this.nodeGenerationPolicy = nodeGenerationPolicy;
        this.uiContractReadinessService = uiContractReadinessService;
    }

    public AsyncAgentTaskResponse enqueueModulePrdGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateModulePrdRequest request
    ) {
        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                TaskType.GENERATE_MODULE_PRD,
                "MODULE_PRD",
                request == null ? null : request.getRequirementText()
        );
    }

    public AsyncAgentTaskResponse enqueueBusinessFlowGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateBusinessFlowRequest request
    ) {
        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                TaskType.GENERATE_BUSINESS_FLOW,
                "BUSINESS_FLOW",
                request == null ? null : request.getRequirementText()
        );
    }

    public AsyncAgentTaskResponse enqueuePageSpecGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GeneratePageSpecRequest request
    ) {
        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                TaskType.GENERATE_PAGE_SPEC,
                "PAGE_SPEC",
                request == null ? null : request.getRequirementText()
        );
    }

    public AsyncAgentTaskResponse enqueuePageMapGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GeneratePageSpecRequest request
    ) {
        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                TaskType.GENERATE_PAGE_MAP,
                "PAGE_MAP",
                request == null ? null : request.getRequirementText()
        );
    }

    public AsyncAgentTaskResponse enqueueUiContractGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateUiContractRequest request
    ) {
        // A9.10.14.1.1_FORCE_UI_CONTRACT_REGENERATE: explicit PAGE prototype regeneration must create a fresh task.
        Map<String, Object> extraPayload = new LinkedHashMap<>();

        if (request != null && request.getThemeProfile() != null) {
            extraPayload.put("themeProfile", request.getThemeProfile());
        }

        if (request != null && request.forceRegenerateResolved()) {
            extraPayload.put("force_regenerate", true);
        }

        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                TaskType.GENERATE_UI_CONTRACT,
                "UI_CONTRACT",
                request == null ? null : request.getRequirementText(),
                extraPayload,
                request != null && request.forceRegenerateResolved()
        );
    }

    public AsyncAgentTaskResponse enqueueArtifactGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            TaskType taskType,
            String requirementText
    ) {
        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                taskType,
                resolveArtifactType(taskType),
                requirementText
        );
    }

    private String resolveArtifactType(
            TaskType taskType
    ) {
        if (taskType == null) {
            throw new IllegalArgumentException(
                    "需求资产任务类型不能为空。"
            );
        }

        return switch (taskType) {
            case GENERATE_MODULE_PRD -> "MODULE_PRD";
            case GENERATE_BUSINESS_FLOW -> "BUSINESS_FLOW";
            case GENERATE_PAGE_MAP -> "PAGE_MAP";
            case GENERATE_PAGE_SPEC -> "PAGE_SPEC";
            case GENERATE_DATA_MODEL -> "DATA_MODEL";
            case GENERATE_API_CONTRACT -> "API_CONTRACT";
            case GENERATE_TEST_SPEC -> "TEST_SPEC";
            case GENERATE_UI_CONTRACT -> "UI_CONTRACT";
            case GENERATE_DEV_HANDOFF_PACKAGE -> "DEV_HANDOFF_PACKAGE";
            default -> throw new IllegalArgumentException(
                    "不支持的需求资产任务类型：" + taskType
            );
        };
    }

    private AsyncAgentTaskResponse enqueueModuleArtifactGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            TaskType taskType,
            String artifactType,
            String requirementText
    ) {
        return enqueueModuleArtifactGeneration(
                workspaceId,
                projectCode,
                moduleId,
                taskType,
                artifactType,
                requirementText,
                new LinkedHashMap<>(),
                false
        );
    }

    private AsyncAgentTaskResponse enqueueModuleArtifactGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            TaskType taskType,
            String artifactType,
            String requirementText,
            Map<String, Object> extraPayload,
            boolean forceRegenerate
    ) {
        CurrentUser currentUser =
                currentUserProvider.getCurrentUser();

        ProjectEntity project =
                projectAccessService.resolveAccessibleProjectEntity(
                        workspaceId,
                        projectCode
                );

        ProjectModuleEntity module =
                projectAccessService.resolveAccessibleModule(
                        project.getId(),
                        moduleId
                );

        nodeGenerationPolicy.assertCanGenerate(
                module,
                taskType
        );

        UiContractGenerationReadinessResult uiContractReadiness =
                TaskType.GENERATE_UI_CONTRACT.equals(taskType)
                        ? uiContractReadinessService.evaluate(
                        project.getId(),
                        module
                )
                        : null;

        if (uiContractReadiness != null) {
            uiContractReadiness.assertReady();
        }

        // A9.10.14.4.3.12.1_TOKEN_GUARD_AND_PRD_UI_SYNC_PATCH_ACTIVE_DEDUPE
        // forceRegenerate means "do not reuse an old artifact"; it must not bypass active-task de-duplication.
        // Otherwise one visible click can create multiple PAGE_SPEC/UI_CONTRACT tasks and burn LLM tokens.
        java.util.Optional<AgentTaskEntity> activeTask =
                agentTaskService.findActiveTask(
                        project.getId(),
                        module.getId(),
                        taskType
                );

        if (activeTask.isPresent()) {
            return toResponse(
                    activeTask.get(),
                    projectCode,
                    true
            );
        }

        if (forceRegenerate) {
            AgentTaskEntity task =
                    agentTaskService.createQueuedTask(
                            project.getId(),
                            module.getId(),
                            taskType,
                            buildRequestPayload(
                                    project,
                                    workspaceId,
                                    projectCode,
                                    module,
                                    taskType,
                                    artifactType,
                                    requirementText,
                                    currentUser,
                                    uiContractReadiness,
                                    extraPayload
                            ),
                            currentUser.username()
                    );

            return toResponse(
                    task,
                    projectCode,
                    false
            );
        }

        return java.util.Optional
                .ofNullable((AgentTaskEntity) null)
                .map(task -> toResponse(
                        task,
                        projectCode,
                        true
                ))
                .orElseGet(() -> {
                    AgentTaskEntity task =
                            agentTaskService.createQueuedTask(
                                    project.getId(),
                                    module.getId(),
                                    taskType,
                                    buildRequestPayload(
                                            project,
                                            workspaceId,
                                            projectCode,
                                            module,
                                            taskType,
                                            artifactType,
                                            requirementText,
                                            currentUser,
                                            uiContractReadiness,
                                            extraPayload
                                    ),
                                    currentUser.username()
                            );

                    return toResponse(
                            task,
                            projectCode,
                            false
                    );
                });
    }

    private AsyncAgentTaskResponse toResponse(
            AgentTaskEntity task,
            String projectCode,
            boolean reused
    ) {
        return new AsyncAgentTaskResponse(
                task.getId(),
                task.getProjectId(),
                projectCode,
                task.getModuleId(),
                task.getTaskType(),
                task.getStatus(),
                task.getCreatedAt(),
                reused
        );
    }

    private Map<String, Object> buildRequestPayload(
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            ProjectModuleEntity module,
            TaskType taskType,
            String artifactType,
            String requirementText,
            CurrentUser currentUser,
            UiContractGenerationReadinessResult uiContractReadiness,
            Map<String, Object> extraPayload
    ) {
        Map<String, Object> requestPayload =
                new LinkedHashMap<>();

        requestPayload.put(
                "project_id",
                project.getId().toString()
        );
        requestPayload.put(
                "project_code",
                projectCode
        );
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
                "module_type",
                nodeGenerationPolicy
                        .resolveNodeType(module)
                        .name()
        );
        requestPayload.put(
                "node_type",
                nodeGenerationPolicy
                        .resolveNodeType(module)
                        .name()
        );
        requestPayload.put(
                "generation_scope",
                nodeGenerationPolicy.buildGenerationScope(
                        module,
                        taskType
                )
        );
        requestPayload.put(
                "current_module",
                nodeGenerationPolicy.buildCurrentModulePayload(
                        module
                )
        );
        requestPayload.put(
                "task_type",
                taskType.name()
        );
        requestPayload.put(
                "artifact_type",
                artifactType
        );
        requestPayload.put(
                "requirement_text",
                nodeGenerationPolicy.buildRequirementInstruction(
                        module,
                        taskType,
                        requirementText
                )
        );
        requestPayload.put(
                "created_by_user_id",
                currentUser.userId().toString()
        );
        requestPayload.put(
                "created_by_username",
                currentUser.username()
        );
        requestPayload.put(
                "created_by_display_name",
                currentUser.displayName()
        );

        if (uiContractReadiness != null) {
            requestPayload.put(
                    "ui_contract_readiness",
                    uiContractReadiness.toMap()
            );
        }

        if (workspaceId != null) {
            requestPayload.put(
                    "workspace_id",
                    workspaceId.toString()
            );
        }

        if (extraPayload != null && !extraPayload.isEmpty()) {
            requestPayload.putAll(extraPayload);
        }

        return requestPayload;
    }
}
