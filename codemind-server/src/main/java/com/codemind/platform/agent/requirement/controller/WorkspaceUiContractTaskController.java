package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateUiContractRequest;
import com.codemind.platform.agent.requirement.service.RequirementArtifactAsyncTaskService;
import com.codemind.platform.agent.requirement.service.UiContractGenerationReadinessResult;
import com.codemind.platform.agent.requirement.service.UiContractPersistenceService;
import com.codemind.platform.agent.requirement.service.UiContractReadinessQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceUiContractTaskController {

    private final UiContractPersistenceService uiContractPersistenceService;
    private final RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService;
    private final UiContractReadinessQueryService uiContractReadinessQueryService;

    public WorkspaceUiContractTaskController(
            UiContractPersistenceService uiContractPersistenceService,
            RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService,
            UiContractReadinessQueryService uiContractReadinessQueryService
    ) {
        this.uiContractPersistenceService = uiContractPersistenceService;
        this.requirementArtifactAsyncTaskService = requirementArtifactAsyncTaskService;
        this.uiContractReadinessQueryService = uiContractReadinessQueryService;
    }

    @GetMapping("/ui-contract/readiness")
    public UiContractGenerationReadinessResult getUiContractReadiness(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return uiContractReadinessQueryService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );
    }

    @PostMapping("/ui-contract")
    public Map<String, Object> generateUiContract(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateUiContractRequest request
    ) {
        return uiContractPersistenceService.generateAndPersistUiContract(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/ui-contract/async")
    public AsyncAgentTaskResponse generateUiContractAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateUiContractRequest request
    ) {
        return requirementArtifactAsyncTaskService.enqueueUiContractGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}
