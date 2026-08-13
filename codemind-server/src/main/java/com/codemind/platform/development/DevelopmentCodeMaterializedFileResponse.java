package com.codemind.platform.development;

public record DevelopmentCodeMaterializedFileResponse(
        String filePath,
        String absolutePath,
        String fileType,
        String action,
        String status,
        String message,
        long bytes
) {
}
