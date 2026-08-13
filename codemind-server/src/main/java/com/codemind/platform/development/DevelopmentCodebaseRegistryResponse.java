package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.UUID;

public record DevelopmentCodebaseRegistryResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String projectCode,
        String targetStack,
        String storageType,
        String storageRootKey,
        String relativeRootPath,
        String platformRootPath,
        String manifestRelativePath,
        String manifestPlatformPath,
        String absoluteRootPath,
        String status,
        boolean registered,
        boolean physicalExists,
        LocalDateTime lastGeneratedAt,
        LocalDateTime lastMaterializedAt,
        String lastRuntimeStatus,
        Integer lastFrontendPort,
        Integer lastBackendPort,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
