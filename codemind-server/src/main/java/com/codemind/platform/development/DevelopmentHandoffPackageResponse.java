package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DevelopmentHandoffPackageResponse(
        UUID packageId,
        String packageStatus,
        String packageChecksum,
        LocalDateTime generatedAt,
        DevelopmentHandoffPackageManifest manifest,
        Map<String, Object> confirmedDevelopmentPlan,
        Map<String, Object> readinessSnapshot,
        Map<String, Object> handoffContextSnapshot,
        Map<String, Object> confirmedUiContractSnapshot,
        Map<String, Object> sourceArtifactVersions,
        List<DevelopmentHandoffPackageAssetItem> assets,
        String handoffReadmeMarkdown
) {
}
