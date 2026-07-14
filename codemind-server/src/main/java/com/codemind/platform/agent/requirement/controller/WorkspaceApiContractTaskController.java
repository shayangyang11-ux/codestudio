package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateApiContractRequest;
import com.codemind.platform.agent.requirement.service.ApiContractAsyncTaskService;
import com.codemind.platform.agent.requirement.service.ApiContractPersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceApiContractTaskController {

    private final ApiContractPersistenceService apiContractPersistenceService;
    private final ApiContractAsyncTaskService apiContractAsyncTaskService;

    public WorkspaceApiContractTaskController(
            ApiContractPersistenceService apiContractPersistenceService,
            ApiContractAsyncTaskService apiContractAsyncTaskService
    ) {
        this.apiContractPersistenceService = apiContractPersistenceService;
        this.apiContractAsyncTaskService = apiContractAsyncTaskService;
    }

    @PostMapping("/api-contract")
    public Map<String, Object> generateApiContract(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateApiContractRequest request
    ) {
        return apiContractPersistenceService.generateAndPersistApiContract(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/api-contract/async")
    public AsyncAgentTaskResponse generateApiContractAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateApiContractRequest request
    ) {
        return apiContractAsyncTaskService.enqueueApiContractGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}