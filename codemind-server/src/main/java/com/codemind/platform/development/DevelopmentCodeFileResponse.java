package com.codemind.platform.development;

public record DevelopmentCodeFileResponse(
        String filePath,
        String fileType,
        String action,
        String source,
        String description,
        String language,
        String content,
        int priority
) {
}
