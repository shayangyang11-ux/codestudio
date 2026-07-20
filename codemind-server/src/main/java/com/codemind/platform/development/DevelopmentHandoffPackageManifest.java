package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DevelopmentHandoffPackageManifest(
        String schemaVersion,
        String packageType,
        UUID packageId,
        String packageStatus,
        UUID projectId,
        String projectCode,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        LocalDateTime generatedAt,
        String generatedBy,
        UUID confirmedDevelopmentPlanSnapshotId,
        String developmentPlanChecksum,
        UUID confirmedUiContractSnapshotId,
        List<String> includedSections,
        Map<String, Object> sourceArtifactVersions
) {
}
