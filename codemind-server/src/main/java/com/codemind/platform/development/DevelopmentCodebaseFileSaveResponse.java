package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseFileSaveResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        String filePath,
        String absolutePath,
        String language,
        long bytes,
        String relationship,
        String fileType,
        String status,
        List<String> affectedNodeIds,
        List<String> warnings,
        String summary,
        LocalDateTime savedAt
) {
}
