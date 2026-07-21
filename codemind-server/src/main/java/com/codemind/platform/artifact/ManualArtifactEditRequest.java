package com.codemind.platform.artifact;

public record ManualArtifactEditRequest(
        String artifactId,
        String artifactType,
        String contentMarkdown,
        String reason
) {
}
