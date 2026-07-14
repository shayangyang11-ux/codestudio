package com.codemind.platform.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AgentTaskSummaryView(
        UUID id,
        UUID projectId,
        UUID moduleId,
        String taskType,
        String status,
        UUID resultArtifactId,
        String errorCode,
        String errorMessage,
        String lastErrorCode,
        String lastErrorMessage,
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
        boolean hasRequestPayload,
        boolean hasResponsePayload,
        String requirementTextPreview,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID promptTemplateId,
        UUID promptTemplateVersionId,
        String promptTemplateKey,
        String promptTemplateVersion,
        String promptTemplateChecksum
) {

    public static AgentTaskSummaryView fromEntity(
            AgentTaskEntity entity
    ) {
        return new AgentTaskSummaryView(
                entity.getId(),
                entity.getProjectId(),
                entity.getModuleId(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getResultArtifactId(),
                entity.getErrorCode(),
                truncate(entity.getErrorMessage(), 300),
                entity.getLastErrorCode(),
                truncate(entity.getLastErrorMessage(), 300),
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
                entity.getRequestPayload() != null && !entity.getRequestPayload().isEmpty(),
                entity.getResponsePayload() != null && !entity.getResponsePayload().isEmpty(),
                extractRequirementTextPreview(entity.getRequestPayload()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getPromptTemplateId(),
                entity.getPromptTemplateVersionId(),
                entity.getPromptTemplateKey(),
                entity.getPromptTemplateVersion(),
                entity.getPromptTemplateChecksum()
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