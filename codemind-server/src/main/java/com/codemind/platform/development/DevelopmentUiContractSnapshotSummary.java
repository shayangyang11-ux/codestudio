package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.UUID;

public record DevelopmentUiContractSnapshotSummary(
        boolean exists,
        boolean matchesCurrentArtifact,
        UUID snapshotId,
        UUID artifactId,
        Integer artifactVersionNo,
        String pageId,
        String pageKey,
        String pageName,
        String status,
        String confirmedBy,
        LocalDateTime confirmedAt
) {
}
