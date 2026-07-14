package com.codemind.platform.task;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ProjectAgentTaskStatusSummaryView(
        UUID projectId,
        long total,
        long pending,
        long queued,
        long running,
        long retrying,
        long blocked,
        long success,
        long partialSuccess,
        long failed,
        long timeout,
        long cancelled,
        long active,
        long abnormal,
        long terminal,
        Map<String, Long> statusCounts,
        LocalDateTime generatedAt
) {

    public static ProjectAgentTaskStatusSummaryView of(
            UUID projectId,
            Map<String, Long> statusCounts
    ) {
        long pending = count(statusCounts, "PENDING");
        long queued = count(statusCounts, "QUEUED");
        long running = count(statusCounts, "RUNNING");
        long retrying = count(statusCounts, "RETRYING");
        long blocked = count(statusCounts, "BLOCKED");
        long success = count(statusCounts, "SUCCESS");
        long partialSuccess = count(statusCounts, "PARTIAL_SUCCESS");
        long failed = count(statusCounts, "FAILED");
        long timeout = count(statusCounts, "TIMEOUT");
        long cancelled = count(statusCounts, "CANCELLED");

        long total = statusCounts.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        long active = pending + queued + running + retrying + blocked;
        long abnormal = blocked + failed + timeout;
        long terminal = success + partialSuccess + failed + timeout + cancelled;

        return new ProjectAgentTaskStatusSummaryView(
                projectId,
                total,
                pending,
                queued,
                running,
                retrying,
                blocked,
                success,
                partialSuccess,
                failed,
                timeout,
                cancelled,
                active,
                abnormal,
                terminal,
                statusCounts,
                LocalDateTime.now()
        );
    }

    private static long count(
            Map<String, Long> statusCounts,
            String status
    ) {
        return statusCounts.getOrDefault(status, 0L);
    }
}