package com.codemind.platform.task;

import com.codemind.platform.agent.common.worker.AgentTaskWorkerIdentity;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerProperties;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerRuntimeMetrics;

import java.time.LocalDateTime;

public record AgentTaskWorkerRuntimeMetricsView(
        String workerId,
        boolean enabled,
        String queueName,
        int maxAcquirePerPoll,
        long lockSeconds,
        long heartbeatSeconds,
        boolean idleBackoffEnabled,
        int idleBackoffThreshold,
        int idleBackoffMaxSkip,
        long pollCount,
        long skippedPollCount,
        long emptyPollCount,
        long acquiredTaskCount,
        long dispatchRejectedCount,
        long executionSuccessCount,
        long executionFailureCount,
        long retryScheduledCount,
        long moduleSerialDeferredCount,
        long dependencyUnblockedCount,
        long dependencyStillBlockedCount,
        LocalDateTime lastPollAt,
        LocalDateTime lastAcquireAt,
        LocalDateTime lastSuccessAt,
        LocalDateTime lastFailureAt
) {

    public static AgentTaskWorkerRuntimeMetricsView from(
            AgentTaskWorkerIdentity workerIdentity,
            AgentTaskWorkerProperties properties,
            AgentTaskWorkerRuntimeMetrics metrics
    ) {
        return new AgentTaskWorkerRuntimeMetricsView(
                workerIdentity.workerId(),
                properties.isEnabled(),
                properties.getQueueName(),
                properties.getMaxAcquirePerPoll(),
                properties.getLockSeconds(),
                properties.getHeartbeatSeconds(),
                properties.isIdleBackoffEnabled(),
                properties.getIdleBackoffThreshold(),
                properties.getIdleBackoffMaxSkip(),
                metrics.getPollCount(),
                metrics.getSkippedPollCount(),
                metrics.getEmptyPollCount(),
                metrics.getAcquiredTaskCount(),
                metrics.getDispatchRejectedCount(),
                metrics.getExecutionSuccessCount(),
                metrics.getExecutionFailureCount(),
                metrics.getRetryScheduledCount(),
                metrics.getModuleSerialDeferredCount(),
                metrics.getDependencyUnblockedCount(),
                metrics.getDependencyStillBlockedCount(),
                metrics.getLastPollAt(),
                metrics.getLastAcquireAt(),
                metrics.getLastSuccessAt(),
                metrics.getLastFailureAt()
        );
    }
}