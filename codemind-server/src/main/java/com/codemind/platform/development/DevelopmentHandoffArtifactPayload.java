package com.codemind.platform.development;

import java.util.Map;
import java.util.UUID;

public record DevelopmentHandoffArtifactPayload(
        String assetKey,
        String artifactType,
        String title,
        boolean required,
        String sourceScope,
        UUID sourceNodeId,
        String sourceNodeKey,
        String sourceNodeName,
        String sourceNodeType,
        boolean inherited,
        UUID artifactId,
        Integer versionNo,
        String status,
        String contentMarkdown,
        Map<String, Object> content
) {
}
