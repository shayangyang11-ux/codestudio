package com.codemind.platform.development;

public record DevelopmentCodebaseSafetyFileResponse(
        String filePath,
        String fileType,
        String action,
        String changeType,
        boolean safeToWrite,
        boolean conflict,
        boolean userModified,
        String previousSha256,
        String currentSha256,
        String proposedSha256,
        String message
) {
}
