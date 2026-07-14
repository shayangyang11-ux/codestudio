package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateTestSpecRequest;
import com.codemind.platform.agent.requirement.service.TestSpecAsyncTaskService;
import com.codemind.platform.agent.requirement.service.TestSpecPersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/tasks")
public class WorkspaceTestSpecTaskController {

    private final TestSpecPersistenceService testSpecPersistenceService;
    private final TestSpecAsyncTaskService testSpecAsyncTaskService;

    public WorkspaceTestSpecTaskController(
            TestSpecPersistenceService testSpecPersistenceService,
            TestSpecAsyncTaskService testSpecAsyncTaskService
    ) {
        this.testSpecPersistenceService = testSpecPersistenceService;
        this.testSpecAsyncTaskService = testSpecAsyncTaskService;
    }

    @PostMapping("/test-spec")
    public Map<String, Object> generateTestSpec(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateTestSpecRequest request
    ) {
        return testSpecPersistenceService.generateAndPersistTestSpec(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/test-spec/async")
    public AsyncAgentTaskResponse generateTestSpecAsync(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateTestSpecRequest request
    ) {
        return testSpecAsyncTaskService.enqueueTestSpecGeneration(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}