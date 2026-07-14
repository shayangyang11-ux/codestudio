package com.codemind.platform.workspace.dto;

import java.util.Map;
import java.util.UUID;

public record SaveProjectWorkspaceStateRequest(
        UUID selectedModuleId,
        String selectedArtifactType,
        UUID selectedArtifactId,
        String activeAgentTab,
        Map<String, Object> uiState
) {
}