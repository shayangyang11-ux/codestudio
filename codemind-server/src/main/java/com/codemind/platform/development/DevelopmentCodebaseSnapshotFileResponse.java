package com.codemind.platform.development;

import java.time.LocalDateTime;

public record DevelopmentCodebaseSnapshotFileResponse(
        String filePath,
        String sha256,
        long bytes,
        LocalDateTime updatedAt
) {
}
