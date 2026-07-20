package com.codemind.platform.uicontract;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/ui-contract-snapshots")
public class UiContractSnapshotController {

    private final ConfirmedUiContractSnapshotService snapshotService;

    public UiContractSnapshotController(
            ConfirmedUiContractSnapshotService snapshotService
    ) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/current")
    public UiContractSnapshotQueryResponse getCurrentSnapshot(
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
                .map(snapshot -> new UiContractSnapshotQueryResponse(
                        true,
                        snapshot
                ))
                .orElseGet(() -> new UiContractSnapshotQueryResponse(
                        false,
                        null
                ));
    }

    @PostMapping("/confirm")
    public ConfirmedUiContractSnapshotResponse confirmSnapshot(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) ConfirmUiContractSnapshotRequest request
    ) {
        return snapshotService.confirm(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}
