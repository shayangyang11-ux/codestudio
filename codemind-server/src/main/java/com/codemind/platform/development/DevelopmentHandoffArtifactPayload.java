package com.codemind.platform.development;

import java.util.Map;
import java.util.UUID;

public record DevelopmentHandoffArtifactPayload(
        String artifactType,
        String title,
        boolean required,
        UUID artifactId,
        Integer versionNo,
        String status,
        String contentMarkdown,
        Map<String, Object> content
) {
}
