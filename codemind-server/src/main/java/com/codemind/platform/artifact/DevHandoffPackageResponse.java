package com.codemind.platform.artifact;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DevHandoffPackageResponse(
        UUID artifactId,
        UUID projectId,
        UUID moduleId,
        String artifactType,
        Integer versionNo,
        String title,
        String status,
        Boolean current,
        Map<String, Object> content,
        String contentMarkdown,
        List<Map<String, Object>> sourceArtifacts,
        List<String> blockers,
        List<String> warnings,
        String message
) {
}
