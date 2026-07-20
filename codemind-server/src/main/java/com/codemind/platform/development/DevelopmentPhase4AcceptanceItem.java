package com.codemind.platform.development;

public record DevelopmentPhase4AcceptanceItem(
        String id,
        String title,
        String status,
        String statusText,
        String description,
        boolean required,
        boolean passed
) {
}
