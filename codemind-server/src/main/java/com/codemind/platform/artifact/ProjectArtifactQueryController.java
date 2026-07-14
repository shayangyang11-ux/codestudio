package com.codemind.platform.artifact;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/projects/{projectCode}/artifacts")
public class ProjectArtifactQueryController {

    private final ArtifactQueryService artifactQueryService;

    public ProjectArtifactQueryController(
            ArtifactQueryService artifactQueryService
    ) {
        this.artifactQueryService = artifactQueryService;
    }

    @GetMapping("/latest")
    public Map<String, Object> getLatestProjectModuleArtifact(
            @PathVariable String projectCode,
            @RequestParam String artifactType
    ) {
        return artifactQueryService.getLatestProjectModuleArtifactByProjectCode(
                projectCode,
                artifactType
        );
    }
}