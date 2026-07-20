package com.codemind.platform.development;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/development/handoff/phase4")
public class DevelopmentPhase4AcceptanceController {

    private final DevelopmentPhase4AcceptanceService acceptanceService;

    public DevelopmentPhase4AcceptanceController(
            DevelopmentPhase4AcceptanceService acceptanceService
    ) {
        this.acceptanceService = acceptanceService;
    }

    @GetMapping("/acceptance")
    public DevelopmentPhase4AcceptanceResponse getAcceptance(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return acceptanceService.getAcceptance(
                workspaceId,
                projectCode,
                moduleId
        );
    }
}
