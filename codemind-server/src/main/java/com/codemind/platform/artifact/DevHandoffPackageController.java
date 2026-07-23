package com.codemind.platform.artifact;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/artifacts/dev-handoff-package")
public class DevHandoffPackageController {

    private final DevHandoffPackageService devHandoffPackageService;

    public DevHandoffPackageController(
            DevHandoffPackageService devHandoffPackageService
    ) {
        this.devHandoffPackageService = devHandoffPackageService;
    }

    @PostMapping
    public DevHandoffPackageResponse generateDevHandoffPackage(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) GenerateDevHandoffPackageRequest request
    ) {
        return devHandoffPackageService.generate(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}
