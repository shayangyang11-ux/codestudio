package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.GenerateModuleTreeRequest;
import com.codemind.platform.agent.requirement.service.ModuleTreePersistenceService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/projects/{projectCode}/tasks")
public class ModuleTreeTaskController {

    private final ModuleTreePersistenceService moduleTreePersistenceService;

    public ModuleTreeTaskController(
            ModuleTreePersistenceService moduleTreePersistenceService
    ) {
        this.moduleTreePersistenceService = moduleTreePersistenceService;
    }

    @PostMapping("/module-tree")
    public Object generateModuleTree(
            @PathVariable String projectCode,
            @RequestBody GenerateModuleTreeRequest request
    ) {
        return moduleTreePersistenceService.generateAndPersistModuleTree(
                projectCode,
                request
        );
    }
}