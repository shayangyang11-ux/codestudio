package com.codemind.platform.task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectAgentTaskMatrixView(
        UUID projectId,
        List<ProjectModuleTaskMatrixView> modules,
        LocalDateTime generatedAt
) {

    public static ProjectAgentTaskMatrixView of(
            UUID projectId,
            List<ProjectModuleTaskMatrixView> modules
    ) {
        return new ProjectAgentTaskMatrixView(
                projectId,
                modules,
                LocalDateTime.now()
        );
    }

    public record ProjectModuleTaskMatrixView(
            UUID moduleId,
            UUID parentId,
            String moduleKey,
            String moduleName,
            String moduleType,
            String modulePath,
            Integer levelNo,
            Integer sortOrder,
            String status,
            List<ProjectModuleTaskMatrixItemView> items
    ) {
    }

    public record ProjectModuleTaskMatrixItemView(
            String artifactType,
            String artifactTitle,
            String taskType,
            String taskTitle,
            Integer sortOrder,
            boolean artifactExists,
            UUID artifactId,
            Integer artifactVersionNo,
            String artifactStatus,
            LocalDateTime artifactUpdatedAt,
            UUID latestTaskId,
            String latestTaskStatus,
            String statusText,
            String displayMessage,
            String errorCode,
            String lastErrorCode,
            Integer attemptCount,
            Integer maxAttempts,
            LocalDateTime taskCreatedAt,
            LocalDateTime taskUpdatedAt,
            LocalDateTime taskStartedAt,
            LocalDateTime taskFinishedAt
    ) {
    }
}