package com.codemind.platform.development;

public record DevelopmentPlanFileItem(
        String filePath,
        String fileType,
        String action,
        String source,
        String description,
        int priority
) {
}
