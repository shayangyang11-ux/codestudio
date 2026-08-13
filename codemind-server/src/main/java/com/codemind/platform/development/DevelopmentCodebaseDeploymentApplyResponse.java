package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseDeploymentApplyResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        String codebaseRoot,
        String absoluteCodebaseRoot,
        boolean applied,
        boolean overwriteExisting,
        String deploymentStatus,
        int writtenCount,
        int overwrittenCount,
        int skippedCount,
        List<String> blockers,
        List<String> warnings,
        List<DevelopmentCodebaseDeploymentFileResponse> files,
        String summary,
        LocalDateTime appliedAt
) {
}
