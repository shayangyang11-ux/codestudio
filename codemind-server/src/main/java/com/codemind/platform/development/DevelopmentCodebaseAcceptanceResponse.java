package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseAcceptanceResponse(
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
        boolean readyToRun,
        boolean executed,
        boolean deliverable,
        String acceptanceStatus,
        int passedCount,
        int warningCount,
        int failedCount,
        int skippedCount,
        long durationMillis,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String frontendUrl,
        String backendUrl,
        String healthUrl,
        List<DevelopmentCodebaseAcceptanceItemResponse> items,
        List<String> blockers,
        List<String> warnings,
        List<String> nextActions,
        String summary
) {
}
