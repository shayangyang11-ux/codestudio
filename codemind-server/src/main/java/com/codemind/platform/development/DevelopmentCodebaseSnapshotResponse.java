package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;

public record DevelopmentCodebaseSnapshotResponse(
        String snapshotId,
        String snapshotStatus,
        String reason,
        String snapshotRoot,
        String archivePlatformPath,
        String archiveAbsolutePath,
        int fileCount,
        long totalBytes,
        String checksum,
        LocalDateTime createdAt,
        List<String> warnings,
        List<DevelopmentCodebaseSnapshotFileResponse> files
) {
}
