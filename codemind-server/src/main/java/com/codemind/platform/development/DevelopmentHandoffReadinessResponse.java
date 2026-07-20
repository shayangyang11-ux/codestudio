package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentHandoffReadinessResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        String nodeTypeLabel,
        boolean ready,
        String readinessLevel,
        String readinessText,
        String readinessDescription,
        int score,
        LocalDateTime generatedAt,
        List<String> requiredArtifactTypes,
        List<String> recommendedArtifactTypes,
        List<DevelopmentHandoffAssetResponse> assets,
        List<DevelopmentHandoffIssueResponse> blockers,
        List<DevelopmentHandoffIssueResponse> warnings,
        List<DevelopmentHandoffIssueResponse> passes,
        DevelopmentUiContractSnapshotSummary uiContractSnapshot
) {
}
