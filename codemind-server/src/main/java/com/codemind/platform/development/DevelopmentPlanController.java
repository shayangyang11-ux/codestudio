package com.codemind.platform.development;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/development/handoff/plans")
public class DevelopmentPlanController {

    private final DevelopmentPlanService developmentPlanService;

    public DevelopmentPlanController(
            DevelopmentPlanService developmentPlanService
    ) {
        this.developmentPlanService = developmentPlanService;
    }

    @PostMapping("/generate")
    public DevelopmentPlanResponse generatePlan(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) GenerateDevelopmentPlanRequest request
    ) {
        return developmentPlanService.generatePlan(
                workspaceId,
                projectCode,
                moduleId,
                request == null ? new GenerateDevelopmentPlanRequest(null, null, null) : request
        );
    }
}
