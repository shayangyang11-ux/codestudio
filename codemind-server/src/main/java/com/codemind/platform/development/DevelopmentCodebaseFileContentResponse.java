package com.codemind.platform.development;

import java.time.LocalDateTime;

public record DevelopmentCodebaseFileContentResponse(
        String filePath,
        String absolutePath,
        String language,
        String content,
        long bytes,
        LocalDateTime updatedAt
) {
}
