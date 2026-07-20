package com.codemind.platform.theme;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
public class ProjectStyleAssistantController {

    private final ProjectThemeProfileService projectThemeProfileService;
    private final ProjectStyleAssistantService styleAssistantService;

    public ProjectStyleAssistantController(
            ProjectThemeProfileService projectThemeProfileService,
            ProjectStyleAssistantService styleAssistantService
    ) {
        this.projectThemeProfileService = projectThemeProfileService;
        this.styleAssistantService = styleAssistantService;
    }

    @GetMapping({
            "/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile/style-assistant/ping",
            "/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile/style-assistant/ping/"
    })
    public Map<String, Object> ping(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("feature", "PROJECT_STYLE_ASSISTANT");
        result.put("workspaceId", workspaceId);
        result.put("projectCode", projectCode);
        return result;
    }

    @PostMapping({
            "/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile/style-assistant/analyze",
            "/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile/style-assistant/analyze/"
    })
    public ProjectStyleAssistantResponse analyze(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody ProjectStyleAssistantRequest request
    ) {
        ProjectThemeProfileResponse currentProfile =
                projectThemeProfileService.getOrCreateThemeProfile(
                        workspaceId,
                        projectCode
                );

        return styleAssistantService.analyze(
                workspaceId,
                projectCode,
                request,
                currentProfile
        );
    }
}
