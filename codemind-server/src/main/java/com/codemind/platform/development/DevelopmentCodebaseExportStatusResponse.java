package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseExportStatusResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        String exportStatus,
        boolean exportable,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        String codebaseRoot,
        String absoluteCodebaseRoot,
        boolean codebaseExists,
        boolean registered,
        boolean manifestExists,
        int fileCount,
        long totalBytes,
        String estimatedZipName,
        List<String> excludedDirectories,
        List<String> blockers,
        List<String> warnings,
        String summary,
        LocalDateTime generatedAt
) {
}
