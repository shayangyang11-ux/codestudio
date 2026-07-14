package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.BatchFullChainAsyncTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateBatchFullChainRequest;
import com.codemind.platform.agent.requirement.service.RequirementBatchFullChainAsyncTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/tasks")
public class WorkspaceBatchFullChainTaskController {

    private final RequirementBatchFullChainAsyncTaskService requirementBatchFullChainAsyncTaskService;

    public WorkspaceBatchFullChainTaskController(
            RequirementBatchFullChainAsyncTaskService requirementBatchFullChainAsyncTaskService
    ) {
        this.requirementBatchFullChainAsyncTaskService = requirementBatchFullChainAsyncTaskService;
    }

    @PostMapping("/full-chain/batch/async")
    public BatchFullChainAsyncTaskResponse generateBatchFullChainAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody GenerateBatchFullChainRequest request
    ) {
        return requirementBatchFullChainAsyncTaskService.enqueueBatchFullChainGeneration(
                workspaceId,
                projectCode,
                request
        );
    }
}