package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.FullChainAsyncTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateFullChainRequest;
import com.codemind.platform.agent.requirement.service.RequirementFullChainAsyncTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceFullChainTaskController {

    private final RequirementFullChainAsyncTaskService requirementFullChainAsyncTaskService;

    public WorkspaceFullChainTaskController(
            RequirementFullChainAsyncTaskService requirementFullChainAsyncTaskService
    ) {
        this.requirementFullChainAsyncTaskService = requirementFullChainAsyncTaskService;
    }

    @PostMapping("/full-chain/async")
    public FullChainAsyncTaskResponse generateFullChainAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateFullChainRequest request
    ) {
        return requirementFullChainAsyncTaskService.enqueueFullChainGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}