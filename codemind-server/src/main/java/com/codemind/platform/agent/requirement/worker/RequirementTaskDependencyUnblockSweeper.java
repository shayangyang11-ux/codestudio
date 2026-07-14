package com.codemind.platform.agent.requirement.worker;

import com.codemind.platform.agent.common.worker.AgentTaskWorkerProperties;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerRuntimeMetrics;
import com.codemind.platform.agent.requirement.dependency.RequirementTaskDependencyCheckResult;
import com.codemind.platform.agent.requirement.dependency.RequirementTaskDependencyGuard;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequirementTaskDependencyUnblockSweeper {

    private static final Logger log = LoggerFactory.getLogger(RequirementTaskDependencyUnblockSweeper.class);

    private final AgentTaskService agentTaskService;
    private final RequirementTaskDependencyGuard dependencyGuard;
    private final AgentTaskWorkerProperties properties;
    private final AgentTaskWorkerRuntimeMetrics runtimeMetrics;

    public RequirementTaskDependencyUnblockSweeper(
            AgentTaskService agentTaskService,
            RequirementTaskDependencyGuard dependencyGuard,
            AgentTaskWorkerProperties properties,
            AgentTaskWorkerRuntimeMetrics runtimeMetrics
    ) {
        this.agentTaskService = agentTaskService;
        this.dependencyGuard = dependencyGuard;
        this.properties = properties;
        this.runtimeMetrics = runtimeMetrics;
    }

    @Scheduled(fixedDelayString = "${codemind.agent.worker.dependency-unblock-sweeper-delay-ms:30000}")
    public void sweepBlockedTasks() {
        if (!properties.isEnabled()) {
            return;
        }

        if (!properties.isDependencyAutoUnblockEnabled()) {
            return;
        }

        List<AgentTaskEntity> blockedTasks =
                agentTaskService.listBlockedTasksByQueue(properties.getQueueName());

        if (blockedTasks.isEmpty()) {
            return;
        }

        int maxScan = Math.max(1, properties.getDependencyBlockedScanBatchSize());
        int scanned = 0;

        for (AgentTaskEntity task : blockedTasks) {
            if (scanned >= maxScan) {
                return;
            }

            scanned++;

            RequirementTaskDependencyCheckResult checkResult = dependencyGuard.check(task);

            if (!checkResult.passed()) {
                runtimeMetrics.recordDependencyStillBlocked();
                continue;
            }

            AgentTaskEntity requeuedTask =
                    agentTaskService.markDependencyResolvedAndRequeue(
                            task.getId(),
                            "Blocked task dependencies are now satisfied. Task requeued automatically."
                    );

            runtimeMetrics.recordDependencyUnblocked();

            log.info(
                    "Requirement agent blocked task requeued after dependency resolution. taskId={}, taskType={}, projectId={}, moduleId={}, queueName={}, status={}",
                    requeuedTask.getId(),
                    requeuedTask.getTaskType(),
                    requeuedTask.getProjectId(),
                    requeuedTask.getModuleId(),
                    requeuedTask.getQueueName(),
                    requeuedTask.getStatus()
            );
        }
    }
}