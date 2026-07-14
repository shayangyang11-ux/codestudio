package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GeneratePageSpecRequest;
import com.codemind.platform.agent.requirement.service.PageSpecPersistenceService;
import com.codemind.platform.agent.requirement.service.RequirementArtifactAsyncTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspacePageSpecTaskController {

    private final PageSpecPersistenceService pageSpecPersistenceService;
    private final RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService;

    public WorkspacePageSpecTaskController(
            PageSpecPersistenceService pageSpecPersistenceService,
            RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService
    ) {
        this.pageSpecPersistenceService = pageSpecPersistenceService;
        this.requirementArtifactAsyncTaskService = requirementArtifactAsyncTaskService;
    }

    @PostMapping("/page-spec")
    public Map<String, Object> generatePageSpec(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GeneratePageSpecRequest request
    ) {
        return pageSpecPersistenceService.generateAndPersistPageSpec(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/page-spec/async")
    public AsyncAgentTaskResponse generatePageSpecAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GeneratePageSpecRequest request
    ) {
        return requirementArtifactAsyncTaskService.enqueuePageSpecGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}