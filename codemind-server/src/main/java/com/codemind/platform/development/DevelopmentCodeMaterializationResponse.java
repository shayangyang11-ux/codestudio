package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodeMaterializationResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String targetStack,
        String materializationStatus,
        LocalDateTime materializedAt,
        String workspaceRoot,
        String generationRoot,
        String absoluteGenerationRoot,
        boolean allowOverwrite,
        int writtenCount,
        int skippedCount,
        int addedCount,
        int modifiedCount,
        int unchangedCount,
        int conflictCount,
        List<String> warnings,
        List<DevelopmentCodeMaterializedFileResponse> files,
        DevelopmentCodeProjectResponse codeProject,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        DevelopmentCodebaseSnapshotResponse snapshot,
        DevelopmentCodebaseSafetyCheckResponse safetyCheck
) {
}
