package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateDataModelRequest;
import com.codemind.platform.agent.requirement.service.DataModelAsyncTaskService;
import com.codemind.platform.agent.requirement.service.DataModelPersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceDataModelTaskController {

    private final DataModelPersistenceService dataModelPersistenceService;
    private final DataModelAsyncTaskService dataModelAsyncTaskService;

    public WorkspaceDataModelTaskController(
            DataModelPersistenceService dataModelPersistenceService,
            DataModelAsyncTaskService dataModelAsyncTaskService
    ) {
        this.dataModelPersistenceService = dataModelPersistenceService;
        this.dataModelAsyncTaskService = dataModelAsyncTaskService;
    }

    @PostMapping("/data-model")
    public Map<String, Object> generateDataModel(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateDataModelRequest request
    ) {
        return dataModelPersistenceService.generateAndPersistDataModel(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/data-model/async")
    public AsyncAgentTaskResponse generateDataModelAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateDataModelRequest request
    ) {
        return dataModelAsyncTaskService.enqueueDataModelGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}