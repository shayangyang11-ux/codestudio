package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateModulePrdRequest;
import com.codemind.platform.agent.requirement.service.ModulePrdPersistenceService;
import com.codemind.platform.agent.requirement.service.RequirementArtifactAsyncTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceModulePrdTaskController {

    private final ModulePrdPersistenceService modulePrdPersistenceService;
    private final RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService;

    public WorkspaceModulePrdTaskController(
            ModulePrdPersistenceService modulePrdPersistenceService,
            RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService
    ) {
        this.modulePrdPersistenceService = modulePrdPersistenceService;
        this.requirementArtifactAsyncTaskService = requirementArtifactAsyncTaskService;
    }

    @PostMapping("/module-prd")
    public Map<String, Object> generateModulePrd(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateModulePrdRequest request
    ) {
        return modulePrdPersistenceService.generateAndPersistModulePrd(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/module-prd/async")
    public AsyncAgentTaskResponse generateModulePrdAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateModulePrdRequest request
    ) {
        return requirementArtifactAsyncTaskService.enqueueModulePrdGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}