package com.codemind.platform.agent.requirement.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FullChainAsyncTaskResponse(
        UUID projectId,
        String projectCode,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        boolean overwriteExisting,
        List<FullChainTaskItemResponse> createdTasks,
        List<FullChainTaskItemResponse> reusedTasks,
        List<FullChainSkippedArtifactResponse> skippedArtifacts,
        int totalTaskCount,
        LocalDateTime createdAt
) {

    public record FullChainTaskItemResponse(
            UUID taskId,
            UUID projectId,
            UUID moduleId,
            String taskType,
            String artifactType,
            String title,
            String status,
            LocalDateTime createdAt,
            boolean reused
    ) {
    }

    public record FullChainSkippedArtifactResponse(
            UUID projectId,
            UUID moduleId,
            String taskType,
            String artifactType,
            String title,
            String reason
    ) {
    }
}