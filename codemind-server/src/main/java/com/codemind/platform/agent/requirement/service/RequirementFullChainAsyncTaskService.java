package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.requirement.dto.FullChainAsyncTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateFullChainRequest;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementArtifactPlan;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RequirementFullChainAsyncTaskService {

    private static final String ACTIVE = "ACTIVE";

    private final ProjectAccessService projectAccessService;
    private final AgentTaskService agentTaskService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final RequirementNodeGenerationPolicy nodeGenerationPolicy;

    public RequirementFullChainAsyncTaskService(
            ProjectAccessService projectAccessService,
            AgentTaskService agentTaskService,
            ArtifactVersionRepository artifactVersionRepository,
            CurrentUserProvider currentUserProvider,
            RequirementNodeGenerationPolicy nodeGenerationPolicy
    ) {
        this.projectAccessService = projectAccessService;
        this.agentTaskService = agentTaskService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.currentUserProvider = currentUserProvider;
        this.nodeGenerationPolicy = nodeGenerationPolicy;
    }

    @Transactional
    public FullChainAsyncTaskResponse enqueueFullChainGeneration(UUID workspaceId, String projectCode, UUID moduleId, GenerateFullChainRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(workspaceId, projectCode);
        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(project.getId(), moduleId);

        boolean overwriteExisting = request != null && request.overwriteExistingResolved();
        String baseRequirementText = request == null ? null : request.getRequirementText();

        List<RequirementArtifactPlan> definitions = nodeGenerationPolicy.fullChainDefinitionsFor(module);
        List<FullChainAsyncTaskResponse.FullChainTaskItemResponse> createdTasks = new ArrayList<>();
        List<FullChainAsyncTaskResponse.FullChainTaskItemResponse> reusedTasks = new ArrayList<>();
        List<FullChainAsyncTaskResponse.FullChainSkippedArtifactResponse> skippedArtifacts = new ArrayList<>();

        for (RequirementArtifactPlan definition : definitions) {
            try {
                nodeGenerationPolicy.assertCanGenerate(module, definition.taskType());
            } catch (IllegalArgumentException ex) {
                skippedArtifacts.add(new FullChainAsyncTaskResponse.FullChainSkippedArtifactResponse(
                        project.getId(), module.getId(), definition.taskType().name(), definition.artifactType(), definition.title(), ex.getMessage()
                ));
                continue;
            }

            boolean artifactExists = artifactVersionRepository.existsByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueAndStatus(
                    project.getId(), module.getId(), definition.artifactType(), ACTIVE
            );

            if (artifactExists && !overwriteExisting) {
                skippedArtifacts.add(new FullChainAsyncTaskResponse.FullChainSkippedArtifactResponse(
                        project.getId(), module.getId(), definition.taskType().name(), definition.artifactType(), definition.title(),
                        "当前节点已存在该资产，且 overwriteExisting=false。"
                ));
                continue;
            }

            AgentTaskEntity task = agentTaskService.findActiveTask(project.getId(), module.getId(), definition.taskType()).orElse(null);
            if (task != null) {
                reusedTasks.add(toTaskItemResponse(task, definition, true));
                continue;
            }

            AgentTaskEntity createdTask = agentTaskService.createQueuedTask(
                    project.getId(),
                    module.getId(),
                    definition.taskType(),
                    buildRequestPayload(
                            project,
                            workspaceId,
                            projectCode,
                            module,
                            definition,
                            nodeGenerationPolicy.buildRequirementInstruction(module, definition.taskType(), baseRequirementText),
                            currentUser
                    ),
                    currentUser.username(),
                    AgentTaskService.DEFAULT_REQUIREMENT_QUEUE,
                    definition.priority(),
                    3
            );

            createdTasks.add(toTaskItemResponse(createdTask, definition, false));
        }

        return new FullChainAsyncTaskResponse(
                project.getId(), projectCode, module.getId(), module.getModuleKey(), module.getModuleName(), overwriteExisting,
                createdTasks, reusedTasks, skippedArtifacts, definitions.size(), LocalDateTime.now()
        );
    }

    private FullChainAsyncTaskResponse.FullChainTaskItemResponse toTaskItemResponse(
            AgentTaskEntity task,
            RequirementArtifactPlan definition,
            boolean reused
    ) {
        return new FullChainAsyncTaskResponse.FullChainTaskItemResponse(
                task.getId(), task.getProjectId(), task.getModuleId(), task.getTaskType(),
                definition.artifactType(), definition.title(), task.getStatus(), task.getCreatedAt(), reused
        );
    }

    private Map<String, Object> buildRequestPayload(
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            ProjectModuleEntity module,
            RequirementArtifactPlan definition,
            String requirementText,
            CurrentUser currentUser
    ) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("project_id", project.getId().toString());
        requestPayload.put("project_code", projectCode);
        requestPayload.put("module_id", module.getId().toString());
        requestPayload.put("module_key", module.getModuleKey());
        requestPayload.put("module_name", module.getModuleName());
        requestPayload.put("module_type", nodeGenerationPolicy.resolveNodeType(module).name());
        requestPayload.put("node_type", nodeGenerationPolicy.resolveNodeType(module).name());
        requestPayload.put("generation_scope", nodeGenerationPolicy.buildGenerationScope(module, definition.taskType()));
        requestPayload.put("current_module", nodeGenerationPolicy.buildCurrentModulePayload(module));
        requestPayload.put("task_type", definition.taskType().name());
        requestPayload.put("artifact_type", definition.artifactType());
        requestPayload.put("requirement_text", requirementText);
        requestPayload.put("created_by_user_id", currentUser.userId().toString());
        requestPayload.put("created_by_username", currentUser.username());
        requestPayload.put("created_by_display_name", currentUser.displayName());
        requestPayload.put("orchestration_type", "FULL_CHAIN");
        requestPayload.put("orchestration_order", definition.order());

        if (workspaceId != null) {
            requestPayload.put("workspace_id", workspaceId.toString());
        }

        return requestPayload;
    }
}
