package com.codemind.platform.theme;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/theme-profile/application-logs")
public class ProjectThemeProfileRollbackController {

    private final ProjectThemeProfileRollbackService rollbackService;

    public ProjectThemeProfileRollbackController(
            ProjectThemeProfileRollbackService rollbackService
    ) {
        this.rollbackService = rollbackService;
    }

    @PostMapping("/{logId}/rollback")
    public ProjectThemeProfileRollbackResponse rollbackThemeProfile(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID logId
    ) {
        return rollbackService.rollbackToBeforeProfile(
                workspaceId,
                projectCode,
                logId
        );
    }
}
