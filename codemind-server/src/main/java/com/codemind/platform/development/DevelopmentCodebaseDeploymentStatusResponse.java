package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseDeploymentStatusResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        String codebaseRoot,
        String absoluteCodebaseRoot,
        boolean codebaseExists,
        boolean packageJsonExists,
        boolean deployable,
        String deploymentStatus,
        int plannedFileCount,
        int existingFileCount,
        int missingFileCount,
        int outdatedFileCount,
        List<String> blockers,
        List<String> warnings,
        List<String> commands,
        List<DevelopmentCodebaseDeploymentFileResponse> files,
        String summary,
        LocalDateTime generatedAt
) {
}
