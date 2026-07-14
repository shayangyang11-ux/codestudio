package com.codemind.platform.agent.requirement.controller;

import com.codemind.platform.agent.requirement.dto.GenerateModulePrdRequest;
import com.codemind.platform.agent.requirement.service.ModulePrdPersistenceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/projects/{projectCode}/modules/{moduleId}/tasks")
public class ModulePrdTaskController {

    private final ModulePrdPersistenceService modulePrdPersistenceService;

    public ModulePrdTaskController(
            ModulePrdPersistenceService modulePrdPersistenceService
    ) {
        this.modulePrdPersistenceService = modulePrdPersistenceService;
    }

    @PostMapping("/module-prd")
    public Map<String, Object> generateModulePrd(
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody GenerateModulePrdRequest request
    ) {
        return modulePrdPersistenceService.generateAndPersistModulePrd(
                projectCode,
                moduleId,
                request
        );
    }
}