package com.codemind.platform.artifact;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ManualArtifactEditResponse(
        UUID artifactId,
        UUID projectId,
        UUID moduleId,
        String artifactType,
        Integer versionNo,
        String title,
        String status,
        Boolean current,
        Map<String, Object> content,
        String contentMarkdown,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> downstreamArtifactTypes,
        Integer downstreamMarkedCount,
        String message
) {
}
