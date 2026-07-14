package com.codemind.platform.task;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectAgentTaskPageView(
        List<ProjectAgentTaskPageItemView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String status,
        String taskType,
        String moduleId,
        String createdBy,
        String sort,
        LocalDateTime generatedAt
) {

    public static ProjectAgentTaskPageView of(
            List<ProjectAgentTaskPageItemView> content,
            int page,
            int size,
            long totalElements,
            String status,
            String taskType,
            String moduleId,
            String createdBy,
            String sort
    ) {
        int safeSize = Math.max(size, 1);
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        return new ProjectAgentTaskPageView(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page <= 0,
                page + 1 >= totalPages,
                status,
                taskType,
                moduleId,
                createdBy,
                sort,
                LocalDateTime.now()
        );
    }

    public record ProjectAgentTaskPageItemView(
            String id,
            String projectId,
            String moduleId,
            String taskType,
            String taskTitle,
            String artifactType,
            String artifactTitle,
            String status,
            String statusText,
            String resultArtifactId,
            String errorCode,
            String errorMessage,
            String lastErrorCode,
            String lastErrorMessage,
            String displayMessage,
            String queueName,
            Integer priority,
            Integer attemptCount,
            Integer maxAttempts,
            LocalDateTime nextRunAt,
            String lockedBy,
            LocalDateTime lockedUntil,
            LocalDateTime heartbeatAt,
            String createdBy,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long durationMs,
            Boolean hasRequestPayload,
            Boolean hasResponsePayload,
            String requirementTextPreview,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}