package com.codemind.platform.development;

public record DevelopmentCodebaseFileSaveRequest(
        String targetStack,
        String filePath,
        String content,
        String relationship,
        String intentScope,
        String fileRole,
        String remarks
) {
}
