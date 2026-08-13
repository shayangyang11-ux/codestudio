package com.codemind.platform.development;

public record DevelopmentCodebaseNodeFileResponse(
        String nodeId,
        String nodeType,
        String nodeName,
        String relationship,
        String filePath,
        String fileType,
        String description,
        String language,
        boolean exists,
        long bytes
) {
}
