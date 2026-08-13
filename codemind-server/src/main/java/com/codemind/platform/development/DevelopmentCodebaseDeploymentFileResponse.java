package com.codemind.platform.development;

public record DevelopmentCodebaseDeploymentFileResponse(
        String filePath,
        String fileType,
        String action,
        boolean exists,
        boolean upToDate,
        boolean writable,
        long bytes,
        String description,
        String message
) {
}
