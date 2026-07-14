package com.codemind.platform.task;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AgentTaskPayloadView(
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
        String lastErrorCode,
        String lastErrorMessage,
        String queueName,
        Integer attemptCount,
        Integer maxAttempts,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID promptTemplateId,
        UUID promptTemplateVersionId,
        String promptTemplateKey,
        String promptTemplateVersion,
        String promptTemplateChecksum
) {

    public static AgentTaskPayloadView fromEntity(
            AgentTaskEntity entity
    ) {
        return new AgentTaskPayloadView(
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
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getQueueName(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
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