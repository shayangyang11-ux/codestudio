package com.codemind.platform.development;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/development/handoff")
public class DevelopmentHandoffGateController {

    private final DevelopmentHandoffGateService developmentHandoffGateService;

    public DevelopmentHandoffGateController(
            DevelopmentHandoffGateService developmentHandoffGateService
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
    }

    @GetMapping("/readiness")
    public DevelopmentHandoffReadinessResponse getReadiness(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );
    }

    @GetMapping("/context")
    public DevelopmentHandoffContextResponse getContext(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return developmentHandoffGateService.getContext(
                workspaceId,
                projectCode,
                moduleId
        );
    }
}
