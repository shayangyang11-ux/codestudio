package com.codemind.platform.task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AgentTaskQueueStatusView(
        String queueName,
        Map<String, Long> statusCounts,
        long queuedCount,
        long runningCount,
        long retryingCount,
        long blockedCount,
        long successCount,
        long failedCount,
        long timeoutCount,
        long cancelledCount,
        long partialSuccessCount,
        long activeCount,
        long terminalCount,
        long runnableCount,
        long expiredRunningCount,
        AgentTaskWorkerRuntimeMetricsView localWorkerMetrics,
        List<AgentTaskQueueTaskSummaryView> recentTasks,
        LocalDateTime generatedAt
) {

    public static AgentTaskQueueStatusView of(
            String queueName,
            Map<String, Long> statusCounts,
            long runnableCount,
            long expiredRunningCount,
            AgentTaskWorkerRuntimeMetricsView localWorkerMetrics,
            List<AgentTaskQueueTaskSummaryView> recentTasks
    ) {
        long queuedCount = count(statusCounts, TaskStatus.QUEUED);
        long runningCount = count(statusCounts, TaskStatus.RUNNING);
        long retryingCount = count(statusCounts, TaskStatus.RETRYING);
        long blockedCount = count(statusCounts, TaskStatus.BLOCKED);
        long successCount = count(statusCounts, TaskStatus.SUCCESS);
        long failedCount = count(statusCounts, TaskStatus.FAILED);
        long timeoutCount = count(statusCounts, TaskStatus.TIMEOUT);
        long cancelledCount = count(statusCounts, TaskStatus.CANCELLED);
        long partialSuccessCount = count(statusCounts, TaskStatus.PARTIAL_SUCCESS);

        long activeCount = queuedCount + runningCount + retryingCount + blockedCount;
        long terminalCount = successCount + failedCount + timeoutCount + cancelledCount + partialSuccessCount;

        return new AgentTaskQueueStatusView(
                queueName,
                statusCounts,
                queuedCount,
                runningCount,
                retryingCount,
                blockedCount,
                successCount,
                failedCount,
                timeoutCount,
                cancelledCount,
                partialSuccessCount,
                activeCount,
                terminalCount,
                runnableCount,
                expiredRunningCount,
                localWorkerMetrics,
                recentTasks,
                LocalDateTime.now()
        );
    }

    private static long count(
            Map<String, Long> statusCounts,
            TaskStatus status
    ) {
        return statusCounts.getOrDefault(status.name(), 0L);
    }
}