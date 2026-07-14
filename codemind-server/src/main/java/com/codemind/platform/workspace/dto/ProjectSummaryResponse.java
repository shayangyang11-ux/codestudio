package com.codemind.platform.workspace.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectSummaryResponse(
        UUID projectId,
        String projectCode,
        String projectName,
        String description,
        String status,
        UUID workspaceId,
        String workspaceCode,
        String visibility,
        Long moduleCount,
        Long artifactCount,
        LocalDateTime latestArtifactAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}