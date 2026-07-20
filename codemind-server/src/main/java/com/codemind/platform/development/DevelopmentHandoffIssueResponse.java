package com.codemind.platform.development;

public record DevelopmentHandoffIssueResponse(
        String id,
        String level,
        String title,
        String description,
        String artifactType
) {
}
