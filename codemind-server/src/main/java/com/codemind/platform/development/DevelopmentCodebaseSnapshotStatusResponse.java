package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseSnapshotStatusResponse(
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
        String snapshotRoot,
        int snapshotCount,
        DevelopmentCodebaseSnapshotResponse latestSnapshot,
        List<DevelopmentCodebaseSnapshotResponse> snapshots,
        List<String> warnings,
        String summary,
        LocalDateTime generatedAt
) {
}
