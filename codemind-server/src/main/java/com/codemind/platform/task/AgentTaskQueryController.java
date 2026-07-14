package com.codemind.platform.task;

import com.codemind.platform.project.ProjectAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AgentTaskQueryController {

    private final AgentTaskService agentTaskService;
    private final ProjectAccessService projectAccessService;

    public AgentTaskQueryController(
            AgentTaskService agentTaskService,
            ProjectAccessService projectAccessService
    ) {
        this.agentTaskService = agentTaskService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/api/agent-tasks/{taskId}")
    public AgentTaskSummaryView getTaskSummary(
            @PathVariable UUID taskId
    ) {
        AgentTaskEntity task = agentTaskService.getById(taskId);
        projectAccessService.resolveAccessibleProjectById(task.getProjectId());

        return AgentTaskSummaryView.fromEntity(task);
    }

    @GetMapping("/api/agent-tasks/{taskId}/payload")
    public AgentTaskPayloadView getTaskPayload(
            @PathVariable UUID taskId
    ) {
        AgentTaskEntity task = agentTaskService.getById(taskId);
        projectAccessService.resolveAccessibleProjectById(task.getProjectId());

        return AgentTaskPayloadView.fromEntity(task);
    }

    @GetMapping("/api/projects/{projectId}/agent-tasks")
    public List<AgentTaskSummaryView> listProjectTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID moduleId
    ) {
        projectAccessService.resolveAccessibleProjectById(projectId);

        if (moduleId != null) {
            return agentTaskService.listRecentByProjectAndModule(projectId, moduleId)
                    .stream()
                    .map(AgentTaskSummaryView::fromEntity)
                    .toList();
        }

        return agentTaskService.listRecentByProject(projectId)
                .stream()
                .map(AgentTaskSummaryView::fromEntity)
                .toList();
    }
}