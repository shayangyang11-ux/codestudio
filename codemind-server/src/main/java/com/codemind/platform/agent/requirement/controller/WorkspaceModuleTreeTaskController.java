package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.GenerateModuleTreeRequest;
import com.codemind.platform.agent.requirement.service.ModuleTreePersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/tasks")
public class WorkspaceModuleTreeTaskController {

    private final ModuleTreePersistenceService moduleTreePersistenceService;

    public WorkspaceModuleTreeTaskController(
            ModuleTreePersistenceService moduleTreePersistenceService
    ) {
        this.moduleTreePersistenceService = moduleTreePersistenceService;
    }

    @PostMapping("/module-tree")
    public Map<String, Object> generateModuleTree(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody GenerateModuleTreeRequest request
    ) {
        return moduleTreePersistenceService.generateAndPersistModuleTree(
                workspaceId,
                projectCode,
                request
        );
    }
}