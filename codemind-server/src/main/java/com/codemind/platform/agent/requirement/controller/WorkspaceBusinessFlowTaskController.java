package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateBusinessFlowRequest;
import com.codemind.platform.agent.requirement.service.BusinessFlowPersistenceService;
import com.codemind.platform.agent.requirement.service.RequirementArtifactAsyncTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceBusinessFlowTaskController {

    private final BusinessFlowPersistenceService businessFlowPersistenceService;
    private final RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService;

    public WorkspaceBusinessFlowTaskController(
            BusinessFlowPersistenceService businessFlowPersistenceService,
            RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService
    ) {
        this.businessFlowPersistenceService = businessFlowPersistenceService;
        this.requirementArtifactAsyncTaskService = requirementArtifactAsyncTaskService;
    }

    @PostMapping("/business-flow")
    public Map<String, Object> generateBusinessFlow(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateBusinessFlowRequest request
    ) {
        return businessFlowPersistenceService.generateAndPersistBusinessFlow(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/business-flow/async")
    public AsyncAgentTaskResponse generateBusinessFlowAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateBusinessFlowRequest request
    ) {
        return requirementArtifactAsyncTaskService.enqueueBusinessFlowGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}