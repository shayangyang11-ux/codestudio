package com.codemind.platform.theme;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile")
public class ProjectThemeProfileController {

    private final ProjectThemeProfileService projectThemeProfileService;

    public ProjectThemeProfileController(
            ProjectThemeProfileService projectThemeProfileService
    ) {
        this.projectThemeProfileService = projectThemeProfileService;
    }

    @GetMapping
    public ProjectThemeProfileResponse getThemeProfile(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode
    ) {
        return projectThemeProfileService.getOrCreateThemeProfile(
                workspaceId,
                projectCode
        );
    }

    @PutMapping
    public ProjectThemeProfileResponse updateThemeProfile(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody UpdateProjectThemeProfileRequest request
    ) {
        return projectThemeProfileService.updateThemeProfile(
                workspaceId,
                projectCode,
                request
        );
    }
}
