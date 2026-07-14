package com.codemind.platform.artifact;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/projects/{projectCode}/modules/{moduleId}/artifacts")
public class ArtifactQueryController {

    private final ArtifactQueryService artifactQueryService;

    public ArtifactQueryController(
            ArtifactQueryService artifactQueryService
    ) {
        this.artifactQueryService = artifactQueryService;
    }

    @GetMapping("/current")
    public Map<String, Object> getCurrentModuleArtifact(
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam String artifactType
    ) {
        return artifactQueryService.getCurrentModuleArtifactByProjectCode(
                projectCode,
                moduleId,
                artifactType
        );
    }
}