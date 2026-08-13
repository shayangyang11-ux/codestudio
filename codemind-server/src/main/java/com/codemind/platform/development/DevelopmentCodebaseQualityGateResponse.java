package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseQualityGateResponse(
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
        String qualityStatus,
        int score,
        boolean deliverableCandidate,
        int passedCount,
        int warningCount,
        int failedCount,
        int requiredFailedCount,
        List<DevelopmentCodebaseQualityItemResponse> items,
        List<String> blockers,
        List<String> warnings,
        List<String> nextActions,
        String summary,
        LocalDateTime generatedAt
) {
}
