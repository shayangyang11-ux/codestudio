package com.codemind.platform.theme;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile/application-logs")
public class ProjectThemeProfileApplicationLogController {

    private final ProjectThemeProfileApplicationLogService applicationLogService;

    public ProjectThemeProfileApplicationLogController(
            ProjectThemeProfileApplicationLogService applicationLogService
    ) {
        this.applicationLogService = applicationLogService;
    }

    @PostMapping
    public ProjectThemeProfileApplicationLogResponse createApplicationLog(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody ProjectThemeProfileApplicationLogRequest request
    ) {
        return applicationLogService.create(
                workspaceId,
                projectCode,
                request
        );
    }

    @GetMapping
    public List<ProjectThemeProfileApplicationLogResponse> listApplicationLogs(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return applicationLogService.list(
                workspaceId,
                projectCode,
                limit
        );
    }
}
