package com.codemind.platform.workspace.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ProjectWorkspaceStateResponse(
        Boolean exists,
        UUID workspaceId,
        UUID projectId,
        UUID userId,
        UUID selectedModuleId,
        String selectedArtifactType,
        UUID selectedArtifactId,
        String activeAgentTab,
        Map<String, Object> uiState,
        LocalDateTime updatedAt
) {
}