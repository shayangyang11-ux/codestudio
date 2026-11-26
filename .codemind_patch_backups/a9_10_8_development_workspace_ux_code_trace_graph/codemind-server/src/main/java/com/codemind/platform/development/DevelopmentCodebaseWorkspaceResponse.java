package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseWorkspaceResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        String targetStack,
        String codebaseStatus,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        String codebaseRoot,
        String absoluteCodebaseRoot,
        boolean codebaseExists,
        boolean selectedNodeHasCode,
        boolean selectedNodeCanGenerate,
        String generateActionLabel,
        String readinessLevel,
        boolean ready,
        int readinessScore,
        List<String> missingRequiredArtifacts,
        List<String> blockers,
        List<String> warnings,
        List<DevelopmentCodebaseFileTreeItemResponse> fileTree,
        List<DevelopmentCodebaseNodeFileResponse> nodeFiles,
        String defaultFilePath,
        String previewRoute,
        String summary,
        LocalDateTime generatedAt
) {
}
