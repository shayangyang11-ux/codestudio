package com.codemind.platform.task;

import com.codemind.platform.project.ProjectAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ProjectAgentTaskCenterController {

    private final ProjectAgentTaskQueryService projectAgentTaskQueryService;
    private final ProjectAccessService projectAccessService;

    public ProjectAgentTaskCenterController(
            ProjectAgentTaskQueryService projectAgentTaskQueryService,
            ProjectAccessService projectAccessService
    ) {
        this.projectAgentTaskQueryService = projectAgentTaskQueryService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/api/projects/{projectId}/agent-tasks/summary")
    public ProjectAgentTaskStatusSummaryView getProjectTaskSummary(
            @PathVariable UUID projectId
    ) {
        projectAccessService.resolveAccessibleProjectById(projectId);

        return projectAgentTaskQueryService.getProjectTaskSummary(projectId);
    }

    @GetMapping("/api/projects/{projectId}/agent-tasks/page")
    public ProjectAgentTaskPageView searchProjectTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAtDesc") String sort
    ) {
        projectAccessService.resolveAccessibleProjectById(projectId);

        return projectAgentTaskQueryService.searchProjectTasks(
                projectId,
                status,
                taskType,
                moduleId,
                createdBy,
                page,
                size,
                sort
        );
    }

    @GetMapping("/api/projects/{projectId}/agent-tasks/module-matrix")
    public ProjectAgentTaskMatrixView getProjectModuleTaskMatrix(
            @PathVariable UUID projectId
    ) {
        projectAccessService.resolveAccessibleProjectById(projectId);

        return projectAgentTaskQueryService.getProjectModuleTaskMatrix(projectId);
    }
}