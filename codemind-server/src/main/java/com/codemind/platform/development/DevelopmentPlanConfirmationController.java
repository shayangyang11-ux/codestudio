package com.codemind.platform.development;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/development/handoff/plans")
public class DevelopmentPlanConfirmationController {

    private final ConfirmedDevelopmentPlanSnapshotService snapshotService;

    public DevelopmentPlanConfirmationController(
            ConfirmedDevelopmentPlanSnapshotService snapshotService
    ) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/current-confirmation")
    public DevelopmentPlanSnapshotQueryResponse getCurrentConfirmation(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return snapshotService
                .getCurrent(
                        workspaceId,
                        projectCode,
                        moduleId
                )
                .map(snapshot -> new DevelopmentPlanSnapshotQueryResponse(
                        true,
                        snapshot
                ))
                .orElseGet(() -> new DevelopmentPlanSnapshotQueryResponse(
                        false,
                        null
                ));
    }

    @PostMapping("/confirm")
    public ConfirmedDevelopmentPlanSnapshotResponse confirmPlan(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) ConfirmDevelopmentPlanSnapshotRequest request
    ) {
        return snapshotService.confirm(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}
