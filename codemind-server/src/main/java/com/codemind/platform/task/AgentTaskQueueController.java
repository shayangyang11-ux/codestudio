package com.codemind.platform.task;

import com.codemind.platform.agent.common.worker.AgentTaskWorkerIdentity;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerProperties;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerRuntimeMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AgentTaskQueueController {

    private final AgentTaskService agentTaskService;
    private final AgentTaskWorkerIdentity workerIdentity;
    private final AgentTaskWorkerProperties workerProperties;
    private final AgentTaskWorkerRuntimeMetrics runtimeMetrics;

    public AgentTaskQueueController(
            AgentTaskService agentTaskService,
            AgentTaskWorkerIdentity workerIdentity,
            AgentTaskWorkerProperties workerProperties,
            AgentTaskWorkerRuntimeMetrics runtimeMetrics
    ) {
        this.agentTaskService = agentTaskService;
        this.workerIdentity = workerIdentity;
        this.workerProperties = workerProperties;
        this.runtimeMetrics = runtimeMetrics;
    }

    @GetMapping("/api/agent-task-queues/{queueName}/status")
    public AgentTaskQueueStatusView getQueueStatus(
            @PathVariable String queueName
    ) {
        Map<String, Long> statusCounts =
                agentTaskService.countTasksByStatusInQueue(queueName);

        long runnableCount =
                agentTaskService.countRunnableTasksInQueue(queueName);

        long expiredRunningCount =
                agentTaskService.countExpiredRunningTasksInQueue(queueName);

        AgentTaskWorkerRuntimeMetricsView localWorkerMetrics =
                AgentTaskWorkerRuntimeMetricsView.from(
                        workerIdentity,
                        workerProperties,
                        runtimeMetrics
                );

        List<AgentTaskQueueTaskSummaryView> recentTasks = agentTaskService.listRecentByQueue(queueName)
                .stream()
                .map(AgentTaskQueueTaskSummaryView::fromEntity)
                .toList();

        return AgentTaskQueueStatusView.of(
                queueName,
                statusCounts,
                runnableCount,
                expiredRunningCount,
                localWorkerMetrics,
                recentTasks
        );
    }

    @GetMapping("/api/agent-task-queues/default/status")
    public AgentTaskQueueStatusView getDefaultQueueStatus() {
        return getQueueStatus(AgentTaskService.DEFAULT_REQUIREMENT_QUEUE);
    }
}