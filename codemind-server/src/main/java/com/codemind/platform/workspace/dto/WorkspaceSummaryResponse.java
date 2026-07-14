package com.codemind.platform.workspace.dto;

import java.util.UUID;

public record WorkspaceSummaryResponse(
        UUID workspaceId,
        String workspaceCode,
        String workspaceName,
        String workspaceType,
        String memberRole,
        String status
) {
}