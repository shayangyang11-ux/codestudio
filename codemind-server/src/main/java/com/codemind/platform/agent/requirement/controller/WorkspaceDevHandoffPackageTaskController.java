package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateDevHandoffPackageRequest;
import com.codemind.platform.agent.requirement.service.DevHandoffPackageAsyncTaskService;
import com.codemind.platform.agent.requirement.service.DevHandoffPackagePersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceDevHandoffPackageTaskController {

    private final DevHandoffPackagePersistenceService devHandoffPackagePersistenceService;
    private final DevHandoffPackageAsyncTaskService devHandoffPackageAsyncTaskService;

    public WorkspaceDevHandoffPackageTaskController(
            DevHandoffPackagePersistenceService devHandoffPackagePersistenceService,
            DevHandoffPackageAsyncTaskService devHandoffPackageAsyncTaskService
    ) {
        this.devHandoffPackagePersistenceService = devHandoffPackagePersistenceService;
        this.devHandoffPackageAsyncTaskService = devHandoffPackageAsyncTaskService;
    }

    @PostMapping("/dev-handoff-package")
    public Map<String, Object> generateDevHandoffPackage(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateDevHandoffPackageRequest request
    ) {
        return devHandoffPackagePersistenceService.generateAndPersistDevHandoffPackage(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/dev-handoff-package/async")
    public AsyncAgentTaskResponse generateDevHandoffPackageAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateDevHandoffPackageRequest request
    ) {
        return devHandoffPackageAsyncTaskService.enqueueDevHandoffPackageGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}