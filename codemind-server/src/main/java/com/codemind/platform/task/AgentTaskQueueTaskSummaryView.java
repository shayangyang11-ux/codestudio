package com.codemind.platform.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AgentTaskQueueTaskSummaryView(
        UUID id,
        UUID projectId,
        UUID moduleId,
        String taskType,
        String status,
        UUID resultArtifactId,
        String errorCode,
        String errorMessage,
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
        String requirementTextPreview,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AgentTaskQueueTaskSummaryView fromEntity(
            AgentTaskEntity entity
    ) {
        return new AgentTaskQueueTaskSummaryView(
                entity.getId(),
                entity.getProjectId(),
                entity.getModuleId(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getResultArtifactId(),
                entity.getErrorCode(),
                truncate(entity.getErrorMessage(), 300),
                entity.getQueueName(),
                entity.getPriority(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getNextRunAt(),
                entity.getLockedBy(),
                entity.getLockedUntil(),
                entity.getHeartbeatAt(),
                entity.getCreatedBy(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                durationMs(entity.getStartedAt(), entity.getFinishedAt()),
                extractRequirementTextPreview(entity.getRequestPayload()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String extractRequirementTextPreview(
            Map<String, Object> requestPayload
    ) {
        if (requestPayload == null || requestPayload.isEmpty()) {
            return null;
        }

        Object value = requestPayload.get("requirement_text");
        if (value == null) {
            return null;
        }

        return truncate(String.valueOf(value), 160);
    }

    private static Long durationMs(
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }

        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }
}