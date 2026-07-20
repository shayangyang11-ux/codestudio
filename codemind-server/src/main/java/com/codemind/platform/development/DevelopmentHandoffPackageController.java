package com.codemind.platform.development;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/development/handoff/packages")
public class DevelopmentHandoffPackageController {

    private final DevelopmentHandoffPackageService handoffPackageService;

    public DevelopmentHandoffPackageController(
            DevelopmentHandoffPackageService handoffPackageService
    ) {
        this.handoffPackageService = handoffPackageService;
    }

    @PostMapping("/generate")
    public DevelopmentHandoffPackageResponse generatePackage(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return handoffPackageService.generatePackage(
                workspaceId,
                projectCode,
                moduleId
        );
    }
}
