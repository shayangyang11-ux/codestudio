package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.UUID;

public record DevelopmentHandoffPackageAssetItem(
        String artifactType,
        String title,
        UUID artifactId,
        Integer versionNo,
        String status,
        Boolean current,
        String checksum,
        LocalDateTime updatedAt
) {
}
