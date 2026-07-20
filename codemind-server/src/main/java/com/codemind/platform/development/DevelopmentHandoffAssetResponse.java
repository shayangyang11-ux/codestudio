package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.UUID;

public record DevelopmentHandoffAssetResponse(
        String artifactType,
        String title,
        boolean required,
        String importance,
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
