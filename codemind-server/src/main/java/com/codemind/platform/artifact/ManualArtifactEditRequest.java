package com.codemind.platform.artifact;

import java.util.Map;

public record ManualArtifactEditRequest(
        String artifactId,
        String artifactType,
        String contentMarkdown,
        String reason,
        Map<String, Object> content
) {
}
