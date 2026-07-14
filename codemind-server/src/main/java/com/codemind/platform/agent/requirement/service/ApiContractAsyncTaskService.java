package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateApiContractRequest;
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
public class ApiContractAsyncTaskService {

    private final ProjectAccessService projectAccessService;
    private final AgentTaskService agentTaskService;
    private final CurrentUserProvider currentUserProvider;

    public ApiContractAsyncTaskService(
            ProjectAccessService projectAccessService,
            AgentTaskService agentTaskService,
            CurrentUserProvider currentUserProvider
    ) {
        this.projectAccessService = projectAccessService;
        this.agentTaskService = agentTaskService;
        this.currentUserProvider = currentUserProvider;
    }

    public AsyncAgentTaskResponse enqueueApiContractGeneration(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateApiContractRequest request
    ) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        return agentTaskService.findActiveTask(
                        project.getId(),
                        module.getId(),
                        TaskType.GENERATE_API_CONTRACT
                )
                .map(task -> toResponse(task, projectCode, true))
                .orElseGet(() -> {
                    AgentTaskEntity task = agentTaskService.createQueuedTask(
                            project.getId(),
                            module.getId(),
                            TaskType.GENERATE_API_CONTRACT,
                            buildRequestPayload(
                                    project,
                                    workspaceId,
                                    projectCode,
                                    module,
                                    request,
                                    currentUser
                            ),
                            currentUser.username()
                    );

                    return toResponse(task, projectCode, false);
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
            GenerateApiContractRequest request,
            CurrentUser currentUser
    ) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("project_id", project.getId().toString());
        requestPayload.put("project_code", projectCode);
        requestPayload.put("module_id", module.getId().toString());
        requestPayload.put("module_key", module.getModuleKey());
        requestPayload.put("module_name", module.getModuleName());
        requestPayload.put("task_type", TaskType.GENERATE_API_CONTRACT.name());
        requestPayload.put("artifact_type", "API_CONTRACT");
        requestPayload.put("requirement_text", request.getRequirementText());
        requestPayload.put("created_by_user_id", currentUser.userId().toString());
        requestPayload.put("created_by_username", currentUser.username());
        requestPayload.put("created_by_display_name", currentUser.displayName());

        if (workspaceId != null) {
            requestPayload.put("workspace_id", workspaceId.toString());
        }

        return requestPayload;
    }
}