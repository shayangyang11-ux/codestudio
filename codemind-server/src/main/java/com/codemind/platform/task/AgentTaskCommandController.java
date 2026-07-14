package com.codemind.platform.task;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.project.ProjectAccessService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AgentTaskCommandController {

    private final AgentTaskService agentTaskService;
    private final ProjectAccessService projectAccessService;
    private final CurrentUserProvider currentUserProvider;

    public AgentTaskCommandController(
            AgentTaskService agentTaskService,
            ProjectAccessService projectAccessService,
            CurrentUserProvider currentUserProvider
    ) {
        this.agentTaskService = agentTaskService;
        this.projectAccessService = projectAccessService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/agent-tasks/{taskId}/cancel")
    public AgentTaskView cancelTask(
            @PathVariable UUID taskId,
            @RequestBody(required = false) AgentTaskCancelRequest request
    ) {
        AgentTaskEntity task = agentTaskService.getById(taskId);
        projectAccessService.resolveAccessibleProjectById(task.getProjectId());

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        AgentTaskEntity updatedTask = agentTaskService.cancelTask(
                taskId,
                currentUser.username(),
                request == null ? null : request.reason()
        );

        return AgentTaskView.fromEntity(updatedTask);
    }

    @PostMapping("/api/agent-tasks/{taskId}/retry")
    public AgentTaskView retryTask(
            @PathVariable UUID taskId,
            @RequestBody(required = false) AgentTaskRetryRequest request
    ) {
        AgentTaskEntity task = agentTaskService.getById(taskId);
        projectAccessService.resolveAccessibleProjectById(task.getProjectId());

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        AgentTaskEntity updatedTask = agentTaskService.retryTask(
                taskId,
                currentUser.username(),
                request == null ? null : request.reason(),
                request == null ? null : request.maxAttempts()
        );

        return AgentTaskView.fromEntity(updatedTask);
    }

    @PostMapping("/api/agent-tasks/{taskId}/archive")
    public AgentTaskSummaryView archiveTask(
            @PathVariable UUID taskId,
            @RequestBody(required = false) AgentTaskArchiveRequest request
    ) {
        AgentTaskEntity task = agentTaskService.getById(taskId);
        projectAccessService.resolveAccessibleProjectById(task.getProjectId());

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        AgentTaskEntity updatedTask = agentTaskService.archiveTask(
                taskId,
                currentUser.username(),
                request == null ? null : request.reason()
        );

        return AgentTaskSummaryView.fromEntity(updatedTask);
    }

}