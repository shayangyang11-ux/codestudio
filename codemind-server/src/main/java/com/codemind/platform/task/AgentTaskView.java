package com.codemind.platform.task;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AgentTaskView(
        UUID id,
        UUID projectId,
        UUID moduleId,
        String taskType,
        String status,
        Map<String, Object> requestPayload,
        Map<String, Object> responsePayload,
        UUID resultArtifactId,
        String errorCode,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String queueName,
        Integer priority,
        Integer attemptCount,
        Integer maxAttempts,
        LocalDateTime nextRunAt,
        String lockedBy,
        LocalDateTime lockedUntil,
        LocalDateTime heartbeatAt,
        String lastErrorCode,
        String lastErrorMessage,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID promptTemplateId,
        UUID promptTemplateVersionId,
        String promptTemplateKey,
        String promptTemplateVersion,
        String promptTemplateChecksum
) {

    public static AgentTaskView fromEntity(AgentTaskEntity entity) {
        return new AgentTaskView(
                entity.getId(),
                entity.getProjectId(),
                entity.getModuleId(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getRequestPayload(),
                entity.getResponsePayload(),
                entity.getResultArtifactId(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getQueueName(),
                entity.getPriority(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getNextRunAt(),
                entity.getLockedBy(),
                entity.getLockedUntil(),
                entity.getHeartbeatAt(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getPromptTemplateId(),
                entity.getPromptTemplateVersionId(),
                entity.getPromptTemplateKey(),
                entity.getPromptTemplateVersion(),
                entity.getPromptTemplateChecksum()
        );
    }
}