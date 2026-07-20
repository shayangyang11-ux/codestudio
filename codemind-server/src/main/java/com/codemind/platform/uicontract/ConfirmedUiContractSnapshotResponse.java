package com.codemind.platform.uicontract;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ConfirmedUiContractSnapshotResponse(
        UUID id,
        UUID projectId,
        UUID moduleId,
        UUID artifactId,
        String artifactType,
        Integer artifactVersionNo,
        String pageId,
        String pageKey,
        String pageName,
        Map<String, Object> uiContract,
        Map<String, Object> themeProfileSnapshot,
        String confirmationNote,
        String status,
        boolean current,
        String confirmedBy,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
