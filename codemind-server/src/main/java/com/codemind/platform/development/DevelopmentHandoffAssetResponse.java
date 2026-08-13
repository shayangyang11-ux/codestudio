package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.UUID;

public record DevelopmentHandoffAssetResponse(
        String assetKey,
        String artifactType,
        String title,
        boolean required,
        String importance,
        String sourceScope,
        UUID sourceNodeId,
        String sourceNodeKey,
        String sourceNodeName,
        String sourceNodeType,
        boolean inherited,
        boolean exists,
        boolean contentReady,
        boolean readyForDevelopment,
        UUID artifactId,
        Integer versionNo,
        String status,
        Boolean current,
        LocalDateTime updatedAt,
        String statusText
) {
}
