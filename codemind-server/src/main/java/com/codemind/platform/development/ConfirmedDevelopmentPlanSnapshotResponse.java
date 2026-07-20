package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ConfirmedDevelopmentPlanSnapshotResponse(
        UUID id,
        UUID projectId,
        UUID moduleId,
        String planChecksum,
        String planStatus,
        Map<String, Object> plan,
        Map<String, Object> readinessSnapshot,
        Map<String, Object> handoffContextSnapshot,
        Map<String, Object> sourceArtifactVersions,
        UUID confirmedUiContractSnapshotId,
        String confirmationNote,
        String status,
        boolean current,
        String confirmedBy,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
