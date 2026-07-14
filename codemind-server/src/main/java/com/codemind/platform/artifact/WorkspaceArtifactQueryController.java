package com.codemind.platform.artifact;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}")
public class WorkspaceArtifactQueryController {

    private final ArtifactQueryService artifactQueryService;

    public WorkspaceArtifactQueryController(
            ArtifactQueryService artifactQueryService
    ) {
        this.artifactQueryService = artifactQueryService;
    }

    @GetMapping("/artifacts/latest")
    public Map<String, Object> getLatestProjectModuleArtifact(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestParam String artifactType
    ) {
        return artifactQueryService.getLatestProjectModuleArtifact(
                workspaceId,
                projectCode,
                artifactType
        );
    }

    @GetMapping("/modules/{moduleId}/artifacts/current")
    public Map<String, Object> getCurrentModuleArtifact(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam String artifactType
    ) {
        return artifactQueryService.getCurrentModuleArtifact(
                workspaceId,
                projectCode,
                moduleId,
                artifactType
        );
    }
}