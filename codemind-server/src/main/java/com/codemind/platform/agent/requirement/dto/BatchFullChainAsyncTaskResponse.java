package com.codemind.platform.agent.requirement.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BatchFullChainAsyncTaskResponse(
        UUID projectId,
        String projectCode,
        int requestedModuleCount,
        int successModuleCount,
        int failedModuleCount,
        int totalCreatedTaskCount,
        int totalReusedTaskCount,
        int totalSkippedArtifactCount,
        boolean overwriteExisting,
        List<FullChainAsyncTaskResponse> results,
        List<BatchFullChainFailedModuleResponse> failedModules,
        LocalDateTime createdAt
) {

    public record BatchFullChainFailedModuleResponse(
            UUID moduleId,
            String errorCode,
            String errorMessage
    ) {
    }
}